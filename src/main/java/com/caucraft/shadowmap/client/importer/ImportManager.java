package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.ShadowMap;
import com.caucraft.shadowmap.client.map.MapManagerImpl;
import com.caucraft.shadowmap.client.map.MapWorldImpl;
import com.caucraft.shadowmap.client.util.MapUtils;
import com.caucraft.shadowmap.client.util.data.DeletableLiveDataMap;
import com.caucraft.shadowmap.client.util.io.ByteBufferInputStream;
import com.caucraft.shadowmap.client.util.io.ByteBufferOutputStream;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.crash.CrashException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public class ImportManager implements Closeable {

    private transient final MapManagerImpl mapManager;
    private transient final Semaphore activeImportLimiter;
    private DeletableLiveDataMap<ImportTask<?>> importTasks;
    private transient final Phaser shutdownPhaser;
    private transient final Thread importManagerThread;
    private transient final AtomicReference<ImportTask<?>> currentTask;
    private transient volatile boolean shutdown;
    private transient long lastSaved;
    private transient boolean dirty;

    public ImportManager(MapManagerImpl mapManager) {
        this.mapManager = mapManager;
        this.activeImportLimiter = new Semaphore(4);
        this.importTasks = new DeletableLiveDataMap<ImportTask<?>>(ImportType::fromNbt).setMergeFunction((oldVal, newVal) -> {
            if (oldVal.isSameTask(newVal)) {
                oldVal.mergeProgress(newVal);
            }
            return oldVal;
        });
        this.shutdownPhaser = new Phaser();
        this.importManagerThread = new Thread(this::run, "SM-MapImportManager");
        this.currentTask = new AtomicReference<>();
        this.importManagerThread.start();
        this.scheduleLoad();
    }

    private void run() {
        shutdownPhaser.register();
        while (!shutdown) {
            try {
                ImportTask<?> nextTask = this.currentTask.get();
                if (nextTask == null) {
                    List<ImportTask<?>> taskList = getTasks();
                    MapWorldImpl currentWorld = mapManager.getCurrentWorld();
                    ImportTask<?> selected = null;
                    for (ImportTask<?> task : taskList) {
                        if (task.isDone() || task.isPaused()) {
                            continue;
                        }
                        if (currentWorld != null && task.getWorldKey().equals(currentWorld.getWorldKey())) {
                            selected = task;
                            break;
                        }
                        MapWorldImpl taskWorld = mapManager.getWorld(task.getWorldKey());
                        if (selected == null && (taskWorld != null || task.isUsingDefaultDatapacks())) {
                            selected = task;
                        }
                    }
                    if (selected != null) {
                        this.currentTask.compareAndSet(nextTask, selected);
                        continue;
                    }
                    LockSupport.park();
                } else if (nextTask.isPaused()) {
                    this.currentTask.compareAndSet(nextTask, null);
                } else {
                    importNextRegion(nextTask);
                }
            } catch (Exception ex) {
                ShadowMap.getLogger().error("Exception in import manager thread", ex);
            }
        }
        shutdownPhaser.arriveAndDeregister();
    }

    @Override
    public void close() {
        ShadowMap.getLogger().info("Importer shutting down");
        shutdownPhaser.register();
        shutdown = true;
        importManagerThread.interrupt();
        try {
            shutdownPhaser.awaitAdvanceInterruptibly(shutdownPhaser.arriveAndDeregister(), 5, TimeUnit.SECONDS);
            ShadowMap.getLogger().info("Importer shut down");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException ex) {
            ShadowMap.getLogger().info("Importer timed out while waiting for shut down");
        }
        scheduleSave();
    }

    private <T extends ImportTask.ImportOp> void importNextRegion(ImportTask<T> task) {
        try {
            activeImportLimiter.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return;
        }
        // TODO clean this up, it's too messy
        if (!task.isReady()) {
            MapWorldImpl world = mapManager.getWorld(task.getWorldKey());
            Object forceLoader = null;
            if (world == null && !task.isInitializing()) {
                if (!task.isUsingDefaultDatapacks()) {
                    currentTask.compareAndSet(task, null);
                    LockSupport.unpark(importManagerThread);
                    activeImportLimiter.release();
                    return;
                }
                world = mapManager.loadWorld(task.getWorldKey());
                forceLoader = world.getForceLoader();
                try {
                    world.waitForLoad();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    activeImportLimiter.release();
                    return;
                }
            }
            if (world != null && forceLoader == null) {
                forceLoader = world.getForceLoader();
            }
            try {
                shutdownPhaser.register();
                final MapWorldImpl finalWorld = world;
                final Object finalForceLoader = forceLoader;
                CompletableFuture<Void> initFuture = mapManager.executeNonLockingIOTask(() -> {
                    ShadowMap.getLogger().info("Initializing import task: " + task);
                    task.init(finalWorld, finalForceLoader);
                    return null;
                });
                initFuture.whenComplete((val, ex) -> {
                    LockSupport.unpark(importManagerThread);
                    shutdownPhaser.arriveAndDeregister();
                });
                initFuture.get();
            } catch (ExecutionException ex) {
                ShadowMap.getLogger().warn("Could not initialize import task", ex);
                currentTask.compareAndSet(task, null);
                LockSupport.unpark(importManagerThread);
                activeImportLimiter.release();
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                activeImportLimiter.release();
                return;
            }
        }
        T nextOp = task.nextImportOp();
        if (nextOp == null) {
            if (task.isDone()) {
                currentTask.compareAndSet(task, null);
                LockSupport.unpark(importManagerThread);
            }
            activeImportLimiter.release();
            return;
        }
        CompletableFuture<Void> ioFuture = mapManager.executeNonLockingIOTask(() -> {
            nextOp.loadFile(mapManager);
            return null;
        });
        CompletableFuture<?> mergeFuture = ioFuture.thenCompose((v) -> nextOp.scheduleMerge(mapManager));
        mergeFuture.whenComplete((val, ex) -> {
            if (ex != null) {
                ShadowMap.getLogger().info("Import task failed for file: " + nextOp.getImportFile(), ex);
                nextOp.completeExceptionally(ex);
                task.addError();
            }
            dirty = true;
            activeImportLimiter.release();
            nextOp.complete(null);
        });
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setCurrentTask(UUID taskId) {
        if (taskId == null) {
            this.currentTask.set(null);
        }
        ImportTask<?> task = importTasks.get(taskId);
        if (task != null) {
            task.setPaused(false);
            this.currentTask.set(task);
            LockSupport.unpark(importManagerThread);
        }
    }

    public ImportTask<?> getCurrentTask() {
        return currentTask.get();
    }

    public void onWorldChanged(WorldKey newWorld) {
        if (newWorld == null) {
            return;
        }
        for (ImportTask<?> task : getTasks()) {
            if (task.getWorldKey().equals(newWorld)) {
                currentTask.set(task);
                LockSupport.unpark(importManagerThread);
                return;
            }
        }
    }

    public synchronized List<ImportTask<?>> getTasks() {
        return new ArrayList<>(importTasks.values());
    }

    public synchronized void addTask(ImportTask<?> task) {
        importTasks.put(task.getId(), task);
        MapWorldImpl world = mapManager.getCurrentWorld();
        ImportTask<?> oldCurrent;
        while ((oldCurrent = currentTask.get()) == null || (world != null && world.getWorldKey().equals(task.getWorldKey()))) {
            if (!currentTask.compareAndSet(oldCurrent, task)) {
                continue;
            }
            LockSupport.unpark(importManagerThread);
            break;
        }
        dirty = true;
    }

    public synchronized void removeTask(UUID taskId) {
        ImportTask<?> task = importTasks.remove(taskId);
        if (task == currentTask.get()) {
            currentTask.compareAndSet(task, null);
            LockSupport.unpark(importManagerThread);
        }
        dirty = true;
    }

    public CompletableFuture<?> scheduleSave() {
        return mapManager.executeGlobalIOTask(new SaveTask());
    }

    public CompletableFuture<?> scheduleLoad() {
        return mapManager.executeGlobalIOTask(new LoadTask());
    }

    private NbtCompound toNbt() {
        long time = ShadowMap.getLastTickTimeS();
        for (ImportTask<?> task : getTasks()) {
            if (task.isDone() && time > task.getFinishTime() + 86_400_000) {
                importTasks.remove(task.getId());
            }
        }

        NbtCompound root = importTasks.toNbt();
        return root;
    }

    private void loadNbt(NbtCompound root) throws IOException {
        importTasks.loadNbt(root);
    }

    private class SaveTask implements Callable<Void> {
        @Override
        public Void call() {
            ByteBuffer buffer = null;
            FileChannel importsChannel = null;

            try {
                ShadowMap.getLogger().info("Saving imports");
                buffer = mapManager.getIOBufferPool().take();
                Path importsPath = mapManager.getMapsDirectory().toPath().resolve("imports/imports.dat");
                Files.createDirectories(importsPath.getParent());
                long importsModified = 0;
                long importsSize = 0;

                importsChannel = FileChannel.open(importsPath, StandardOpenOption.CREATE, StandardOpenOption.READ,
                        StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                importsChannel.lock(0, Long.MAX_VALUE, false);
                importsModified = Files.getLastModifiedTime(importsPath).toMillis();
                importsSize = importsChannel.size();

                if (importsModified > lastSaved && importsSize != 0) {
                    buffer = MapUtils.readFileToBuffer(importsChannel, buffer, importsSize);
                    buffer.flip();
                    NbtCompound importsNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffer));
                    loadNbt(importsNbt);
                    buffer.clear();
                }

                dirty = false;
                NbtCompound importsNbt = toNbt();
                ByteBufferOutputStream bufferOutput = new ByteBufferOutputStream(buffer);
                MapUtils.writeCompressedNbt("imports", importsNbt, bufferOutput);
                buffer = bufferOutput.getBuffer();
                buffer.flip();

                MapUtils.writeFileFromBuffer(importsChannel, buffer);
                lastSaved = Files.getLastModifiedTime(importsPath).toMillis();
                ShadowMap.getLogger().info("Imports saved");
            } catch (IOException | CrashException ex) {
                ShadowMap.getLogger().error("Couldn't save imports", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    if (importsChannel != null) {
                        importsChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't save imports", ex);
                }

                if (buffer != null) {
                    mapManager.getIOBufferPool().release(buffer);
                }
            }
            return null;
        }
    }

    private class LoadTask implements Callable<Void> {
        @Override
        public Void call() {
            if (mapManager.isShuttingDown()) {
                return null;
            }
            ByteBuffer buffer = null;
            FileChannel importsChannel = null;

            try {
                buffer = mapManager.getIOBufferPool().take();
                Path importsPath = mapManager.getMapsDirectory().toPath().resolve("imports/imports.dat");
                boolean importsExists = Files.exists(importsPath);
                long importsModified = 0;
                long importsSize = 0;

                // Check files, get channels, acquire locks, etc.
                if (importsExists) {
                    importsChannel = FileChannel.open(importsPath, StandardOpenOption.READ);
                    importsChannel.lock(0, Long.MAX_VALUE, true);
                    importsModified = Files.getLastModifiedTime(importsPath).toMillis();
                    importsSize = importsChannel.size();
                }

                // Once locks are acquired, read files to buffer and close
                // channel to minimize read and lock times.
                if (importsExists) {
                    buffer = MapUtils.readFileToBuffer(importsChannel, buffer, importsSize);
                    buffer.flip();
                    try {
                        importsChannel.close();
                    } catch (IOException ex) {
                        ShadowMap.getLogger().error("Couldn't load imports", ex);
                    }
                }

                // Decompress and parse buffer contents, merge into loaded.
                if (importsExists && buffer.hasRemaining()) {
                    NbtCompound importsNbt = MapUtils.readCompressedNbt(new ByteBufferInputStream(buffer));
                    loadNbt(importsNbt);
                    lastSaved = importsModified;
                }
            } catch (IOException | CrashException ex) {
                ShadowMap.getLogger().error("Couldn't load imports", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                try {
                    if (importsChannel != null) {
                        importsChannel.close();
                    }
                } catch (IOException ex) {
                    ShadowMap.getLogger().error("Couldn't load imports", ex);
                }

                mapManager.getIOBufferPool().release(buffer);
            }
            return null;
        }
    }
}
