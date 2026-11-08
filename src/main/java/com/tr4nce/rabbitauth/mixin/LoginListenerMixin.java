package com.tr4nce.rabbitauth.mixin;

import com.mojang.authlib.GameProfile;
import com.tr4nce.rabbitauth.RabbitAuth;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginListenerMixin {
    @Shadow @Final private Connection connection;
    @Shadow public abstract void disconnect(Component reason);
    @Inject(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$admit(GameProfile profile, CallbackInfo callback) {
        Component denial = RabbitAuth.serviceOrNull() == null
                ? com.tr4nce.rabbitauth.auth.AuthService.message(com.tr4nce.rabbitauth.i18n.MessageKey.NOT_READY) : RabbitAuth.service().admission(connection, profile);
        if (denial != null) { disconnect(denial); callback.cancel(); }
    }
}
