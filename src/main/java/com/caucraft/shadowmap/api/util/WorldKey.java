package com.caucraft.shadowmap.api.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;

import java.util.Locale;
import java.util.Objects;

public final class WorldKey {
    private final ServerKey serverKey;
    private final String worldName;
    private final String dimensionName;

    public static WorldKey newKey(ServerKey serverKey, String worldName, String dimensionName) {
        return new WorldKey(serverKey, worldName.toLowerCase(Locale.ROOT), dimensionName.toLowerCase(Locale.ROOT));
    }

    private WorldKey(ServerKey serverKey, String worldName, String dimensionName) {
        this.serverKey = serverKey;
        this.worldName = worldName;
        this.dimensionName = dimensionName;
    }

    public NbtCompound toNbt() {
        NbtCompound root = serverKey.toNbt();
        root.put("world", NbtString.of(worldName));
        root.put("dim", NbtString.of(dimensionName));
        return root;
    }

    public static WorldKey fromNbt(NbtCompound root) {
        return new WorldKey(ServerKey.fromNbt(root), root.getString("world"), root.getString("dim"));
    }

    @Override
    public String toString() {
        return serverKey + "-" + worldName + "-" + dimensionName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        WorldKey worldKey = (WorldKey) o;
        return Objects.equals(serverKey, worldKey.serverKey) && Objects.equals(worldName, worldKey.worldName)
                && Objects.equals(dimensionName, worldKey.dimensionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverKey, worldName, dimensionName);
    }

    public ServerKey serverKey() {return serverKey;}

    public String worldName() {return worldName;}

    public String dimensionName() {return dimensionName;}

}
