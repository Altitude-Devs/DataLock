package com.alttd.datalock;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "data-lock",
        name = "DataLock",
        version = BuildConstants.VERSION,
        description = "A proxy plugin that can be utilized to prevent any plugins from editing data that is currently in use elsewhere",
        url = "https://alttd.com",
        authors = {"Teriuihi"}
)
public class DataLock {

    private static DataLock instance;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    @Inject
    public DataLock(ProxyServer proxyServer, Logger proxyLogger, @DataDirectory Path proxyDataDirectory) {
        instance = this;
        server = proxyServer;
        logger = proxyLogger;
        dataDirectory = proxyDataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        reloadConfig();
        server.getEventManager().register(this, EventListener.getInstance());
        new Reload(server);
    }

    public static DataLock getInstance() {
        return instance;
    }

    public static Logger getLogger() {
        return getInstance().logger;
    }

    public static ProxyServer getServer() {
        return getInstance().server;
    }

    public static Path getDataDirectory() {
        return getInstance().dataDirectory;
    }

    public void reloadConfig() {
        Config.init();
        EventListener.reload();
    }
}
