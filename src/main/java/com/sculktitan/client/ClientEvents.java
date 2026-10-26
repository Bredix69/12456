package com.sculktitan.client;

import com.sculktitan.ClientAbilities;
import com.sculktitan.ClientState;
import com.sculktitan.SculkTitanMod;
import com.sculktitan.TitanData;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SculkTitanMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Защита от рекурсии: наш рендерер сам вызывает RenderPlayerEvent. */
    private static boolean rendering = false;
    /** Если рендер сломался - возвращаемся к обычному, чтобы игра не падала. */
    private static boolean bodyBroken = false;
    private static boolean armBroken = false;

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (rendering || bodyBroken) {
            return;
        }
        PlayerRenderer titan = ClientSetup.titanRenderer();
        Player player = event.getEntity();
        if (titan == null || !(player instanceof AbstractClientPlayer clientPlayer) || !TitanData.isTitan(player)) {
            return;
        }
        event.setCanceled(true);
        rendering = true;
        try {
            titan.render(clientPlayer, clientPlayer.getYRot(), event.getPartialTick(),
                    event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        } catch (Throwable t) {
            bodyBroken = true;
            SculkTitanMod.LOGGER.error("Sculk Titan: ошибка рендера титана", t);
        } finally {
            rendering = false;
        }
    }

    /** Рука титана от первого лица со скином титана. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (armBroken) {
            return;
        }
        PlayerRenderer renderer = ClientSetup.titanRenderer();
        AbstractClientPlayer player = event.getPlayer();
        if (!(renderer instanceof TitanPlayerRenderer titan) || !TitanData.isTitan(player)) {
            return;
        }
        event.setCanceled(true);
        try {
            titan.renderTitanArm(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), player, event.getArm());
        } catch (Throwable t) {
            armBroken = true;
            SculkTitanMod.LOGGER.error("Sculk Titan: ошибка рендера руки титана", t);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.clear();
        ClientAbilities.clear();
    }

    private ClientEvents() {
    }
}
