package com.sculktitan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sculktitan.ClientState;
import com.sculktitan.SculkTitanMod;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Остатки скалка на теле игрока после возврата в человеческий облик.
 * Держатся 10-15 секунд: сначала полностью, потом пятна постепенно исчезают.
 */
public class SculkRemnantsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation[] BASE = new ResourceLocation[3];
    private static final ResourceLocation[] GLOW = new ResourceLocation[3];

    static {
        for (int i = 0; i < 3; i++) {
            BASE[i] = new ResourceLocation(SculkTitanMod.MODID, "textures/entity/sculk_remnants_" + i + ".png");
            GLOW[i] = new ResourceLocation(SculkTitanMod.MODID, "textures/entity/sculk_remnants_glow_" + i + ".png");
        }
    }

    public SculkRemnantsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int remaining = ClientState.remnants(player.getUUID());
        if (remaining <= 0 || player.isInvisible()) {
            return;
        }
        int stage = remaining > 170 ? 0 : remaining > 85 ? 1 : 2;
        float alpha = Math.min(1.0F, remaining / 30.0F);

        poseStack.pushPose();
        // Чуть увеличиваем модель относительно ног, чтобы пятна лежали поверх скина
        poseStack.translate(0.0F, 1.5F, 0.0F);
        poseStack.scale(1.04F, 1.04F, 1.04F);
        poseStack.translate(0.0F, -1.5F, 0.0F);

        PlayerModel<AbstractClientPlayer> model = this.getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0.0F);

        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(BASE[stage])),
                packedLight, overlay, 1.0F, 1.0F, 1.0F, alpha);
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(GLOW[stage])),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, alpha, alpha, alpha, 1.0F);

        poseStack.popPose();
    }
}
