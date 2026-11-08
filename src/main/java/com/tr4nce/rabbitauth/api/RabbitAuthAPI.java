package com.tr4nce.rabbitauth.api;

import com.tr4nce.rabbitauth.RabbitAuth;
import net.minecraft.server.level.ServerPlayer;

public final class RabbitAuthAPI {
    private RabbitAuthAPI() {}
    /** Trusted in-process interface. Callers must authorize external actors before invoking. */
    public static java.util.concurrent.CompletableFuture<java.util.Map<String, Object>> manageWhitelist(java.util.Map<String, String> request) {
        var service = RabbitAuth.serviceOrNull();
        if (service == null) return java.util.concurrent.CompletableFuture.completedFuture(java.util.Map.of(
                "ok", false, "code", "NOT_READY", "message", "RabbitAuth is not ready"));
        return service.whitelist().execute(request);
    }
    public static boolean isAuthenticated(ServerPlayer player) {
        var service = RabbitAuth.serviceOrNull();
        return service != null && service.authenticated(player);
    }
}
