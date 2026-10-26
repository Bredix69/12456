package com.sculktitan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Скалк как еда: тот, кто получил силу титана, может есть скалк и лечиться.
 * Блок в мире - ПКМ пустой рукой. Предмет - ПКМ по воздуху (или Shift+ПКМ, если смотришь на блок).
 */
public final class SculkEating {
    /** heal - сколько здоровья (полсердца = 1), hunger - сколько еды, regen - даёт ли регенерацию. */
    public record Food(float heal, int hunger, boolean regen) {
    }

    private static final String K_LAST = "sculktitan_eat";
    private static final int EAT_COOLDOWN = 8;

    public static Food of(Block b) {
        if (b == Blocks.SCULK) {
            return new Food(4.0F, 1, false);          // 2 сердца
        }
        if (b == Blocks.SCULK_VEIN) {
            return new Food(1.0F, 0, false);          // полсердца
        }
        if (b == Blocks.SCULK_SENSOR) {
            return new Food(6.0F, 2, false);          // 3 сердца
        }
        if (b == Blocks.SCULK_SHRIEKER) {
            return new Food(8.0F, 2, false);          // 4 сердца
        }
        if (b == Blocks.SCULK_CATALYST) {
            return new Food(10.0F, 3, true);          // 5 сердец + регенерация
        }
        return null;
    }

    public static Food of(ItemStack s) {
        return s.getItem() instanceof BlockItem bi ? of(bi.getBlock()) : null;
    }

    public static boolean isFood(Block b) {
        return of(b) != null;
    }

    public static boolean isFood(ItemStack s) {
        return !s.isEmpty() && of(s) != null;
    }

    /** Только на сервере: есть ли у игрока сила титана (или он сейчас проходит испытание). */
    public static boolean canEat(ServerPlayer p) {
        return TitanData.isUnlocked(p) || TitanData.isTitan(p);
    }

    /** Съесть блок скалка в мире. */
    public static void eatBlock(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        Food f = of(block);
        if (f == null) {
            return;
        }
        if (consume(p, f, new ItemStack(block.asItem()))) {
            level.removeBlock(pos, false);
        }
    }

    /** Съесть скалк из руки. */
    public static void eatItem(ServerPlayer p, ItemStack stack) {
        Food f = of(stack);
        if (f == null) {
            return;
        }
        if (consume(p, f, stack.copy()) && !p.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    private static boolean consume(ServerPlayer p, Food f, ItemStack visual) {
        CompoundTag d = p.getPersistentData();
        long now = p.level().getGameTime();
        if (now - d.getLong(K_LAST) < EAT_COOLDOWN) {
            return false;
        }
        d.putLong(K_LAST, now);

        FoodData food = p.getFoodData();
        if (p.getHealth() >= p.getMaxHealth() && !food.needsFood()) {
            p.displayClientMessage(Component.translatable("message.sculktitan.full"), true);
            return false;
        }

        float mult = TitanData.isTitan(p) ? 2.0F : 1.0F; // титан получает вдвое больше
        p.heal(f.heal() * mult);
        if (f.hunger() > 0) {
            food.eat(f.hunger(), 0.5F);
        }
        if (f.regen()) {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        }

        ServerLevel level = p.serverLevel();
        double y = p.getEyeY() - 0.2;
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, visual), p.getX(), y, p.getZ(),
                10, 0.25, 0.15, 0.25, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), y, p.getZ(), 4, 0.3, 0.2, 0.3, 0.03);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.9F, 0.7F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 1.0F);
        return true;
    }

    private SculkEating() {
    }
}
