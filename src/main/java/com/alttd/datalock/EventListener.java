package com.alttd.datalock;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.*;
import java.util.stream.Collectors;

public class EventListener {

    private final HashMap<ChannelIdentifier, HashSet<Lock>> queuedLocks = new HashMap<>();
    private final HashMap<ChannelIdentifier, HashSet<Lock>> channelLockMap = new HashMap<>();
    private final List<ChannelIdentifier> channelIdentifierList = new ArrayList<>();
    private static EventListener instance = null;

    public static EventListener getInstance() {
        if (instance == null)
            return new EventListener();
        return instance;
    }

    public static void reload()
    {
        instance = getInstance();
        instance.channelIdentifierList.clear();
        ChannelRegistrar channelRegistrar = DataLock.getServer().getChannelRegistrar();
        for (String s : Config.PLUGIN_MESSAGE_CHANNELS) {
            String[] split = s.split(":");
            if (split.length != 2) {
                Logger.warn("Invalid message channel [%] in config.", s);
                continue;
            }
            MinecraftChannelIdentifier minecraftChannelIdentifier = MinecraftChannelIdentifier.create(split[0], split[1]);
            if (instance.channelIdentifierList.contains(minecraftChannelIdentifier)) {
                Logger.warn("Duplicate message channel [%] in config.", s);
                continue;
            }
            if (Config.DEBUG)
                Logger.info("Loaded entry [%] as [%].", s, minecraftChannelIdentifier.asKey().asString());
            instance.channelIdentifierList.add(minecraftChannelIdentifier);
            channelRegistrar.register(minecraftChannelIdentifier);
        }
    }

    public void clearServer(int hashCode) {
        channelLockMap.forEach((identifier, value) -> {
            HashSet<Lock> temp = new HashSet<>();
            for (Lock lock : value) {
                if (lock.getServerHash() == hashCode)
                    temp.add(lock);
            }
            for (Lock lock : temp) {
                value.remove(lock);
                queueNextLock(value, lock, identifier);
                if (Config.DEBUG)
                    Logger.info("Clearing % from % due to clear server being called for the server that lock is on", lock.getData(), identifier.getId());
            }
        });
    }

    private String formatLockMap(HashMap<ChannelIdentifier, HashSet<Lock>> map) {
        StringBuilder stringBuilder = new StringBuilder();
        for (ChannelIdentifier plugin : map.keySet()) {
            stringBuilder
                    .append(plugin)
                    .append("\n")
                    .append(
                            map.get(plugin)
                                    .stream()
                                    .map(lock -> lock.getData() + " : " + lock.getServerHash())
                                    .collect(Collectors.joining(", ")))
                    .append("\n---\n");
        }
        return stringBuilder.toString();
    }

    @Subscribe
    public void onPluginMessageEvent(PluginMessageEvent event) {
        ChannelIdentifier identifier = event.getIdentifier();
        if (Config.DEBUG)
            Logger.info("Received message on [%].", identifier.getId());
        if (!channelIdentifierList.contains(identifier))
            return;

        if (Config.DEBUG)
            Logger.info("\nCurrent locks:\n%\nQueued locks:\n%", formatLockMap(channelLockMap), formatLockMap(queuedLocks));

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if(event.getSource() instanceof Player) {
            Logger.warn("Received plugin message from a player");
            return;
        }

        if (!(event.getSource() instanceof ServerConnection serverConnection)) {
            Logger.warn("Received plugin message from something other than a server.");
            return;
        }

        HashSet<Lock> hashLock = channelLockMap.getOrDefault(identifier, new HashSet<>());
        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String channel;
        try {
            channel = in.readUTF();
        } catch (IllegalStateException e) {
            Logger.error("Input stream did not contain enough data, please contact %'s developer.",
                    identifier.getId());
            return;
        }
        String data;
        try {
            data = in.readUTF();
        } catch (IllegalStateException e) {
            Logger.error("Input stream did not contain enough data, please contact %'s developer.",
                    identifier.getId());
            return;
        }

        if (Config.DEBUG)
            Logger.info("Plugin message channel: [%]", channel.toLowerCase());

        switch (channel.toLowerCase()) {
            case "try-lock" -> tryLock(identifier, hashLock, data, serverConnection);
            case "check-lock" -> checkLock(identifier, hashLock, data, serverConnection);
            case "try-unlock" -> tryUnlock(identifier, hashLock, data, serverConnection);
        }
    }

