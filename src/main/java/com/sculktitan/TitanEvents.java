package com.sculktitan;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SculkTitanMod.MODID)
public final class TitanEvents {

    /** Для каких игроков (отдельно на клиенте и сервере) размер хитбокса уже подогнан под состояние. */
    private static final Map<Player, Boolean> DIM_APPLIED = Collections.synchronizedMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Player p = e.player;

        boolean titan = TitanData.isTitan(p);
        boolean applied = DIM_APPLIED.getOrDefault(p, Boolean.FALSE);
        if (titan != applied) {
            DIM_APPLIED.put(p, titan);
            p.refreshDimensions();
            p.setMaxUpStep(titan ? 2.1F : 0.6F);
        }

        if (p.level().isClientSide) {
            ClientState.tick(p.getUUID());
        } else if (p instanceof ServerPlayer sp) {
            TitanData.serverTick(sp);
        }
    }

    /** Увеличиваем хитбокс титана (и высоту глаз). */
    @SubscribeEvent
    public static void onSize(EntityEvent.Size e) {
        if (e.getEntity() instanceof Player p && TitanData.isTitan(p)) {
            // Хитбокс и высота глаз (камеры) увеличиваются вместе: камера оказывается на уровне глаз титана
            e.setNewEyeHeight(e.getNewEyeHeight() * TitanData.SCALE);
            e.setNewSize(e.getNewSize().scale(TitanData.SCALE));
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof Player p && TitanData.isTitan(p)) {
            e.setDamageMultiplier(0.2F);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            TitanData.resetAll(sp);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer sp) {
            TitanData.resetAll(sp);
            TitanData.copyUnlocked(e.getOriginal(), sp);
        }
    }

    /** Вампиризм берсерка. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof ServerPlayer sp && e.getSource().getDirectEntity() == sp) {
            Abilities.onMeleeHit(sp, e.getAmount());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Abilities.onLogout(sp);
        }
    }

    /** Поедание скалка: блок пустой рукой или предмет в руке при Shift. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player p = e.getEntity();
        Level level = e.getLevel();
        ItemStack held = e.getItemStack();

        if (level.isClientSide) {
            // клиент только отменяет своё предсказание (например, установку блока); решает сервер
            if (!ClientAbilities.isUnlocked() && !ClientState.isTitan(p.getUUID())) {
                return;
            }
            boolean eatBlock = held.isEmpty() && SculkEating.isFood(level.getBlockState(e.getPos()).getBlock());
            boolean eatItem = p.isShiftKeyDown() && SculkEating.isFood(held);
            if (eatBlock || eatItem) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        if (!(p instanceof ServerPlayer sp) || !SculkEating.canEat(sp)) {
            return;
        }
        if (held.isEmpty() && SculkEating.isFood(level.getBlockState(e.getPos()).getBlock())) {
            SculkEating.eatBlock(sp, e.getPos());
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        } else if (sp.isShiftKeyDown() && SculkEating.isFood(held)) {
            SculkEating.eatItem(sp, held);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Поедание скалка из руки (ПКМ по воздуху). */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack held = e.getItemStack();
        if (SculkEating.isFood(held) && SculkEating.canEat(sp)) {
            SculkEating.eatItem(sp, held);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            TitanData.clearOnLogin(sp);
            TitanData.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            TitanData.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            TitanData.sync(sp);
        }
    }

    /** Когда игрок появляется в поле зрения другого игрока - отправляем ему состояние. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendTo(viewer, TitanData.packetFor(target));
        }
    }

    private TitanEvents() {
    }
}
