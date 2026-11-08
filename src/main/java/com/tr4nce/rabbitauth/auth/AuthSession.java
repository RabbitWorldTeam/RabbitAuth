package com.tr4nce.rabbitauth.auth;

import net.minecraft.server.level.ServerPlayer;

public final class AuthSession {
    public final ServerPlayer player;
    public final String ip;
    public final long deadline;
    public volatile boolean authenticated;
    public volatile boolean busy;
    public com.tr4nce.rabbitauth.storage.SavedPosition returnPosition;
    public com.tr4nce.rabbitauth.storage.SavedPosition anchor;
    public boolean protectionReady;
    public boolean disconnected;
    public boolean rememberAllowed = true;
    public int failures;
    public long nextAttempt;
    public AuthSession(ServerPlayer player, String ip, long deadline) {
        this.player = player; this.ip = ip; this.deadline = deadline;
    }
}
