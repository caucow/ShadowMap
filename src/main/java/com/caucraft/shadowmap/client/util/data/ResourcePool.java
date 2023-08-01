package com.caucraft.shadowmap.client.util.data;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ResourcePool<T> {

    private final Supplier<T> supplier;
    private final Consumer<T> onRelease;
    private final AtomicInteger currentCapacity;
    private final int maxCapacity;
    private final LinkedBlockingDeque<T> pool;

    /**
     * @param resourceSupplier the supplier for new instances of the resource.
     * @param onRelease a function to reset instances of the resource released
     * back to the pool.
     * @param initialCapacity initial number of instances of the resource to
     * hold in the pool.
     * @param maxCapacity maximum number of instances of the resource to hold in
     * the pool.
     */
    public ResourcePool(Supplier<T> resourceSupplier, Consumer<T> onRelease, int initialCapacity, int maxCapacity) {
        this.supplier = resourceSupplier;
        this.onRelease = onRelease;
        this.currentCapacity = new AtomicInteger(initialCapacity);
        this.maxCapacity = maxCapacity;
        this.pool = new LinkedBlockingDeque<>();
        for (int i = 0; i < initialCapacity; i++) {
            pool.add(supplier.get());
        }
    }

    private T getNewOrWait() throws InterruptedException {
        int cap = currentCapacity.get();
        T resource = null;
        while (cap < maxCapacity && !currentCapacity.compareAndSet(cap, cap + 1)) {
            cap = currentCapacity.get();
        }
        if (cap < maxCapacity) { // loop terminated from successful CAS
            resource = supplier.get();
        } else { // pool is at max capacity
            resource = pool.takeFirst();
        }
        return resource;
    }

    public T take() throws InterruptedException {
        T resource = pool.pollFirst();
        if (resource == null) {
            resource = getNewOrWait();
        }
        return resource;
    }

    /**
     * Attempts to take enough resources to fill the array, blocking if
     * necessary until enough are available. Care should be taken to make sure
     * the pool has enough resources that all threads using it can call this
     * method, and at least one of them will be able to take enough to complete
     * its task, releasing those resources so other threads cah chain-complete.
     * @param holder array to store resources in.
     *
     * @throws InterruptedException if the thread is interrupted while waiting
     * for a resource to become available. In this case, resources already in
     * the array will be released, the array will be filled with null, and the
     * exception will be thrown.
     */
    public void bulkTake(T[] holder) throws InterruptedException {
        int acquired = 0;
        try {
            for (; acquired < holder.length; acquired++) {
                holder[acquired] = take();
            }
        } catch (InterruptedException ex) {
            for (int i = 0; i < acquired; i++) {
                release(holder[i]);
            }
            throw ex;
        }
    }

    public void release(T resource) {
        if (resource == null) {
            return;
        }
        if (pool.size() == currentCapacity.get()) {
            throw new IllegalStateException("Tried to return resource to pool that is already full");
        }
        onRelease.accept(resource);
        pool.addFirst(resource);
    }

    public void bulkRelease(T[] holder) {
        for (int i = 0; i < holder.length; i++) {
            release(holder[i]);
        }
    }
}
