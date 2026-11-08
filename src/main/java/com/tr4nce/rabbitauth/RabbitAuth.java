package com.tr4nce.rabbitauth;

import com.mojang.logging.LogUtils;
import com.tr4nce.rabbitauth.auth.AuthService;
import com.tr4nce.rabbitauth.command.AuthCommands;
import com.tr4nce.rabbitauth.config.AuthConfig;
import com.tr4nce.rabbitauth.storage.AccountStore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.nio.file.Path;

@Mod(value = RabbitAuth.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class RabbitAuth {
    public static final String MOD_ID = "rabbitauth";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile AuthService service;
    public RabbitAuth() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(this::starting);
        bus.addListener(this::stopped);
        bus.addListener(this::commands);
        bus.addListener(this::join);
        bus.addListener(this::leave);
        bus.addListener(this::respawn);
        bus.addListener(this::tick);
        bus.addListener(com.tr4nce.rabbitauth.protection.Protection::command);
        bus.addListener(com.tr4nce.rabbitauth.protection.Protection::chat);
        bus.addListener(com.tr4nce.rabbitauth.protection.Protection::damage);
    }
    public static AuthService serviceOrNull() { return service; }
    public static AuthService service() {
        AuthService result = service;
        if (result == null) throw new IllegalStateException("RabbitAuth is not ready");
        return result;
    }
    private void starting(ServerStartingEvent event) {
        Path directory = event.getServer().getServerDirectory().resolve("config/rabbitauth");
        try {
            AuthConfig config = new AuthConfig(directory);
            var messages = new com.tr4nce.rabbitauth.i18n.Messages(directory, config.text("language"));
            AccountStore store = new AccountStore(config, directory);
            try {
                AuthService initialized = new AuthService(event.getServer(), config, store, messages);
                if (config.flag("spawn-enabled")) com.tr4nce.rabbitauth.protection.Protection.spawn(initialized);
                service = initialized;
            } catch (Exception exception) { store.close(); throw exception; }
            warnBlindingConflict(event.getServer().getServerDirectory());
            LOGGER.info("RabbitAuth ready");
        } catch (Exception exception) { throw new IllegalStateException("RabbitAuth initialization failed; refusing unprotected startup", exception); }
    }
    private void warnBlindingConflict(Path directory) {
        Path plugins = directory.resolve("plugins");
        if (java.nio.file.Files.isDirectory(plugins)) {
            try (var paths = java.nio.file.Files.list(plugins)) {
                if (paths.anyMatch(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("catseedloginblinding")))
                    LOGGER.warn("CatSeedLoginBlinding detected in plugins/. Disable its blindness feature to avoid conflicting login effects; Bukkit/hybrid compatibility is not supported.");
            } catch (java.io.IOException exception) { LOGGER.warn("Unable to inspect optional plugin conflict directory", exception); }
        }
    }
    private void stopped(ServerStoppedEvent event) {
        AuthService old = service; service = null;
        if (old != null) try { old.close(); } catch (Exception exception) { LOGGER.error("RabbitAuth shutdown failed", exception); }
    }
    private void commands(RegisterCommandsEvent event) { AuthCommands.register(event.getDispatcher()); com.tr4nce.rabbitauth.command.WhitelistCommands.register(event.getDispatcher()); }
    private void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) service().join(player);
    }
    private void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (service != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) service.leave(player);
    }
    private void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (service != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) service.respawn(player);
    }
    private void tick(ServerTickEvent.Post event) { if (service != null) service.tick(); }
}
