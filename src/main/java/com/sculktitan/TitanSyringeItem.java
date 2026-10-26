package com.sculktitan;

import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class TitanSyringeItem extends Item {

    public TitanSyringeItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 30;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (TitanData.isUnlocked(player)) {
                player.displayClientMessage(Component.translatable("message.sculktitan.already"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (TitanData.isBusy(player) || TitanData.isTitan(player)) {
                return InteractionResultHolder.fail(stack);
            }
            if (!TitanData.canGrow(player)) {
                player.displayClientMessage(Component.translatable("message.sculktitan.no_space"), true);
                return InteractionResultHolder.fail(stack);
            }
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            if (!TitanData.isUnlocked(player) && !TitanData.isTitan(player)) {
                TitanData.startAnimation(player, TitanData.MODE_GROW);
            }
            player.getCooldowns().addCooldown(this, 100);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sculktitan.titan_syringe.tooltip")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.sculktitan.titan_syringe.tooltip2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.sculktitan.titan_syringe.tooltip3")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
