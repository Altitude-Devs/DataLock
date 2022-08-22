package com.alttd.datalock;

import static org.junit.jupiter.api.Assertions.*;

class LockTest {

    @org.junit.jupiter.api.Test
    void testEquals() {
        assertTrue(new Lock(123, "test").equals(new Lock(123, "test")));
        assertFalse(new Lock(123, "test1").equals(new Lock(123, "test2")));
        assertFalse(new Lock(123, "test").equals(new Lock(-123, "test")));
    }

    @org.junit.jupiter.api.Test
    void testHashCode() {
        assertEquals(new Lock(123, "test").hashCode(), new Lock(123, "test").hashCode());
        assertNotEquals(new Lock(123, "test1").hashCode(), new Lock(123, "test2").hashCode());
        assertNotEquals(new Lock(123, "test").hashCode(), new Lock(-123, "test").hashCode());
    }

    @org.junit.jupiter.api.Test
    void compareTo() {
        assertEquals(0, new Lock(123, "test").compareTo(new Lock(123, "test")));
        assertNotEquals(0, new Lock(123, "test1").compareTo(new Lock(123, "test")));
        assertNotEquals(0, new Lock(123, "test").compareTo(new Lock(-123, "test")));
    }
}