package com.tr4nce.rabbitauth.mixin;

import com.mojang.authlib.GameProfile;
import com.tr4nce.rabbitauth.RabbitAuth;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DedicatedPlayerList.class)
public abstract class DedicatedWhitelistMixin {
    @Inject(method = "isWhiteListed", at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$whitelist(GameProfile profile, CallbackInfoReturnable<Boolean> callback) {
        var service = RabbitAuth.serviceOrNull();
        callback.setReturnValue(service != null && service.whitelist().store().allows(profile.getName()));
    }
}
