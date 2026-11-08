package com.tr4nce.rabbitauth.auth;

import com.tr4nce.rabbitauth.storage.Account;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Process-local tickets; restarting the server always revokes them. */
public final class LoginTickets {
    private record Ticket(String ip, String passwordHash, long expires) {}
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    public LoginTickets() { this(System::nanoTime); }
    public LoginTickets(LongSupplier clock) { this.clock = clock; }
    public void issue(Account account, String ip, int minutes) {
        if (ip.isEmpty() || minutes <= 0) return;
        tickets.put(Account.key(account.name()), new Ticket(ip, account.passwordHash(),
                clock.getAsLong() + TimeUnit.MINUTES.toNanos(minutes)));
    }
    public boolean valid(Account account, String ip) {
        if (account == null || ip.isEmpty()) return false;
        Ticket ticket = tickets.get(Account.key(account.name()));
        return ticket != null && clock.getAsLong() < ticket.expires()
                && ticket.ip().equals(ip) && ticket.passwordHash().equals(account.passwordHash());
    }
    public void revoke(String name) { tickets.remove(Account.key(name)); }
    public void purge() { long now = clock.getAsLong(); tickets.entrySet().removeIf(e -> now >= e.getValue().expires()); }
}
