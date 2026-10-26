package com.sculktitan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sculktitan.ClientState;
import com.sculktitan.SculkTitanMod;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Пульсирующее красное свечение рёбер, ядра и глаз титана в фазе берсерка. */
public class BerserkGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation GLOW =
            new ResourceLocation(SculkTitanMod.MODID, "textures/entity/sculk_titan_berserk_glow.png");

    public BerserkGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!ClientState.isBerserk(player.getUUID()) || player.isInvisible()) {
            return;
        }
        float pulse = 0.65F + 0.35F * Mth.sin(ageInTicks * 0.4F);
        this.getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(GLOW)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 1.0F);
    }
}
