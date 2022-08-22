package com.alttd.datalock;

import static org.junit.jupiter.api.Assertions.*;

class LockTest {

    @org.junit.jupiter.api.Test
    void testEquals() {
        assertEquals(new Lock(123, "test"), new Lock(123, "test"));
        assertNotEquals(new Lock(123, "test1"), new Lock(123, "test2"));
        assertEquals(new Lock(123, "test"), new Lock(-123, "test"));
    }

    @org.junit.jupiter.api.Test
    void testHashCode() {
        assertEquals(new Lock(123, "test").hashCode(), new Lock(123, "test").hashCode());
        assertNotEquals(new Lock(123, "test1").hashCode(), new Lock(123, "test2").hashCode());
        assertEquals(new Lock(123, "test").hashCode(), new Lock(-123, "test").hashCode());
    }

    @org.junit.jupiter.api.Test
    void compareTo() {
        assertEquals(0, new Lock(123, "test").compareTo(new Lock(123, "test")));
        assertNotEquals(0, new Lock(123, "test1").compareTo(new Lock(123, "test")));
        assertNotEquals(0, new Lock(123, "test").compareTo(new Lock(-123, "test")));
    }
}