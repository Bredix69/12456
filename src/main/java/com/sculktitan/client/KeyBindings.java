package com.sculktitan.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sculktitan.CastAbilityPacket;
import com.sculktitan.ClientAbilities;
import com.sculktitan.ClientState;
import com.sculktitan.ModNetwork;
import com.sculktitan.SculkTitanMod;
import com.sculktitan.ToggleRequestPacket;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши мода: G - превращение (как раньше), R - меню способностей, V - применить выбранную способность.
 * Все меняются в Настройки → Управление → «Скалковый титан».
 */
public final class KeyBindings {
    public static final KeyMapping TRANSFORM = new KeyMapping(
            "key.sculktitan.transform", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.sculktitan");
    public static final KeyMapping MENU = new KeyMapping(
            "key.sculktitan.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.sculktitan");
    public static final KeyMapping CAST = new KeyMapping(
            "key.sculktitan.cast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.sculktitan");

    @Mod.EventBusSubscriber(modid = SculkTitanMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(TRANSFORM);
            event.register(MENU);
            event.register(CAST);
        }

        private ModBus() {
        }
    }

    @Mod.EventBusSubscriber(modid = SculkTitanMod.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.isPaused()) {
                ClientAbilities.tick();
            }

            while (TRANSFORM.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    ModNetwork.CHANNEL.sendToServer(new ToggleRequestPacket());
                }
            }
            while (MENU.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    boolean titan = ClientState.isTitan(mc.player.getUUID());
                    if (!titan && !ClientAbilities.isUnlocked()) {
                        mc.player.displayClientMessage(Component.translatable("message.sculktitan.locked"), true);
                    } else {
                        mc.setScreen(new AbilityScreen(titan));
                    }
                }
            }
            while (CAST.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    ModNetwork.CHANNEL.sendToServer(new CastAbilityPacket());
                }
            }
        }

        private ForgeBus() {
        }
    }

    private KeyBindings() {
    }
}
