package com.alttd.datalock;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;

import java.util.*;

public class EventListener {

    private final HashMap<ChannelIdentifier, HashSet<Lock>> channelLockMap = new HashMap<>();
    private final static List<ChannelIdentifier> channelIdentifierList = new ArrayList<>();

    public EventListener(List<ChannelIdentifier> channelIdentifierList)
    {
        EventListener.reload(channelIdentifierList);
    }

    public static void reload(List<ChannelIdentifier> channelIdentifierList)
    {
        EventListener.channelIdentifierList.clear();
        EventListener.channelIdentifierList.addAll(channelIdentifierList);
    }

    @Subscribe
    public void onPluginMessageEvent(PluginMessageEvent event) {
        ChannelIdentifier identifier = event.getIdentifier();
        if (!EventListener.channelIdentifierList.contains(identifier))
            return;

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
        if (lockSet.contains(lock)) //An entry from this server already exists, so we can say that it's locked
        {
            out.writeBoolean(true);
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            return;
        }

        Optional<Lock> first = lockSet.stream().filter(a -> a.compareTo(lock) == 0).findFirst();
        if (first.isPresent()) //An entry from another server exists, so we can't lock it
        {
            out.writeBoolean(false);
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            return;
        }

        //Lock the data
        lockSet.add(lock);
        channelLockMap.put(identifier, lockSet);

        out.writeBoolean(true);
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

        serverConnection.sendPluginMessage(identifier, out.toByteArray());
    }

    private void tryUnlock(ChannelIdentifier identifier, HashSet<Lock> lockSet, String data, ServerConnection serverConnection) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("try-unlock-result");

        Lock lock = new Lock(serverConnection.getServerInfo().hashCode(), data);
        if (lockSet.contains(lock)) //Lock is in the list, but it's made by this server, so we can unlock it
        {
            out.writeBoolean(true);
            lockSet.remove(lock);
            channelLockMap.put(identifier, lockSet);
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            return;
        }

        Optional<Lock> first = lockSet.stream().filter(a -> a.compareTo(lock) == 0).findFirst();
        if (first.isEmpty()) //There is no entry with this data, so we can say it's unlocked
        {
            out.writeBoolean(true);
            serverConnection.sendPluginMessage(identifier, out.toByteArray());
            return;
        }

        //There is an entry with this data, but it's not owned by this server, so we can't unlock it
        out.writeBoolean(false);
        serverConnection.sendPluginMessage(identifier, out.toByteArray());
    }

}
