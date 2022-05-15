package com.alttd.datalock;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.util.Optional;

public class PlayerListener {

    @Subscribe
    void onPlayerConnect(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        ServerInfo serverInfo = event.getServer().getServerInfo();
        if (event.getServer().getPlayersConnected().stream().filter(p -> p.equals(player)).findAny().isEmpty())
            EventListener.getInstance().clearServer(serverInfo.hashCode());

        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        if (previousServer.isEmpty())
            return;
        serverInfo = previousServer.get().getServerInfo();
        if (event.getServer().getPlayersConnected().stream().filter(p -> p.equals(player)).findAny().isEmpty())
            EventListener.getInstance().clearServer(serverInfo.hashCode());
    }

}
