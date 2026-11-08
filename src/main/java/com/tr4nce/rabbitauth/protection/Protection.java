package com.tr4nce.rabbitauth.protection;

import com.tr4nce.rabbitauth.RabbitAuth;
import com.tr4nce.rabbitauth.auth.*;
import com.tr4nce.rabbitauth.storage.SavedPosition;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class Protection {
    private Protection() {}
    public static boolean locked(ServerPlayer player) {
        AuthService service = RabbitAuth.serviceOrNull();
        return service == null || !service.authenticated(player);
    }
    public static SavedPosition capture(ServerPlayer player) {
        return new SavedPosition(player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }
    public static void teleport(ServerPlayer player, SavedPosition position) {
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(position.dimension())));
        if (level == null) {
            level = player.server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            position = new SavedPosition(level.dimension().location().toString(), spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5, 0, 0);
        }
        player.stopRiding();
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.teleportTo(level, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }
    public static SavedPosition spawn(AuthService service) {
        var config = service.config;
        ServerLevel level = service.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(config.text("spawn-dimension"))));
        if (level == null) throw new IllegalArgumentException("Authentication dimension is unavailable");
        BlockPos spawn = level.getSharedSpawnPos();
        boolean custom = !config.text("spawn-x").isEmpty();
        if (custom) {
            SavedPosition result = new SavedPosition(level.dimension().location().toString(),
                    Double.parseDouble(config.text("spawn-x")), Double.parseDouble(config.text("spawn-y")),
                    Double.parseDouble(config.text("spawn-z")), 0, 0);
            if (!safe(level, BlockPos.containing(result.x(), result.y(), result.z())))
                throw new IllegalArgumentException("Authentication spawn needs solid ground, empty feet/head blocks and no fluids");
            return result;
        }
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                int x = spawn.getX() + dx, z = spawn.getZ() + dz;
                BlockPos candidate = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (safe(level, candidate))
                    return new SavedPosition(level.dimension().location().toString(), x + .5, candidate.getY(), z + .5, 0, 0);
            }
        }
        throw new IllegalArgumentException("No safe authentication spawn near shared spawn; configure spawn-x/y/z");
    }
    private static boolean safe(ServerLevel level, BlockPos position) {
        return level.getWorldBorder().isWithinBounds(position) && level.isInWorldBounds(position)
                && level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP)
                && level.getBlockState(position).isAir() && level.getBlockState(position.above()).isAir()
                && level.getFluidState(position.below()).isEmpty();
    }
    public static void enter(AuthSession session) {
        var service = RabbitAuth.service();
        if (service.config.flag("spawn-enabled")) teleport(session.player, spawn(service));
        session.anchor = capture(session.player);
        if (service.config.flag("blindness-before-login")) {
            session.player.connection.send(new ClientboundUpdateMobEffectPacket(session.player.getId(),
                    new MobEffectInstance(MobEffects.BLINDNESS, -1, 0, false, false), false));
        }
    }
    public static void exit(AuthSession session) {
        teleport(session.player, session.returnPosition);
        if (RabbitAuth.service().config.flag("blindness-before-login")) {
            session.player.connection.send(new ClientboundRemoveMobEffectPacket(session.player.getId(), MobEffects.BLINDNESS));
            MobEffectInstance original = session.player.getEffect(MobEffects.BLINDNESS);
            if (original != null) session.player.connection.send(new ClientboundUpdateMobEffectPacket(session.player.getId(), original, false));
        }
        session.player.inventoryMenu.sendAllDataToRemote();
        session.player.containerMenu.sendAllDataToRemote();
    }
    public static void tick(AuthSession session) {
        session.player.setDeltaMovement(Vec3.ZERO);
        session.player.fallDistance = 0;
        if (session.anchor != null && session.player.server.getTickCount() % 20 == 0) teleport(session.player, session.anchor);
    }
    public static boolean commandAllowed(String command) {
        AuthService service = RabbitAuth.serviceOrNull();
        return service != null && CommandPolicy.allowed(command, service.config.commandWhitelist());
    }
    public static void command(CommandEvent event) {
        ServerPlayer player = event.getParseResults().getContext().getSource().getPlayer();
        if (player != null && locked(player) && !commandAllowed(event.getParseResults().getReader().getString())) {
            event.setCanceled(true);
        }
    }
    public static void chat(ServerChatEvent event) {
        if (locked(event.getPlayer()) && !RabbitAuth.service().config.flag("before-login-allow-chat")) event.setCanceled(true);
    }
    public static void damage(LivingIncomingDamageEvent event) {
        if ((event.getEntity() instanceof ServerPlayer player && locked(player) && RabbitAuth.service().config.flag("no-damage-before-login"))
                || (event.getSource().getEntity() instanceof ServerPlayer attacker && locked(attacker))) event.setCanceled(true);
    }
    public static boolean allowInbound(Packet<?> packet) {
        // Deny gameplay packets by default, including unknown mod gameplay payloads.
        return packet instanceof ServerboundChatPacket || packet instanceof ServerboundChatCommandPacket
                || packet instanceof ServerboundChatCommandSignedPacket || packet instanceof ServerboundChatAckPacket
                || packet instanceof ServerboundChatSessionUpdatePacket || packet instanceof ServerboundAcceptTeleportationPacket
                || packet instanceof ServerboundChunkBatchReceivedPacket
                || packet instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
                || packet instanceof net.minecraft.network.protocol.common.ServerboundPongPacket
                || packet instanceof net.minecraft.network.protocol.common.ServerboundClientInformationPacket
                || packet instanceof net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Packet<?> mask(Packet<?> packet, ServerPlayer player) {
        AuthService service = RabbitAuth.serviceOrNull();
        if (!locked(player) || (service != null && !service.config.flag("hide-inventory"))) return packet;
        if (packet instanceof ClientboundContainerSetContentPacket content) {
            return new ClientboundContainerSetContentPacket(content.getContainerId(), content.getStateId(),
                    NonNullList.withSize(content.getItems().size(), ItemStack.EMPTY), ItemStack.EMPTY);
        }
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            return new ClientboundContainerSetSlotPacket(slot.getContainerId(), slot.getStateId(), slot.getSlot(), ItemStack.EMPTY);
        }
        if (packet instanceof ClientboundSetEquipmentPacket equipment && equipment.getEntity() == player.getId()) {
            return new ClientboundSetEquipmentPacket(equipment.getEntity(), equipment.getSlots().stream()
                    .map(pair -> com.mojang.datafixers.util.Pair.of(pair.getFirst(), ItemStack.EMPTY)).toList());
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
            for (Packet<? super ClientGamePacketListener> child : bundle.subPackets()) packets.add((Packet) mask(child, player));
            return new ClientboundBundlePacket(packets);
        }
        return packet;
    }
}
