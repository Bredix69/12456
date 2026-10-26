package com.sculktitan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sculktitan.SculkTitanMod;
import com.sculktitan.TitanData;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

/** Рендер игрока в облике скалкового титана: свой скин и увеличенная модель. */
public class TitanPlayerRenderer extends PlayerRenderer {
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(SculkTitanMod.MODID, "textures/entity/sculk_titan.png");

    public TitanPlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
        this.shadowRadius = 0.5F * TitanData.SCALE;
        this.addLayer(new BerserkGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
        return TEXTURE;
    }

    @Override
    protected void scale(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        super.scale(player, poseStack, partialTick);
        poseStack.scale(TitanData.SCALE, TitanData.SCALE, TitanData.SCALE);
    }

    /**
     * Рука от первого лица со скином титана.
     * Свой метод нужен, потому что renderRightHand/renderLeftHand сами вызывают RenderArmEvent
     * (иначе получится бесконечная рекурсия), а ванильная рука берёт скин самого игрока.
     */
    public void renderTitanArm(PoseStack poseStack, MultiBufferSource buffer, int light,
                               AbstractClientPlayer player, HumanoidArm arm) {
        PlayerModel<AbstractClientPlayer> model = this.getModel();
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart armPart = right ? model.rightArm : model.leftArm;
        ModelPart sleevePart = right ? model.rightSleeve : model.leftSleeve;

        armPart.visible = true;
        armPart.xRot = 0.0F;
        armPart.render(poseStack, buffer.getBuffer(RenderType.entitySolid(TEXTURE)), light, OverlayTexture.NO_OVERLAY);

        sleevePart.visible = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        sleevePart.xRot = 0.0F;
        sleevePart.render(poseStack, buffer.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
    }
}