    private void tryLock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, ServerConnection serverConnection) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("try-lock-result");

        Lock lock = new Lock(serverConnection.getServerInfo().hashCode(), data);
        if (lockSet.contains(lock)) {
            //An entry from this server already exists, so we can say that it's locked
            out.writeBoolean(true);
            out.writeUTF(lock.getData());
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            return;
        }

        Optional<Lock> optionalActiveLock = lockSet.stream().filter(a -> a.compareTo(lock) == 0).findAny();
        if (optionalActiveLock.isPresent()) {
            Lock activeLock = optionalActiveLock.get();
            if (DataLock.getServer().getAllServers().stream()
                    .filter(sc -> !sc.getPlayersConnected().isEmpty())
                    .noneMatch(sc -> activeLock.getServerHash() == sc.getServerInfo().hashCode())) {
                //The server the active lock belongs to is no longer present, we can remove it and apply the new lock
                Logger.warn("Removing lock [%] due to being unable to find a server where that lock was active", activeLock.getData());
                lockSet.remove(activeLock);
            }
            else {
                //An entry from another server exists, so we can't lock it
                out.writeBoolean(false);
                out.writeUTF(lock.getData());
                serverConnection.sendPluginMessage(identifier, out.toByteArray());
                queueLock(queuedLocks.getOrDefault(identifier, new HashSet<>()), identifier, lock, serverConnection);
                return;
            }
        }

        //Lock the data
        lockSet.add(lock);
        channelLockMap.put(identifier, lockSet);

        out.writeBoolean(true);
        out.writeUTF(lock.getData());
        serverConnection.sendPluginMessage(identifier, out.toByteArray());
    }

    private void checkLock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, ServerConnection serverConnection) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        Lock lock = new Lock(serverConnection.hashCode(), data);

        out.writeUTF("check-lock-result");
        if (lockSet.contains(lock)) //We locked this, but we still return true since it's locked
            out.writeBoolean(true);
        else if (lockSet.stream().anyMatch(a -> a.compareTo(lock) == 0))
            out.writeBoolean(true); //There is a lock (not ours, but it's still locked)
        else
            out.writeBoolean(false); //The data is not locked

        out.writeUTF(lock.getData());
        serverConnection.sendPluginMessage(identifier, out.toByteArray());
    }

    private void tryUnlock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, ServerConnection serverConnection) {
        int hash = serverConnection.getServerInfo().hashCode();
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("try-unlock-result");

        Lock lock = new Lock(hash, data);
        if (lockSet.contains(lock)) //Lock is in the list, but it's made by this server, so we can unlock it
        {
            out.writeBoolean(true);
            out.writeUTF(lock.getData());
            lockSet.remove(lock);
            channelLockMap.put(identifier, lockSet);
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            queueNextLock(lockSet, lock, identifier);
            return;
        }

        Optional<Lock> first = lockSet.stream().filter(a -> a.compareTo(lock) == 0).findFirst();
        if (first.isEmpty()) //There is no entry with this data, so we can say it's unlocked
        {
            removeQueuedLock(queuedLocks.get(identifier), lock, hash);
            out.writeBoolean(true);
            out.writeUTF(lock.getData());
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            queueNextLock(lockSet, lock, identifier);
            return;
        }

        //There is an entry with this data, but it's not owned by this server, so we can't unlock it
        out.writeBoolean(false);
        out.writeUTF(lock.getData());
        serverConnection.sendPluginMessage(identifier, out.toByteArray());
    }

    private void removeQueuedLock(HashSet<Lock> locks, Lock exampleLock, int hash) {
        if (locks == null)
            return;
        Optional<Lock> other = locks.stream().filter(a -> a.compareTo(exampleLock) == 0).findFirst();
        if (other.isEmpty())
            return;
        Lock lock = other.get();
        if (lock.getServerHash() == hash)
            locks.remove(lock);
    }

    private void queueLock(HashSet<Lock> lockSet, ChannelIdentifier identifier, Lock lock, ServerConnection serverConnection) {
        if (lockSet.contains(lock)) {
            //Lock already queued we don't have to queue it again
            return;
        }
        Optional<Lock> optionalQueuedLock = lockSet.stream().filter(a -> a.compareTo(lock) != 0).findAny();
        if (optionalQueuedLock.isPresent()) {
            Lock queuedLock = optionalQueuedLock.get();
            Optional<RegisteredServer> optionalRegisteredServer = DataLock.getServer().getAllServers().stream()
                    .filter(sc -> queuedLock.getServerHash() == sc.getServerInfo().hashCode())
                    .findAny();
            if (optionalRegisteredServer.isPresent()) {
                //The server that queued this lock is still active, so we can't queue a new one
                RegisteredServer registeredServer = optionalRegisteredServer.get();
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("queue-lock-failed");
                out.writeUTF(queuedLock.getData());
                out.writeUTF(registeredServer.getServerInfo().getName());
                serverConnection.sendPluginMessage(identifier, out.toByteArray());
                return;
            }
            Logger.warn("Removing queued lock [%] due to being unable to find a server where that lock could be active", queuedLock.getData());
            lockSet.remove(queuedLock);
        }
        lockSet.add(lock);
        queuedLocks.put(identifier, lockSet);
    }

    private void queueNextLock(HashSet<Lock> lockSet, Lock lock, ChannelIdentifier identifier) {
        if (!queuedLocks.containsKey(identifier))
            return;
        HashSet<Lock> queuedLockSet = queuedLocks.get(identifier);
        Optional<Lock> optionalQueuedLock = queuedLockSet.stream().filter(l -> l.compareTo(lock) == 0).findFirst();
        if (optionalQueuedLock.isEmpty())
            return;
        Lock queuedLock = optionalQueuedLock.get();
        queuedLockSet.remove(lock);

        Optional<RegisteredServer> optionalRegisteredServer = DataLock.getServer().getAllServers().stream()
                .filter(registeredServer -> registeredServer.getServerInfo().hashCode() == queuedLock.getServerHash())
                .findAny();
        if (optionalRegisteredServer.isEmpty()) {
            Logger.warn("Removing queued lock [%] due to being unable to find a server where that lock could be active", queuedLock.getData());
            return;
        }
        RegisteredServer registeredServer = optionalRegisteredServer.get();
        lockSet.add(queuedLock);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("locked-queued-lock");
        out.writeUTF(queuedLock.getData());
        registeredServer.sendPluginMessage(identifier, out.toByteArray());
    }

}
