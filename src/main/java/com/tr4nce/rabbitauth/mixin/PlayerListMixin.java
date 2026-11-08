package com.tr4nce.rabbitauth.mixin;

import com.tr4nce.rabbitauth.protection.Protection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$preservePlayerData(ServerPlayer player, CallbackInfo callback) {
        // Never save the temporary authentication position or unauthenticated mutations.
        if (Protection.locked(player)) callback.cancel();
    }
}
