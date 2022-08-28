package com.alttd.datalock;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.util.Collection;
import java.util.Optional;

public class PlayerListener {

    @Subscribe
    void onPlayerConnect(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        ServerInfo serverInfo = event.getServer().getServerInfo();
        Collection<Player> playersConnected = event.getServer().getPlayersConnected();
        if (playersConnected.isEmpty() || playersConnected.size() == 1 && playersConnected.contains(player))
            EventListener.getInstance().clearServer(serverInfo.hashCode());

        Optional<RegisteredServer> previousServer = event.getPreviousServer();
        if (previousServer.isEmpty())
            return;
        serverInfo = previousServer.get().getServerInfo();
        if (playersConnected.isEmpty() || playersConnected.size() == 1 && playersConnected.contains(player))
            EventListener.getInstance().clearServer(serverInfo.hashCode());
    }

}
