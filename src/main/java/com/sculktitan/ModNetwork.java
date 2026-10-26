package com.sculktitan;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;

import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SculkTitanMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, SyncPacket.class,
                SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
        CHANNEL.registerMessage(1, ToggleRequestPacket.class,
                ToggleRequestPacket::encode, ToggleRequestPacket::decode, ToggleRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, SelfStatePacket.class,
                SelfStatePacket::encode, SelfStatePacket::decode, SelfStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, SelectAbilityPacket.class,
                SelectAbilityPacket::encode, SelectAbilityPacket::decode, SelectAbilityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, CastAbilityPacket.class,
                CastAbilityPacket::encode, CastAbilityPacket::decode, CastAbilityPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Отправить личное состояние (способности, перезарядки) только самому игроку. */
    public static void sendSelf(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Отправить состояние игрока ему самому и всем, кто его видит. */
    public static void broadcast(ServerPlayer player, SyncPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    /** Отправить состояние игрока одному зрителю. */
    public static void sendTo(ServerPlayer viewer, SyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), packet);
    }

    private ModNetwork() {
    }
}
