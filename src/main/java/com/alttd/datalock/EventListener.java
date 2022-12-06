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

    private EventListener() {}

//    private final Idempotency idempotencyStorage = new Idempotency();
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
        channelLockMap.keySet().forEach(key -> {
            HashSet<Lock> temp = new HashSet<>();
            HashSet<Lock> locks = channelLockMap.get(key);
            for (Lock lock : locks) {
                if (lock.getServerHash() == hashCode)
                    temp.add(lock);
            }
            for (Lock lock : temp) {
                locks.remove(lock);
                queueNextLock(locks, lock, key, null);
                if (Config.DEBUG)
                    Logger.info("Clearing % from % due to clear server being called for the server that lock is on", lock.getData(), key.getId());
            }
            channelLockMap.put(key, locks);
        });
    }

    private String formatLockMap(HashMap<ChannelIdentifier, HashSet<Lock>> map) {
        StringBuilder stringBuilder = new StringBuilder();
        for (ChannelIdentifier plugin : map.keySet()) {
            stringBuilder
                    .append(plugin)
                    .append(": ")
                    .append(map.get(plugin).size())
                    .append(" entries\n")
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
            Logger.info("Current locks:\n%\nQueued locks:\n%", formatLockMap(channelLockMap), formatLockMap(queuedLocks));

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

        if (!isValid(channel.toLowerCase(), data))
            return;

        UUID idempotency;
        try {
            idempotency = UUID.fromString(in.readUTF());
        } catch (Exception e) {
            Logger.error("No idempotency key found.",
                    identifier.getId());
            idempotency = null; //TODO change this to return
        }


        if (Config.DEBUG)
            Logger.info("Plugin message channel: [%]", channel.toLowerCase());

        Optional<RequestType> first = Arrays.stream(RequestType.values()).filter(value -> value.subChannel.equalsIgnoreCase(channel)).findFirst();
        if (first.isEmpty()) {
            Logger.warn("Received invalid request type [%]", channel.toLowerCase());
            return;
        }
//        RequestType requestType = first.get();
//        if (!idempotencyStorage.putIdempotencyData(requestType, new IdempotencyData(requestType, data, idempotency)))
//            return;
//        switch (requestType) {
//
//        }
        switch (channel.toLowerCase()) { //TODO something with idempotency for function
            case "try-lock" -> tryLock(identifier, hashLock, data, idempotency, serverConnection);
            case "check-lock" -> checkLock(identifier, hashLock, data, idempotency, serverConnection);
            case "try-unlock" -> tryUnlock(identifier, hashLock, data, idempotency, serverConnection);
        }
    }

    private final HashMap<String, Long> validMap = new HashMap<>();
    private synchronized boolean isValid(String channel, String data) {
        String key = channel + data;
        long currentTime = new Date().getTime();
        if (validMap.containsKey(key)) {
            Long time = validMap.get(key);
            if (time < (currentTime - 1000)) {
                validMap.remove(key);
                return true;
            }
            return false;
        } else {
            validMap.put(key, currentTime);
            return true;
        }
    }

    private void sendPluginMessage(String channel, boolean result, String data, UUID idempotency, ServerConnection serverConnection, ChannelIdentifier identifier) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(channel);
        out.writeBoolean(result);
        out.writeUTF(data);
        out.writeUTF(idempotency.toString());
        serverConnection.sendPluginMessage(identifier, out.toByteArray());
    }
    private void tryLock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, UUID idempotency, ServerConnection serverConnection) {
        String channel = "try-lock-result";

        Lock lock = new Lock(serverConnection.getServerInfo().hashCode(), data);
        if (lockSet.contains(lock)) {
            //An entry from this server already exists, so we can say that it's locked
            sendPluginMessage(channel, true, lock.getData(), idempotency, serverConnection, identifier);
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
                sendPluginMessage(channel, false, lock.getData(), idempotency, serverConnection, identifier);
                queueLock(queuedLocks.getOrDefault(identifier, new HashSet<>()), identifier, lock, serverConnection, idempotency);
                return;
            }
        }

        //Lock the data
        lockSet.add(lock);
        channelLockMap.put(identifier, lockSet);
        sendPluginMessage(channel, true, lock.getData(), idempotency, serverConnection, identifier);
    }

    private void checkLock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, UUID idempotency, ServerConnection serverConnection) {
        Lock lock = new Lock(serverConnection.hashCode(), data);
        String channel = "check-lock-result";
        boolean result;

        if (lockSet.contains(lock)) //We locked this, but we still return true since it's locked
            result = true;
        else if (lockSet.stream().anyMatch(a -> a.compareTo(lock) == 0))
            result = true; //There is a lock (not ours, but it's still locked)
        else
            result = false; //The data is not locked

        sendPluginMessage(channel, result, lock.getData(), idempotency, serverConnection, identifier);
    }

    private void tryUnlock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, UUID idempotency, ServerConnection serverConnection) {
        int hash = serverConnection.getServerInfo().hashCode();
        String channel = "try-unlock-result";

        Lock lock = new Lock(hash, data);
        if (lockSet.contains(lock)) //Lock is in the list, but it's made by this server, so we can unlock it
        {
            lockSet.remove(lock);
            queueNextLock(lockSet, lock, identifier, idempotency);
            channelLockMap.put(identifier, lockSet);
            sendPluginMessage(channel, true, lock.getData(), idempotency, serverConnection, identifier);
            return;
        }

        Optional<Lock> first = lockSet.stream().filter(a -> a.compareTo(lock) == 0).findFirst();
        if (first.isEmpty()) //There is no entry with this data, so we can say it's unlocked
        {
            removeQueuedLock(queuedLocks.get(identifier), lock, hash);
            sendPluginMessage(channel, true, lock.getData(), idempotency, serverConnection, identifier);
            queueNextLock(lockSet, lock, identifier, idempotency);
            return;
        }

        //There is an entry with this data, but it's not owned by this server, so we can't unlock it
        sendPluginMessage(channel, false, lock.getData(), idempotency, serverConnection, identifier);
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

    private void queueLock(HashSet<Lock> lockSet, ChannelIdentifier identifier, Lock lock, ServerConnection serverConnection, UUID idempotency) {
        String channel = "queue-lock-failed";
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
//                RegisteredServer registeredServer = optionalRegisteredServer.get(); todo this was once used in the plugin message, check if it was needed
                sendPluginMessage(channel, false, queuedLock.getData(), idempotency, serverConnection, identifier);
                return;
            }
            Logger.warn("Removing queued lock [%] due to being unable to find a server where that lock could be active", queuedLock.getData());
            lockSet.remove(queuedLock);
        }
        lockSet.add(lock);
        queuedLocks.put(identifier, lockSet);
    }

    private void queueNextLock(HashSet<Lock> lockSet, Lock lock, ChannelIdentifier identifier, UUID idempotency) {
        String channel = "locked-queued-lock";
        if (!queuedLocks.containsKey(identifier))
            return;
        HashSet<Lock> queuedLockSet = queuedLocks.get(identifier);
        Optional<Lock> optionalQueuedLock = queuedLockSet.stream().filter(l -> l.getData().equals(lock.getData())).findFirst();
        if (optionalQueuedLock.isEmpty())
            return;
        Lock queuedLock = optionalQueuedLock.get();
        queuedLockSet.remove(queuedLock);
        queuedLocks.put(identifier, queuedLockSet);

        Optional<RegisteredServer> optionalRegisteredServer = DataLock.getServer().getAllServers().stream()
                .filter(registeredServer -> registeredServer.getServerInfo().hashCode() == queuedLock.getServerHash())
                .findAny();
        if (optionalRegisteredServer.isEmpty()) {
            Logger.warn("Removing queued lock [%] due to being unable to find a server where that lock could be active", queuedLock.getData());
            queueNextLock(lockSet, lock, identifier, idempotency);
            return;
        }
        RegisteredServer registeredServer = optionalRegisteredServer.get();
        lockSet.add(queuedLock);
        sendPluginMessage(channel, true, queuedLock.getData(), idempotency, (ServerConnection) registeredServer, identifier); //TODO test if this cast works
    }

}
