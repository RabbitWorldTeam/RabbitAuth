package com.tr4nce.rabbitauth.mixin;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WhitelistCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WhitelistCommand.class)
public abstract class VanillaWhitelistCommandMixin {
    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void rabbitAuth$replace(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo callback) {
        callback.cancel();
    }
}
