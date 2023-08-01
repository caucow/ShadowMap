package com.caucraft.shadowmap.api.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;

import java.util.Locale;
import java.util.Objects;

public final class ServerKey {
    private final ServerType type;
    private final String nameOrIp;
    private final int port;

    public static ServerKey newKey(ServerType type, String nameOrIp, int port) {
        if (type != ServerType.MULTIPLAYER) {
            port = -1;
        }
        return new ServerKey(type, nameOrIp.toLowerCase(Locale.ROOT), port);
    }

    private ServerKey(ServerType type, String nameOrIp, int port) {
        this.type = type;
        this.nameOrIp = nameOrIp;
        this.port = port;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        root.put("type", NbtString.of(type.name()));
        root.put("name", NbtString.of(nameOrIp));
        root.put("port", NbtInt.of(port));
        return root;
    }

    public static ServerKey fromNbt(NbtCompound root) {
        return new ServerKey(ServerType.valueOf(root.getString("type")), root.getString("name"), root.getInt("port"));
    }

    @Override
    public String toString() {
        if (type == ServerType.MULTIPLAYER) {
            return type.name() + '-' + nameOrIp + ':' + port;
        }
        return type.name() + '-' + nameOrIp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        ServerKey serverKey = (ServerKey) o;
        return type == serverKey.type && (type != ServerType.MULTIPLAYER || port == serverKey.port) && Objects.equals(
                nameOrIp, serverKey.nameOrIp);
    }

    @Override
    public int hashCode() {
        if (type != ServerType.MULTIPLAYER) {
            return Objects.hash(type, nameOrIp, port);
        } else {
            return Objects.hash(type, nameOrIp);
        }
    }

    public ServerType type() {return type;}

    public String nameOrIp() {return nameOrIp;}

    public int port() {return port;}


    public enum ServerType {
        SINGLEPLAYER,
        LAN,
        REALMS,
        MULTIPLAYER;
    }
}
