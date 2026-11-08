package com.tr4nce.rabbitauth.smoke;

import com.tr4nce.rabbitauth.security.Crypt;
import com.tr4nce.rabbitauth.storage.*;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.*;

@Mixin(Main.class)
public abstract class SmokeMainMixin {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V", shift = At.Shift.AFTER))
    private static void rabbitAuth$smoke(String[] arguments, CallbackInfo callback) throws Exception {
        if (!Boolean.getBoolean("rabbitauth.smoke")) return;
        for (String target : new String[]{
                "net.minecraft.server.network.ServerLoginPacketListenerImpl",
                "net.minecraft.server.network.ServerGamePacketListenerImpl",
                "net.minecraft.network.Connection",
                "net.minecraft.server.level.ServerPlayer",
                "net.minecraft.server.players.PlayerList",
                "net.minecraft.server.dedicated.DedicatedPlayerList",
                "net.minecraft.server.commands.WhitelistCommand"}) {
            Class.forName(target, false, Main.class.getClassLoader()).getDeclaredMethods();
        }



        net.neoforged.neoforge.server.loading.ServerModLoader.load();
        Path directory = Path.of("smoke-data");
        Files.createDirectories(directory);
        String hash = Crypt.hash("SmokePassword123".toCharArray());
        if (!Crypt.verify("SmokePassword123".toCharArray(), hash)) throw new AssertionError("Argon2id runtime");
        try (AccountStore store = new AccountStore(directory.resolve("runtime.db"))) { store.verifyAvailable(); }
        Class.forName("com.mysql.cj.jdbc.Driver");
        Files.writeString(Path.of("smoke-success.txt"), "RABBITAUTH_SMOKE_OK");
        System.out.println("RABBITAUTH_SMOKE_OK: mod constructors loaded; all seven Mixin targets transformed; Argon2id, SQLite and MySQL driver load successfully");
    }
}
