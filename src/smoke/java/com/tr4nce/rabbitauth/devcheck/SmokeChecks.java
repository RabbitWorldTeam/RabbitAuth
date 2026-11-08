package com.tr4nce.rabbitauth.devcheck;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Development-only mod entry point; excluded from the production artifact. */
@Mod(value = "rabbitauth_smoke", dist = Dist.DEDICATED_SERVER)
public final class SmokeChecks {
    private Object bridge;
    public SmokeChecks() {
        if (Boolean.getBoolean("rabbitauth.bridgeIntegration")) {
            NeoForge.EVENT_BUS.addListener(this::started);
            NeoForge.EVENT_BUS.addListener(this::tick);
            NeoForge.EVENT_BUS.addListener(this::stopping);
        }
    }
    private void started(ServerStartedEvent event) {
        try {
            bridge = Class.forName("com.tr4nce.rabbitcore.bridge.BridgeService").getConstructor(net.minecraft.server.MinecraftServer.class, String.class)
                    .newInstance(event.getServer(), "integration-fixture");
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
    private void tick(ServerTickEvent.Post event) { invoke("tick"); }
    private void stopping(ServerStoppingEvent event) { invoke("close"); bridge = null; }
    private void invoke(String method) {
        if (bridge != null) try { bridge.getClass().getMethod(method).invoke(bridge); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
}
