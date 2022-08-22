package com.alttd.datalock;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class Lock implements Comparable {

    private final int serverHash;
    private final String data;

    public Lock(int serverHash, String data) {
        this.serverHash = serverHash;
        this.data = data;
    }

    public int getServerHash() {
        return serverHash;
    }

    public String getData() {
        return data;
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        Lock other = (Lock) o;
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return data.equals(other.data) && serverHash == other.serverHash;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(serverHash, data);
    }


    @Override
    public int compareTo(@NotNull Object o) {
        Lock lock = (Lock) o;
        int data = lock.data.compareTo(this.data);
        if (data != 0)
            return data;
        return Integer.compare(lock.getServerHash(), getServerHash());
    }
}
