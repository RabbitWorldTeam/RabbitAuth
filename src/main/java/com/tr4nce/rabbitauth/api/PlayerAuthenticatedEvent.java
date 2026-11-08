package com.tr4nce.rabbitauth.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the server thread after successful authentication. */
public final class PlayerAuthenticatedEvent extends Event {
    private final ServerPlayer player;
    public PlayerAuthenticatedEvent(ServerPlayer player) { this.player = player; }
    public ServerPlayer getPlayer() { return player; }
}
