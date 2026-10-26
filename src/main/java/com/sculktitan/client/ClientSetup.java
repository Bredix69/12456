package com.sculktitan.client;

import com.sculktitan.SculkTitanMod;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SculkTitanMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private static PlayerRenderer titanRenderer;

    public static PlayerRenderer titanRenderer() {
        return titanRenderer;
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : new String[]{"default", "slim"}) {
            EntityRenderer<? extends Player> renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new SculkRemnantsLayer(playerRenderer));
            }
        }
        // Скин титана - Alex (тонкие руки)
        titanRenderer = new TitanPlayerRenderer(event.getContext(), true);
    }

    private ClientSetup() {
    }
}
