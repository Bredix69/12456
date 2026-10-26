package com.sculktitan;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Клиент -> сервер: игрок нажал клавишу применения выбранной способности. */
public record CastAbilityPacket() {

    public static void encode(CastAbilityPacket p, FriendlyByteBuf buf) {
    }

    public static CastAbilityPacket decode(FriendlyByteBuf buf) {
        return new CastAbilityPacket();
    }

    public static void handle(CastAbilityPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                Abilities.cast(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
