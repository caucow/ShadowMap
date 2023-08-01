package com.caucraft.shadowmap.client.util;

import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.api.util.WorldKey;
import com.caucraft.shadowmap.client.util.sim.Area;
import com.caucraft.shadowmap.client.util.sim.SingleBlockWorld;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.registry.Registry;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.lwjgl.opengl.GL12;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public abstract class MapUtils {

    public static final int DEFAULT_BUFFER_SIZE = 0x10_0000; // 1MB
    public static final int BUFFER_GROW_AMOUNT = 0x10_0000; // 1MB
    public static final int MAX_BUFFER_SIZE = 0xA0_0000; // 10MB

    private static final char ILLEGAL_CHAR_PREFIX;
    private static final BitSet ILLEGAL_CHARS;

    public static final ThreadLocal<SingleBlockWorld> EMPTY_WORLD1 = ThreadLocal.withInitial(SingleBlockWorld::new);
    public static final ThreadLocal<SingleBlockWorld> EMPTY_WORLD2 = ThreadLocal.withInitial(SingleBlockWorld::new);
    public static final ThreadLocal<SingleBlockWorld> EMPTY_WORLD3 = ThreadLocal.withInitial(SingleBlockWorld::new);

    static {
        // ===== DEV NOTE / WARNING =====
        // These are used in filenames saved to disk
        // DO NOT remove characters from these strings, ONLY add to them,
        // otherwise existing map folder structures will not correlate to
        // the servers/worlds that generated them.
        String illegalCharsString = "`~@#$%&*+=[]{}\\|;:'\"<>/?";
        ILLEGAL_CHAR_PREFIX = '^';
        ILLEGAL_CHARS = new BitSet(128);
        ILLEGAL_CHARS.set(ILLEGAL_CHAR_PREFIX);
        for (int i = 0; i <= 0x1F; i++) {
            ILLEGAL_CHARS.set(i);
        }
        for (int i = 0; i < illegalCharsString.length(); i++) {
            ILLEGAL_CHARS.set(illegalCharsString.charAt(i));
        }
    }

    // literally just https://stackoverflow.com/questions/3018313/algorithm-to-convert-rgb-to-hsv-and-hsv-to-rgb-in-range-0-255-for-both
    // because MathHelper.hsvToRgb *does not work* for input hue 360 apparently
    /**
     * @param hue hue value in the range [0, 1]
     * @param sat saturation value in the range [0, 1]
     * @param val value/luminance value in the range [0, 1]
     * @return the converted color in the format 0x__RRGGBB
     */
    public static int hsvToRgb(float hue, float sat, float val) {
        float hh, p, q, t, ff, r, g, b;
        int i;

        hue = MathHelper.clamp(hue, 0.0F, 1.0F);
        sat = MathHelper.clamp(sat, 0.0F, 1.0F);
        val = MathHelper.clamp(val, 0.0F, 1.0F);
        hh = hue;
        if (hh >= 1.0F) {
            hh = 0.0F;
        }
        hh *= 6.0F;
        i = (int) hh;
        ff = hh - i;
        p = val * (1.0F - sat);
        q = val * (1.0F - (sat * ff));
        t = val * (1.0F - (sat * (1.0F - ff)));

        switch (i) {
            case 0:
                r = val;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = val;
                b = p;
                break;
            case 2:
                r = p;
                g = val;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = val;
                break;
            case 4:
                r = t;
                g = p;
                b = val;
                break;
            case 5:
            default:
                r = val;
                g = p;
                b = q;
                break;
        }
        return (int) (r * 255.0F + 0.001F) << 16 | (int) (g * 255F + 0.001F) << 8 | (int) (b * 255F + 0.001F);
    }

    /**
     * @param rgb the rgb color in the format 0x__RRGGBB
     * @return an array of 3 floats in the order [hue, saturation, value], each
     * with values in the range [0, 1].
     */
    public static float[] rgbToHsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255.0F;
        float g = (rgb >> 8 & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float Cmax = Math.max(r, Math.max(g, b));
        float Cmin = Math.min(r, Math.min(g, b));
        float delta = Cmax - Cmin;
        float hue, sat, val;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (Cmax == r) {
            hue = ((g - b) / delta / 6.0F + 1.0F) % 1.0F;
        } else if (Cmax == g) {
            hue = ((b - r) / delta + 2.0F) / 6.0F;
        } else {
            hue = ((r - g) / delta + 4.0F) / 6.0F;
        }
        val = Cmax;
        sat = Cmax == 0.0F ? 0 : delta / Cmax;
        return new float[] {hue, sat, val};
    }

    /**
     * Reads a file completely into a ByteBuffer, using the provided buffer if
     * possible or returning an enlarged buffer if not.
     * @param channel channel to read the file from.
     * @param buffer initial buffer to try to read the file into.
     * @param fileSize size of the file, or a negative number to get its size
     * from the channel.
     * @return the ByteBuffer the file was actually read into.
     * @throws IOException if an I/O error occurs
     */
    public static ByteBuffer readFileToBuffer(FileChannel channel, ByteBuffer buffer, long fileSize) throws IOException {
        if (fileSize < 0) {
            fileSize = channel.size();
        }
        if (fileSize > buffer.remaining()) {
            if (fileSize > Integer.MAX_VALUE) {
                throw new IOException("File size is larger than Integer.MAX_VALUE");
            }
            fileSize += buffer.position();
            try {
                buffer = MapUtils.growBuffer(buffer, (int) fileSize);
            } catch (IllegalArgumentException ex) { throw new IOException(ex); }
        }
        int bytesRead;
        do {
            bytesRead = channel.read(buffer);
        } while (bytesRead > 0);
        return buffer;
    }

    /**
     * Writes a ByteBuffer completely to a file.
     * @param channel the channel to write the buffer to.
     * @param buffer the buffer to write.
     * @throws IOException if an I/O error occurs
     */
    public static void writeFileFromBuffer(FileChannel channel, ByteBuffer buffer) throws IOException {
        channel.position(0);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        channel.truncate(buffer.limit());
    }

    public static void writeCompressedNbt(String rootName, NbtCompound nbt, OutputStream outputStream) throws IOException {
        try (DataOutputStream dataOut = new DataOutputStream(new GZIPOutputStream(outputStream))) {
            dataOut.writeByte(nbt.getType());
            if (nbt.getType() != NbtElement.END_TYPE) {
                dataOut.writeUTF(rootName);
                nbt.write(dataOut);
            }
            dataOut.flush();
        }
    }

    public static NbtCompound readCompressedNbt(InputStream inputStream) throws IOException {
        try (DataInputStream dataIn = new DataInputStream(new GZIPInputStream(inputStream, 65536))) {
            return NbtIo.read(dataIn, NbtTagSizeTracker.EMPTY);
        }
    }

    /**
     * Creates a new ByteBuffer larger than {@code buffer} by
     * up to {@link #BUFFER_GROW_AMOUNT} bytes, as long as it is smaller than
     * {@link #MAX_BUFFER_SIZE}, and copies {@code buffer}'s content from index
     * 0 through {@link ByteBuffer#position()} to it. The returned ByteBuffer
     * will have the same {@link ByteBuffer#position()} as the passed one.
     * @param buffer the buffer to grow
     * @return A larger buffer with the contents of the passed buffer.
     *
     * @throws IllegalArgumentException if the buffer is already at its maximum
     * length.
     */
    public static ByteBuffer growBuffer(ByteBuffer buffer) {
        if (buffer.capacity() >= MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException("Buffer cannot be grown or it will exceed maximum size: " + MAX_BUFFER_SIZE);
        }
        int newSize = buffer.capacity() + BUFFER_GROW_AMOUNT;
        if (newSize > MAX_BUFFER_SIZE) {
            newSize = MAX_BUFFER_SIZE;
        }
        return growBuffer(buffer, newSize);
    }

    /**
     * Creates a new ByteBuffer larger than {@code buffer} by
     * up to {@link #BUFFER_GROW_AMOUNT} bytes, as long as it is smaller than
     * {@link #MAX_BUFFER_SIZE}, and copies {@code buffer}'s content from index
     * 0 through {@link ByteBuffer#position()} to it. The returned ByteBuffer
     * will have the same {@link ByteBuffer#position()} as the passed one.
     * @param buffer the buffer to grow
     * @param minSize the minimum desired size of the new buffer
     * @return A larger buffer with the contents of the passed buffer.
     *
     * @throws IllegalArgumentException if the buffer is already at its maximum
     * length.
     */
    public static ByteBuffer growBuffer(ByteBuffer buffer, int minSize) {
        if (minSize <= buffer.capacity()) {
            return buffer.limit(buffer.capacity());
        }
        if (minSize > MAX_BUFFER_SIZE) {
            throw new IllegalArgumentException("Buffer cannot be grown to " + minSize + " bytes or it will exceed maximum size: " + MAX_BUFFER_SIZE);
        }
        int newSize = Math.min(MAX_BUFFER_SIZE, Math.max(minSize, buffer.capacity() + BUFFER_GROW_AMOUNT));
        ByteBuffer newBuffer = ByteBuffer.allocate(newSize);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    /**
     * Reads a variable-length int from the input stream
     * @param in the input stream
     * @return the next varint from the stream
     * @throws IOException if the stream throws an exception
     * https://wiki.vg/VarInt_And_VarLong
     */
    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        int currentByte;

        while (true) {
            currentByte = in.read();
            if (currentByte == -1) {
                throw new IOException("Reached end of stream while reading VarInt");
            }
            value |= (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) break;

            position += 7;

            if (position >= 32) {
                throw new IOException("VarInt is too big");
            }
        }

        return value;
    }

    /**
     * Writes a variable-length int to the output stream
     * @param out the output stream
     * @param value the int to write
     * @throws IOException if the stream throws an exception
     * https://wiki.vg/VarInt_And_VarLong
     */
    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                out.write(value);
                return;
            }

            out.write((value & SEGMENT_BITS) | CONTINUE_BIT);

            // Note: >>> means that the sign bit is shifted with the rest of the number rather than being left alone
            value >>>= 7;
        }
    }

    public static BlockState blockStateFromString(Registry<Block> blockRegistry, String stateString) {
        int propsStart = stateString.indexOf('[');
        int propsEnd = stateString.lastIndexOf(']');
        String blockString;
        String propsString;
        if (propsStart != -1 && propsEnd == stateString.length() - 1) {
            blockString = stateString.substring(0, propsStart);
            propsString = stateString.substring(propsStart + 1, propsEnd);
        } else {
            blockString = stateString;
            propsString = null;
        }
        Block block = blockRegistry.get(new Identifier(blockString));
        if (block == null) {
            return Blocks.AIR.getDefaultState();
        }
        BlockState state = block.getDefaultState();
        if (propsString != null) {
            StateManager<Block, BlockState> stateManager = block.getStateManager();
            for (String propEntry : propsString.split(",")) {
                int eqIndex = propEntry.indexOf('=');
                String propName = propEntry.substring(0, eqIndex);
                String propValue = propEntry.substring(eqIndex + 1);
                state = setBlockStateProperty(stateManager, state, propName, propValue);
            }
        }
        return state;
    }

    public static BlockState blockStateFromNbt(Registry<Block> blockRegistry, NbtCompound stateNbt) {
        if (!stateNbt.contains("Name", NbtElement.STRING_TYPE)) {
            throw new IllegalArgumentException("NbtCompound does not have a Name element");
        }
        Block block = blockRegistry.get(new Identifier(stateNbt.getString("Name")));
        if (block == null) {
            return Blocks.AIR.getDefaultState();
        }
        BlockState state = block.getDefaultState();
        if (!stateNbt.contains("Properties", NbtElement.COMPOUND_TYPE)) {
            return state;
        }
        NbtCompound propsNbt = stateNbt.getCompound("Properties");
        StateManager<Block, BlockState> stateManager = block.getStateManager();
        for (String propKey : propsNbt.getKeys()) {
            state = setBlockStateProperty(stateManager, state, propKey, propsNbt.getString(propKey));
        }
        return state;
    }

    public static String blockToString(BlockState state) {
        StringBuilder sb = new StringBuilder();
        blockToString(sb, state);
        return sb.toString();
    }

    public static void blockToString(StringBuilder sb, BlockState state) {
        sb.append(state.getRegistryEntry().getKey().get().getValue());
    }

    public static String blockStateToString(BlockState state) {
        StringBuilder sb = new StringBuilder();
        blockStateToString(sb, state);
        return sb.toString();
    }

    public static void blockStateToString(StringBuilder sb, BlockState state) {
        sb.append(state.getRegistryEntry().getKey().get().getValue());
        ImmutableMap<Property<?>, Comparable<?>> propertyMap = state.getEntries();
        if (!propertyMap.isEmpty()) {
            sb.append('[');
            for (Map.Entry<Property<?>, Comparable<?>> entry : propertyMap.entrySet()) {
                sb.append(entry.getKey().getName());
                sb.append('=');
                sb.append(((Property) entry.getKey()).name(entry.getValue()));
                sb.append(',');
            }
            sb.setCharAt(sb.length() - 1, ']');
        }
    }

    public static BlockState setBlockStateProperty(StateManager<Block, BlockState> stateManager,
            BlockState state, String propKey, String propValue) {
        Property stateProperty = stateManager.getProperty(propKey);
        Optional<Comparable> realValue =
                stateProperty.parse(propValue);
        if (realValue.isPresent()) {
            try {
                state = state.withIfExists(stateProperty, realValue.get());
            } catch (IllegalArgumentException ignored) {}
        }
        return state;
    }

    /**
     * @param mapsDirectory the root directory map data will be stored in
     * @param worldKey the key for the expected world
     * @return the directory for the given server, in the format
     * {@code mapDir/serverId/worldId}
     */
    public static File getServerDirectory(File mapsDirectory, WorldKey worldKey) {
        String subdirectory = getServerId(worldKey);
        return new File(mapsDirectory, subdirectory);
    }

    /**
     * @param mapsDirectory the root directory map data will be stored in
     * @param worldKey the key for the expected world
     * @return the directory for the given world, in the format
     * {@code mapDir/serverId/worldId}
     */
    public static File getWorldDirectory(File mapsDirectory, WorldKey worldKey) {
        String subdirectory = getServerId(worldKey) + '/' + getWorldId(worldKey);
        return new File(mapsDirectory, subdirectory);
    }

    private static String getServerId(WorldKey worldKey) {
        ServerKey serverKey = worldKey.serverKey();
        String serverId = switch (serverKey.type()) {
            case SINGLEPLAYER -> serverKey.nameOrIp()+ "-SP";
            case LAN -> serverKey.nameOrIp() + "-LAN";
            case REALMS -> serverKey.nameOrIp() + "-REALM";
            case MULTIPLAYER -> serverKey.nameOrIp() + '-' + serverKey.port() + "-MP";
        };
        return escapeIllegalFilenameChars(serverId);
    }

    private static String getWorldId(WorldKey worldKey) {
        String worldId = worldKey.worldName() + '-' + worldKey.dimensionName();
        return escapeIllegalFilenameChars(worldId);
    }

    /**
     * Escapes common Windows/Mac/Linux illegal characters present in
     * server/world/dimension names so maps can be safely saved to disk. Illegal
     * characters are replaced with {@link #ILLEGAL_CHAR_PREFIX} followed by the
     * 2-digit hex value of that character. Characters with values >0x7F are not
     * replaced.
     * @param filename the filename to escape, if necessary
     * @return a legal file name, or the original string if no illegal
     * characters were present.
     */
    public static String escapeIllegalFilenameChars(String filename) {
        boolean legal = true;
        for (int i = 0; i < filename.length(); i++) {
            if (ILLEGAL_CHARS.get(filename.charAt(i))) {
                legal = false;
                break;
            }
        }

        if (legal) {
            return filename.toLowerCase();
        }

        StringBuilder sb = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char nextChar = filename.charAt(i);
            if (ILLEGAL_CHARS.get(nextChar)) {
                sb.append(ILLEGAL_CHAR_PREFIX);
                if (nextChar < 0x10) {
                    sb.append('0');
                    sb.append(Integer.toString(nextChar, 16));
                } else {
                    sb.append(Integer.toString(nextChar, 16));
                }
            } else {
                sb.append(nextChar);
            }
        }

        return sb.toString();
    }

    private static String unescapeIllegalFilenameChars(String filename) {
        boolean legal = true;
        for (int i = 0; i < filename.length(); i++) {
            if (filename.charAt(i) == '^') {
                legal = false;
            }
        }

        if (legal) {
            return filename;
        }

        StringBuilder sb = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char nextChar = filename.charAt(i);
            if (nextChar != '^') {
                sb.append(nextChar);
            } else if (i == filename.length() - 1 || i == filename.length() - 2 && filename.charAt(i + 1) != ILLEGAL_CHAR_PREFIX) {
                throw new IllegalArgumentException("String ends with escape prefix '" + ILLEGAL_CHAR_PREFIX + "': " + filename);
            } else if (filename.charAt(i + 1) == ILLEGAL_CHAR_PREFIX) {
                // Allows for the possibility of a unique sequence that can
                // appear in the filename but can't be generated by the
                // escape method.
                sb.append('^');
                i++;
            } else {
                sb.append((char) Integer.parseInt(filename.substring(i + 1, i + 3), 16));
                i += 2;
            }
        }
        return sb.toString();
    }

    /**
     * Tries to determine if the block is opaque for mapping purposes. If the
     * block is transparent, it will be rendered on a different layer, producing
     * a more realistic looking map.
     * @param state the state to test for transparency.
     * @return true if the block is considered opaque to the map, false
     * otherwise.
     */
    public static void updateOpacity(BlockState state) {
        MapBlockStateMutable mapState = (MapBlockStateMutable) state;
        SingleBlockWorld fakeWorld = EMPTY_WORLD1.get();
        fakeWorld.setBlock(state);
        VoxelShape shape = state.getOutlineShape(fakeWorld, BlockPos.ORIGIN);
        boolean fullCube = Block.isShapeFullCube(shape);

        // According to Block.shouldDrawSide... it's actually that simple for
        // most transparent blocks.
        if (!state.isOpaque()) {
            if (!fullCube && !shape.isEmpty()) {
                Box aabb = shape.getBoundingBox();
                mapState.shadowMap$setOpacity(false, (int) ((aabb.maxX - aabb.minX) * (aabb.maxZ - aabb.minZ) * 255));
            } else {
                mapState.shadowMap$setOpacity(false, 255);
            }
            return;
        }

        // Also according to Block.shouldDrawSide... "opaque" blocks need some
        // additional testing.

        // Subtract area covered by block bounding boxes, check if any remains.
        List<Box> bbList = shape.getBoundingBoxes();
        Area uncoveredArea = new Area();
        for (Box subBounds : bbList) {
            uncoveredArea.subtract(subBounds.minX, subBounds.minZ, subBounds.maxX, subBounds.maxZ);
        }
        if (!uncoveredArea.isEmpty()) {
            double area = 0;
            for (Area.Rectangle rectangle : uncoveredArea.getRemaining()) {
                area += (rectangle.x2() - rectangle.x1()) * (rectangle.y2() - rectangle.y1());
            }
            mapState.shadowMap$setOpacity(false, (int) (255 * area));
            return;
        }

        // And last but not least, one additional test that breaks some
        // visibly-opaque-but-functionally-apparently-not blocks.
        // Among these, the target was big_dripleaf, which was classified as
        // opaque by the bounding box check (it's visibly transparent).
        // Also among these, repeaters, comparators, composters, and wool/moss
        // carpets will also inadvertently be made transparent.
        if (!Block.isShapeFullCube(shape) && !state.hasSidedTransparency()) {
            mapState.shadowMap$setOpacity(false, 255);
            return;
        }

        mapState.shadowMap$setOpacity(true, 255);
    }

    /**
     * Uses singular value decomposition (I think?) to get a best-fit plane for
     * a set of points. This was brought in from a private 3+ year old project,
     * whether it was sourced from somewhere or adapted from wikipedia was not
     * documented.
     * @param pts an array of vector points approximating a plane surface.
     * @param destPlane a 4d vector to store the plane's normals and scale in.
     * @return the {@code destPlane}
     */
    public static Vector4d svdPlaneFromPoints(Vector3d[] pts, Vector4d destPlane) {
        if (pts.length < 3) {
            return null;
        }

        double cx = 0, cy = 0, cz = 0;

        for (int i = pts.length - 1; i >= 0; i--) {
            cx += pts[i].x;
            cy += pts[i].y;
            cz += pts[i].z;
        }

        double factor = 1.0 / (double)pts.length;
        cx *= factor;
        cy *= factor;
        cz *= factor;

        double bx, by, bz, xx = 0.0, xy = 0.0, xz = 0.0, yy = 0.0, yz = 0.0, zz = 0.0;

        for (int i = pts.length - 1; i >= 0; i--) {
            bx = pts[i].x - cx;
            by = pts[i].y - cy;
            bz = pts[i].z - cz;
            xx += bx * bx;
            xy += bx * by;
            xz += bx * bz;
            yy += by * by;
            yz += by * bz;
            zz += bz * bz;
        }

        double det_x = yy * zz - yz * yz,
                det_y = xx * zz - xz * xz,
                det_z = xx * yy - xy * xy,
                det_max = Math.max(det_x, Math.max(det_y, det_z));

        if (det_max <= 0.0) {
            return null;
        }

        if (det_max == det_x) {
            bx = det_x;
            by = xz * yz - xy * zz;
            bz = xy * yz - xz * yy;
        } else if (det_max == det_y) {
            bx = xz * yz - xy * zz;
            by = det_y;
            bz = xy * xz - yz * xx;
        } else {
            bx = xy * yz - xz * yy;
            by = xy * xz - yz * xx;
            bz = det_z;
        }

        return destPlane.set(bx, by, bz, -bx * cx - by * cy - bz * cz);
    }

    public static void uploadTexture(int textureId, IntBuffer imageData, int width, int height) {
        RenderSystem.assertOnRenderThread();
        RenderSystem.activeTexture(GlConst.GL_TEXTURE0);
        RenderSystem.bindTextureForSetup(textureId);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SWAP_BYTES, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_LSB_FIRST, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texImage2D(GlConst.GL_TEXTURE_2D, 0, GlConst.GL_RGBA, width, height, 0, GL12.GL_BGRA, 33639, imageData);
        GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MIN_FILTER, GlConst.GL_NEAREST);
        GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MAG_FILTER, GlConst.GL_NEAREST);
    }

    private MapUtils() {}

}
