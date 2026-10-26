package com.sculktitan;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Сервер -> клиент (только самому игроку): силы, выбор способностей, перезарядки, берсерк. */
public record SelfStatePacket(boolean unlocked, int bersLeft, int bersCd, int selTitan, int selHuman, int[] cds) {

    public static void encode(SelfStatePacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.unlocked);
        buf.writeVarInt(p.bersLeft);
        buf.writeVarInt(p.bersCd);
        buf.writeVarInt(p.selTitan);
        buf.writeVarInt(p.selHuman);
        for (int i = 0; i < Ability.COUNT; i++) {
            buf.writeVarInt(i < p.cds.length ? p.cds[i] : 0);
        }
    }

    public static SelfStatePacket decode(FriendlyByteBuf buf) {
        boolean unlocked = buf.readBoolean();
        int bl = buf.readVarInt();
        int bc = buf.readVarInt();
        int st = buf.readVarInt();
        int sh = buf.readVarInt();
        int[] cds = new int[Ability.COUNT];
        for (int i = 0; i < cds.length; i++) {
            cds[i] = buf.readVarInt();
        }
        return new SelfStatePacket(unlocked, bl, bc, st, sh, cds);
    }

    public static void handle(SelfStatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                ClientAbilities.set(p.unlocked, p.bersLeft, p.bersCd, p.selTitan, p.selHuman, p.cds));
        ctx.get().setPacketHandled(true);
    }
}
