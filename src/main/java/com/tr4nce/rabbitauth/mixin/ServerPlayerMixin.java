package com.tr4nce.rabbitauth.mixin;

import com.tr4nce.rabbitauth.protection.Protection;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = {"tick", "doTick"}, at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$freeze(CallbackInfo callback) {
        if (Protection.locked((ServerPlayer) (Object) this)) callback.cancel();
    }
}
