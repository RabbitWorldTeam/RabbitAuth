package com.tr4nce.rabbitauth.auth;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Failed attempts remain limited across reconnects. Bounded to prevent address-map growth. */
public final class FailureLimiter {
    private record Window(int count, long expires) {}
    private final Map<String, Window> failures = new HashMap<>();
    private final LongSupplier clock;
    public FailureLimiter() { this(System::nanoTime); }
    public FailureLimiter(LongSupplier clock) { this.clock = clock; }
    public synchronized boolean blocked(String ip, int limit) {
        purge();
        Window window = failures.get(ip);
        return window == null ? failures.size() >= 4096 : window.count() >= limit;
    }
    public synchronized void fail(String ip) {
        long now = clock.getAsLong();
        purge();
        Window old = failures.get(ip);
        if (old != null) failures.put(ip, new Window(old.count() + 1, old.expires()));
        else if (failures.size() < 4096) failures.put(ip, new Window(1, now + TimeUnit.MINUTES.toNanos(5)));
    }
    public synchronized void purge() {
        long now = clock.getAsLong();
        failures.entrySet().removeIf(e -> now >= e.getValue().expires());
    }
}
