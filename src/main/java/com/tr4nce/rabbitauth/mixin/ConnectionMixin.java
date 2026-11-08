package com.tr4nce.rabbitauth.mixin;

import com.tr4nce.rabbitauth.protection.Protection;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow public abstract PacketListener getPacketListener();
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void rabbitAuth$inbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callback) {
        if (getPacketListener() instanceof ServerGamePacketListenerImpl listener && Protection.locked(listener.player)
                && !Protection.allowInbound(packet)) callback.cancel();
    }
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> rabbitAuth$outbound(Packet<?> packet) {
        return getPacketListener() instanceof ServerGamePacketListenerImpl listener ? Protection.mask(packet, listener.player) : packet;
    }
}
