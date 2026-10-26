package com.sculktitan;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Клиент -> сервер: игрок нажал клавишу превращения. */
public record ToggleRequestPacket() {

    public static void encode(ToggleRequestPacket p, FriendlyByteBuf buf) {
    }

    public static ToggleRequestPacket decode(FriendlyByteBuf buf) {
        return new ToggleRequestPacket();
    }

    public static void handle(ToggleRequestPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                TitanData.requestToggle(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
