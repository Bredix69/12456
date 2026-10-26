package com.sculktitan.client;

import java.util.Locale;

import com.sculktitan.Ability;
import com.sculktitan.Abilities;
import com.sculktitan.ClientAbilities;
import com.sculktitan.ClientState;
import com.sculktitan.SculkTitanMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** HUD: выбранная способность с перезарядкой (справа снизу) и полоса берсерка. */
@Mod.EventBusSubscriber(modid = SculkTitanMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HudOverlay {

    @SubscribeEvent
    public static void onRegister(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("abilities", HudOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) {
            return;
        }
        boolean titan = ClientState.isTitan(p.getUUID());
        if (!titan && !ClientAbilities.isUnlocked()) {
            return;
        }
        Font font = mc.font;

        // ---- берсерк: красная виньетка по краям + полоса времени
        int bers = ClientAbilities.berserkLeft();
        if (bers > 0 && titan) {
            int alpha = (int) (44 + 26 * Math.sin(Util.getMillis() / 150.0));
            int col = (alpha << 24) | 0xA00018;
            g.fillGradient(0, 0, w, h / 6, col, 0x00A00018);
            g.fillGradient(0, h - h / 6, w, h, 0x00A00018, col);

            int bw = 120;
            int bx = (w - bw) / 2;
            int by = 18;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xC0200006);
            g.fill(bx, by, bx + (int) (bw * (bers / (float) Abilities.BERSERK_TICKS)), by + 6, 0xFFE0243A);
            g.drawCenteredString(font, Component.translatable("hud.sculktitan.berserk",
                    String.format(Locale.ROOT, "%.0f", bers / 20.0)), w / 2, by - 11, 0xFFFF5566);
        }

        // ---- выбранная способность
        Ability a = Ability.of(titan, ClientAbilities.selected(titan));
        int cd = ClientAbilities.cooldown(a);
        Component line = Component.literal("[").append(KeyBindings.CAST.getTranslatedKeyMessage())
                .append("] ").append(Component.translatable(a.nameKey()));
        int pw = Math.max(110, font.width(line) + 12);
        int ph = 24;
        int x = w - pw - 6;
        int y = h - ph - 6;
        g.fill(x, y, x + pw, y + ph, 0xA0030D10);
        g.fill(x, y, x + pw, y + 1, 0xFF14797C);
        g.fill(x, y + ph - 1, x + pw, y + ph, 0xFF14797C);
        g.fill(x, y, x + 1, y + ph, 0xFF14797C);
        g.fill(x + pw - 1, y, x + pw, y + ph, 0xFF14797C);
        g.drawString(font, line, x + 6, y + 5, 0xFF3DF2E4, false);

        float frac = 1.0F - cd / (float) a.cooldown;
        g.fill(x + 6, y + ph - 8, x + pw - 6, y + ph - 5, 0xFF0A2A2D);
        g.fill(x + 6, y + ph - 8, x + 6 + (int) ((pw - 12) * frac), y + ph - 5, cd <= 0 ? 0xFF3DF2E4 : 0xFFFFB347);
    }

    private HudOverlay() {
    }
}
