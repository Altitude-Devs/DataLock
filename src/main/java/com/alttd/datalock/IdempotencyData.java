package com.alttd.datalock;

import java.util.UUID;

record IdempotencyData(RequestType channel, String data, UUID idempotencyToken) {
    @Override
    public String toString() {
        return "Channel: [" + channel + "] Data: [" + data + "] Idempotency Token: [" + idempotencyToken + "]";
    }
}