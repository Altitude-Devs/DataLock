package com.alttd.datalock;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

class Idempotency {

    private final HashMap<RequestType, HashSet<IdempotencyData>> idempotencyMap;

    protected Idempotency() {
        idempotencyMap = new HashMap<>();
    }

    private HashSet<IdempotencyData> getIdempotencySet(RequestType requestType) {
        return idempotencyMap.getOrDefault(requestType, new HashSet<>());
    }

    private void putIdempotencySet(RequestType requestType, HashSet<IdempotencyData> idempotencySet) {
        idempotencyMap.put(requestType, idempotencySet);
    }

    /**
     * Add IdempotencyData to the list of active queries
     * @param idempotencyData Data to add to the set
     * @return true if entry did not exist yet
     */
    protected synchronized boolean putIdempotencyData(RequestType requestType, IdempotencyData idempotencyData) {
        HashSet<IdempotencyData> idempotencySet = getIdempotencySet(requestType);
        boolean result = idempotencySet.add(idempotencyData);
        putIdempotencySet(requestType, idempotencySet);
        return result;
    }

    /**
     * Remove IdempotencyData from the list of active queries
     * @param idempotencyData Data to remove from the set
     * @return True if the data that was requested to be removed was in the set and was removed
     */
    protected synchronized boolean removeIdempotencyData(RequestType requestType, IdempotencyData idempotencyData) {
        HashSet<IdempotencyData> idempotencySet = getIdempotencySet(requestType);
        boolean result = idempotencySet.remove(idempotencyData);
        putIdempotencySet(requestType, idempotencySet);
        return result;
    }

    protected synchronized Set<IdempotencyData> getIdempotencyData(RequestType requestType) {
        return Collections.unmodifiableSet(getIdempotencySet(requestType));
    }
}
