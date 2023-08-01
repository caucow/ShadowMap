package com.caucraft.shadowmap.client.importer;

import com.caucraft.shadowmap.api.util.ServerKey;
import com.caucraft.shadowmap.client.ShadowMap;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class ServerFinder {
    private ServerKey[] serverList;
    private CompletableFuture<ServerKey[]> loadFuture;

    public ServerFinder(boolean async) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (async) {
            this.loadFuture = ShadowMap.getInstance().getMapManager().executeNonLockingIOTask(() -> {
                ServerList clientServerList = new ServerList(client);
                clientServerList.loadFile();
                return parseClientServerList(clientServerList);
            });
        } else {
            ServerList clientServerList = new ServerList(client);
            clientServerList.loadFile();
            this.serverList = parseClientServerList(clientServerList);
            this.loadFuture = CompletableFuture.completedFuture(serverList);
        }
    }

    public ServerKey getBestMatch(String address, int port) {
        address = address.toLowerCase(Locale.ROOT);
        if (serverList == null) {
            serverList = loadFuture.join();
        }
        if (!loadFuture.isDone()) {
            loadFuture.join();
        }
        ServerKey selected = null;
        for (int i = 0; i < serverList.length; i++) {
            ServerKey testKey = serverList[i];
            if (testKey.nameOrIp().equals(address)) {
                if (port == testKey.port()) {
                    return testKey;
                }
                if (port == -1 && (selected == null || testKey.port() == SharedConstants.DEFAULT_PORT)) {
                    selected = testKey;
                }
            }
        }
        return selected;
    }

    private static ServerKey[] parseClientServerList(ServerList serverList) {
        ServerKey[] keyArray = new ServerKey[serverList.size()];
        for (int i = 0; i < serverList.size(); i++) {
            ServerInfo info = serverList.get(i);
            String address = info.address;
            int port = info.address.indexOf(':');
            if (port == -1) {
                port = SharedConstants.DEFAULT_PORT;
            } else {
                String portString = address.substring(port + 1);
                address = address.substring(0, port);
                port = Integer.parseInt(portString);
            }
            keyArray[i] = ServerKey.newKey(ServerKey.ServerType.MULTIPLAYER, address, port);
        }
        return keyArray;
    }
}
