package com.alttd.datalock;

enum RequestType {
    TRY_LOCK("try-lock"),
    TRY_UNLOCK("try-unlock"),
    CHECK_LOCK("check-lock");

    String subChannel;

    RequestType(String subChannel) {
        this.subChannel = subChannel;
    }
}
