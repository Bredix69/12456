package com.sculktitan;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Сервер -> клиент: превращён ли игрок, в берсерке ли он и сколько тиков осталось остаткам скалка. */
public record SyncPacket(UUID id, boolean titan, int remnants, boolean berserk) {

    public static void encode(SyncPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.id);
        buf.writeBoolean(p.titan);
        buf.writeVarInt(p.remnants);
        buf.writeBoolean(p.berserk);
    }

    public static SyncPacket decode(FriendlyByteBuf buf) {
        return new SyncPacket(buf.readUUID(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SyncPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientState.set(p.id, p.titan, p.remnants, p.berserk));
        ctx.get().setPacketHandled(true);
    }
}
