package com.sculktitan.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sculktitan.Ability;
import com.sculktitan.ClientAbilities;
import com.sculktitan.ClientState;
import com.sculktitan.ModNetwork;
import com.sculktitan.SelectAbilityPacket;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Меню выбора способностей: три карточки, как на референсе, только в скалковых цветах. */
public class AbilityScreen extends Screen {
    private static final int TEAL = 0xFF3DF2E4;
    private static final int TEAL_DIM = 0xFF14797C;
    private static final int TEXT = 0xFFA9CFCF;

    private final boolean titanForm;
    private final List<List<FormattedCharSequence>> desc = new ArrayList<>();
    private int cardW;
    private int cardH;
    private int gap;
    private int x0;
    private int y0;
    private int iconPx;
    private int pad;

    public AbilityScreen(boolean titanForm) {
        super(Component.translatable(titanForm ? "gui.sculktitan.title.titan" : "gui.sculktitan.title.human"));
        this.titanForm = titanForm;
    }

    @Override
    protected void init() {
        pad = 8;
        cardW = Mth.clamp((int) (width * 0.24F), 100, 150);
        gap = Math.max(8, cardW / 8);
        iconPx = (cardW >= 120 && height >= 240) ? 2 : 1;
        int iconSize = 32 * iconPx;
        x0 = (width - (cardW * 3 + gap * 2)) / 2;

        desc.clear();
        int maxLines = 0;
        for (int i = 0; i < Ability.PER_FORM; i++) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(Ability.of(titanForm, i).descKey()),
                    cardW - pad * 2);
            desc.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }
        cardH = pad + iconSize + 10 + 12 + 4 + maxLines * 9 + 10 + 10 + pad;
        y0 = Math.max(40, (height - cardH) / 2 + 4);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.player == null
                || ClientState.isTitan(minecraft.player.getUUID()) != titanForm) {
            onClose(); // форма сменилась - меню больше не актуально
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // фон: тёмный, с бирюзовым свечением снизу и горизонтальными «строками развёртки»
        g.fill(0, 0, width, height, 0xCC020B0D);
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x50106A6C);
        for (int y = 0; y < height; y += 2) {
            g.fill(0, y, width, y + 1, 0x16000000);
        }

        // заголовок (в 2 раза крупнее)
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(width / 2.0F, 12.0F, 0.0F);
        ps.scale(2.0F, 2.0F, 1.0F);
        g.drawCenteredString(font, title, 1, 1, 0xFF0B5E60);
        g.drawCenteredString(font, title, 0, 0, TEAL);
        ps.popPose();

        int bers = ClientAbilities.berserkLeft();
        if (bers > 0) {
            g.drawCenteredString(font, Component.translatable("gui.sculktitan.berserk_active",
                    String.format(Locale.ROOT, "%.0f", bers / 20.0)), width / 2, 34, 0xFFFF4455);
        }

        long time = Util.getMillis() / 50L;
        int selected = ClientAbilities.selected(titanForm);
        for (int i = 0; i < Ability.PER_FORM; i++) {
            Ability a = Ability.of(titanForm, i);
            int cx = x0 + i * (cardW + gap);
            int cy = y0;
            boolean sel = i == selected;
            boolean hover = mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH;

            g.fillGradient(cx, cy, cx + cardW, cy + cardH, hover ? 0xF00A2A30 : 0xF0061C21, 0xF002090B);
            border(g, cx, cy, cardW, cardH, sel ? TEAL : hover ? 0xFF2AB8B4 : TEAL_DIM);
            if (sel) {
                border(g, cx + 1, cy + 1, cardW - 2, cardH - 2, 0x803DF2E4);
                g.drawCenteredString(font, Component.translatable("gui.sculktitan.selected"),
                        cx + cardW / 2, cy - 10, TEAL);
            }

            int mid = cx + cardW / 2;
            AbilityIcons.draw(g, a, mid, cy + pad + 16 * iconPx, iconPx, time, sel || hover);

            int ny = cy + pad + 32 * iconPx + 10;
            g.drawCenteredString(font, Component.translatable(a.nameKey()), mid, ny, sel ? TEAL : 0xFFFFFFFF);

            int dy = ny + 16;
            for (FormattedCharSequence line : desc.get(i)) {
                g.drawString(font, line, cx + pad, dy, TEXT, false);
                dy += 9;
            }

            int cd = ClientAbilities.cooldown(a);
            Component status = cd <= 0
                    ? Component.translatable("gui.sculktitan.ready")
                    : Component.translatable("gui.sculktitan.cooldown", String.format(Locale.ROOT, "%.1f", cd / 20.0));
            g.drawCenteredString(font, status, mid, cy + cardH - pad - 8, cd <= 0 ? 0xFF55FF99 : 0xFFFFB347);
        }

        g.drawCenteredString(font, Component.translatable("gui.sculktitan.footer"), width / 2, height - 16, 0xFF8FB5B5);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void choose(int index) {
        if (index < 0 || index >= Ability.PER_FORM || index == ClientAbilities.selected(titanForm)) {
            return;
        }
        ClientAbilities.select(titanForm, index);
        ModNetwork.CHANNEL.sendToServer(new SelectAbilityPacket(titanForm, index));
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < Ability.PER_FORM; i++) {
                int cx = x0 + i * (cardW + gap);
                if (mouseX >= cx && mouseX < cx + cardW && mouseY >= y0 && mouseY < y0 + cardH) {
                    choose(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1-3 (обычные и цифровая клавиатура)
        if (keyCode >= 49 && keyCode <= 51) {
            choose(keyCode - 49);
            return true;
        }
        if (keyCode >= 321 && keyCode <= 323) { // KP_1..KP_3
            choose(keyCode - 321);
            return true;
        }
        if (keyCode == 263) { // влево
            choose(Math.max(0, ClientAbilities.selected(titanForm) - 1));
            return true;
        }
        if (keyCode == 262) { // вправо
            choose(Math.min(Ability.PER_FORM - 1, ClientAbilities.selected(titanForm) + 1));
            return true;
        }
        if (KeyBindings.MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
