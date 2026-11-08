package com.tr4nce.rabbitauth.mixin;

import com.tr4nce.rabbitauth.RabbitAuth;
import com.tr4nce.rabbitauth.auth.AuthService;
import com.tr4nce.rabbitauth.i18n.MessageKey;
import com.tr4nce.rabbitauth.protection.Protection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GameListenerMixin {
    @Shadow public ServerPlayer player;
    @Unique private final java.util.concurrent.atomic.AtomicBoolean rabbitAuth$queued = new java.util.concurrent.atomic.AtomicBoolean();

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$command(ServerboundChatCommandPacket packet, CallbackInfo callback) {
        if (rabbitAuth$handle(packet.command())) callback.cancel();
    }
    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$signedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo callback) {
        if (rabbitAuth$handle(packet.command())) callback.cancel();
    }
    @Unique private boolean rabbitAuth$handle(String input) {
        String rawRoot = input.split(" ", 2)[0];
        String root = rawRoot.startsWith("rabbitauth:") ? rawRoot.substring("rabbitauth:".length()) : rawRoot;
        if (root.equals("rabbitauth") && input.matches("\\S+ +whitelist(?: .*|$)")) return Protection.locked(player);
        boolean reset = root.equals("rabbitauth");
        if (!root.equals("login") && !root.equals("register") && !root.equals("changepassword") && !reset) {
            return Protection.locked(player) && !Protection.commandAllowed(input);
        }
        if (!rabbitAuth$queued.compareAndSet(false, true)) return true;
        ServerPlayer original = player;
        // Keep authentication secrets out of the normal command dispatch and error feedback.
        original.server.execute(() -> {
            try {
                if (player != original || !original.connection.isAcceptingMessages()) return;
                String[] parts = input.split(" ", -1);
                int expected = reset ? 4 : root.equals("login") ? 2 : 3;
                if (parts.length != expected || java.util.Arrays.stream(parts).anyMatch(String::isBlank)) {
                    AuthService.send(original, reset ? MessageKey.RESET_USAGE : MessageKey.USAGE);
                    return;
                }
                if (reset) {
                    if (!parts[1].equals("resetpassword")) { AuthService.send(original, MessageKey.RESET_USAGE); return; }
                    RabbitAuth.service().resetPassword(original.createCommandSourceStack(), parts[2], parts[3]);
                } else {
                    RabbitAuth.service().submit(original, root.equals("changepassword") ? "change" : root,
                            parts[1], parts.length == 3 ? parts[2] : "");
                }
            } finally { rabbitAuth$queued.set(false); }
        });
        return true;
    }
}
