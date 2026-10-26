package com.sculktitan;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Клиент -> сервер: игрок выбрал способность в меню. */
public record SelectAbilityPacket(boolean titan, int index) {

    public static void encode(SelectAbilityPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.titan);
        buf.writeVarInt(p.index);
    }

    public static SelectAbilityPacket decode(FriendlyByteBuf buf) {
        return new SelectAbilityPacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(SelectAbilityPacket p, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                Abilities.select(sender, p.titan, p.index);
            }
        });
        context.setPacketHandled(true);
    }
}
