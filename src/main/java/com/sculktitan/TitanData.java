package com.sculktitan;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;

/** Вся серверная логика состояния «человек / скалковый титан». */
public final class TitanData {
    /** Во сколько раз титан больше игрока. */
    public static final float SCALE = 3.0F;
    /** Длительность превращения (тики). */
    public static final int ANIM_TICKS = 50;
    /** Остатки скалка на теле после возврата: 10-15 секунд. */
    public static final int REMNANTS_MIN = 200;
    public static final int REMNANTS_MAX = 300;

    /** Испытание после шприца: сколько времени игрок остаётся титаном (10 минут). */
    public static final int TRIAL_TICKS = 20 * 60 * 10;
    /** Пауза между превращениями по клавише (5 секунд). */
    public static final int TOGGLE_COOLDOWN = 100;

    public static final int MODE_GROW = 1;
    public static final int MODE_SHRINK = 2;

    private static final String K_TITAN = "sculktitan_titan";
    private static final String K_REM = "sculktitan_remnants";
    private static final String K_ANIM = "sculktitan_anim";
    private static final String K_MODE = "sculktitan_mode";
    private static final String K_UNLOCKED = "sculktitan_unlocked";
    private static final String K_TRIAL = "sculktitan_trial";
    private static final String K_CD = "sculktitan_cd";

    private static final UUID ID_HEALTH = UUID.fromString("7a3b9c10-1111-4222-8333-000000000001");
    private static final UUID ID_DAMAGE = UUID.fromString("7a3b9c10-1111-4222-8333-000000000002");
    private static final UUID ID_ARMOR = UUID.fromString("7a3b9c10-1111-4222-8333-000000000003");
    private static final UUID ID_KNOCKBACK = UUID.fromString("7a3b9c10-1111-4222-8333-000000000004");
    private static final UUID ID_SPEED = UUID.fromString("7a3b9c10-1111-4222-8333-000000000005");
    private static final UUID ID_BLOCK_REACH = UUID.fromString("7a3b9c10-1111-4222-8333-000000000006");
    private static final UUID ID_ENTITY_REACH = UUID.fromString("7a3b9c10-1111-4222-8333-000000000007");

    /** true на сервере читает сохранённые данные, на клиенте - то, что прислал сервер. */
    public static boolean isTitan(Player p) {
        Level level = p.level();
        if (level == null) {
            return false;
        }
        if (level.isClientSide) {
            return ClientState.isTitan(p.getUUID());
        }
        return p.getPersistentData().getBoolean(K_TITAN);
    }

    public static boolean isBusy(Player p) {
        return p.getPersistentData().getInt(K_ANIM) > 0;
    }

    /** Игрок уже прошёл испытание и может превращаться клавишей. */
    public static boolean isUnlocked(Player p) {
        return p.getPersistentData().getBoolean(K_UNLOCKED);
    }

    /** Сколько тиков осталось до конца испытания (0 - испытания нет). */
    public static int trialLeft(Player p) {
        return p.getPersistentData().getInt(K_TRIAL);
    }

    /** Переносим пробуждённую силу на нового игрока после смерти. */
    public static void copyUnlocked(Player original, Player fresh) {
        if (original.getPersistentData().getBoolean(K_UNLOCKED)) {
            fresh.getPersistentData().putBoolean(K_UNLOCKED, true);
            Abilities.copySelection(original, fresh);
        }
    }

    /** Нажатие клавиши превращения (пришло с клиента). */
    public static void requestToggle(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        if (!d.getBoolean(K_UNLOCKED)) {
            p.displayClientMessage(Component.translatable("message.sculktitan.locked"), true);
            return;
        }
        if (Abilities.berserk(p)) {
            p.displayClientMessage(Component.translatable("message.sculktitan.berserk_lock"), true);
            return;
        }
        if (isBusy(p) || d.getInt(K_TRIAL) > 0) {
            return;
        }
        if (d.getInt(K_CD) > 0) {
            p.displayClientMessage(Component.translatable("message.sculktitan.cooldown"), true);
            return;
        }
        startAnimation(p, d.getBoolean(K_TITAN) ? MODE_SHRINK : MODE_GROW);
    }

    public static int remnants(Player p) {
        return p.getPersistentData().getInt(K_REM);
    }

    /** Хватает ли места, чтобы вырасти. */
    public static boolean canGrow(Player p) {
        EntityDimensions dims = p.getDimensions(Pose.STANDING).scale(SCALE);
        AABB box = dims.makeBoundingBox(p.position());
        return p.level().noCollision(p, box);
    }

    // ------------------------------------------------------------------ анимация

    public static void startAnimation(ServerPlayer p, int mode) {
        if (isBusy(p)) {
            return;
        }
        if (mode == MODE_GROW && !canGrow(p)) {
            p.displayClientMessage(Component.translatable("message.sculktitan.no_space"), true);
            return;
        }
        CompoundTag d = p.getPersistentData();
        d.putInt(K_ANIM, ANIM_TICKS);
        d.putInt(K_MODE, mode);
        if (mode == MODE_GROW && d.getInt(K_REM) > 0) {
            d.putInt(K_REM, 0);
            sync(p);
        }
        // Во время превращения игрок почти не может двигаться
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ANIM_TICKS, 6, false, false, false));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR,
                SoundSource.PLAYERS, 1.6F, mode == MODE_GROW ? 0.7F : 1.1F);
    }

    /** Вызывается каждый тик на сервере. */
    public static void serverTick(ServerPlayer p) {
        safe(() -> Abilities.tick(p)); // перезарядки, активные способности, берсерк
        CompoundTag d = p.getPersistentData();
        int anim = d.getInt(K_ANIM);
        if (anim > 0) {
            int mode = d.getInt(K_MODE);
            final int elapsed = ANIM_TICKS - anim;
            safe(() -> TitanFx.animation(p, mode, elapsed));
            anim--;
            d.putInt(K_ANIM, anim);
            if (anim == 0) {
                finishAnimation(p, mode);
            }
            return;
        }

        int cd = d.getInt(K_CD);
        if (cd > 0) {
            d.putInt(K_CD, cd - 1);
        }

        // Испытание после шприца: 10 минут в облике титана, потом возврат в человека
        int trial = d.getInt(K_TRIAL);
        if (trial > 0 && d.getBoolean(K_TITAN)) {
            trial--;
            d.putInt(K_TRIAL, trial);
            if (trial == 0) {
                d.putBoolean(K_UNLOCKED, true);
                p.displayClientMessage(Component.translatable("message.sculktitan.awakened"), true);
                startAnimation(p, MODE_SHRINK);
            } else if (trial % 20 == 0) {
                int s = trial / 20;
                p.displayClientMessage(Component.translatable("message.sculktitan.trial_timer",
                        String.format("%d:%02d", s / 60, s % 60)), true);
            }
        }

        if (d.getBoolean(K_TITAN)) {
            if (p.tickCount % 20 == 0) {
                applyModifiers(p); // восстановит модификаторы, если их потеряли
                if (p.getHealth() < p.getMaxHealth()) {
                    p.heal(1.0F);
                }
            }
        }

        int rem = d.getInt(K_REM);
        if (rem > 0) {
            final int left = rem;
            safe(() -> TitanFx.remnants(p, left));
            rem--;
            d.putInt(K_REM, rem);
            if (rem == 0) {
                safe(() -> TitanFx.remnantsGone(p));
                sync(p);
            }
        }
    }

    private static void finishAnimation(ServerPlayer p, int mode) {
        CompoundTag d = p.getPersistentData();
        d.putInt(K_MODE, 0);
        if (mode == MODE_GROW) {
            if (!canGrow(p)) { // за время анимации место могло закончиться
                p.displayClientMessage(Component.translatable("message.sculktitan.no_space"), true);
                return;
            }
            d.putBoolean(K_TITAN, true);
            if (!d.getBoolean(K_UNLOCKED)) {
                d.putInt(K_TRIAL, TRIAL_TICKS); // первое превращение (шприцем): запускаем 10-минутный таймер
            }
            applyModifiers(p);
            p.setHealth(p.getMaxHealth());
            safe(() -> TitanFx.growBurst(p));
        } else {
            safe(() -> TitanFx.shrinkBurst(p));
            Abilities.endBerserk(p, false);
            d.putBoolean(K_TITAN, false);
            removeModifiers(p);
            d.putInt(K_REM, REMNANTS_MIN + p.getRandom().nextInt(REMNANTS_MAX - REMNANTS_MIN + 1));
        }
        d.putInt(K_CD, TOGGLE_COOLDOWN);
        sync(p);
    }

    /** Полный сброс (смерть). */
    public static void resetAll(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.remove(K_TITAN);
        d.remove(K_REM);
        d.remove(K_ANIM);
        d.remove(K_MODE);
        d.remove(K_TRIAL);
        d.remove(K_CD);
        removeModifiers(p);
        Abilities.resetAll(p);
        sync(p);
    }

    public static void clearOnLogin(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.remove(K_ANIM);
        d.remove(K_MODE);
        if (d.getBoolean(K_TITAN)) {
            applyModifiers(p);
        } else {
            removeModifiers(p);
        }
        Abilities.onLogin(p);
    }

    // ------------------------------------------------------------------ атрибуты

    public static void applyModifiers(Player p) {
        add(p, Attributes.MAX_HEALTH, ID_HEALTH, "Sculk titan health", 40.0, AttributeModifier.Operation.ADDITION);
        add(p, Attributes.ATTACK_DAMAGE, ID_DAMAGE, "Sculk titan damage", 8.0, AttributeModifier.Operation.ADDITION);
        add(p, Attributes.ARMOR, ID_ARMOR, "Sculk titan armor", 6.0, AttributeModifier.Operation.ADDITION);
        add(p, Attributes.KNOCKBACK_RESISTANCE, ID_KNOCKBACK, "Sculk titan knockback", 1.0, AttributeModifier.Operation.ADDITION);
        add(p, Attributes.MOVEMENT_SPEED, ID_SPEED, "Sculk titan speed", 0.15, AttributeModifier.Operation.MULTIPLY_BASE);
        add(p, ForgeMod.BLOCK_REACH.get(), ID_BLOCK_REACH, "Sculk titan block reach", 6.0, AttributeModifier.Operation.ADDITION);
        add(p, ForgeMod.ENTITY_REACH.get(), ID_ENTITY_REACH, "Sculk titan entity reach", 6.0, AttributeModifier.Operation.ADDITION);
    }

    public static void removeModifiers(Player p) {
        remove(p, Attributes.MAX_HEALTH, ID_HEALTH);
        remove(p, Attributes.ATTACK_DAMAGE, ID_DAMAGE);
        remove(p, Attributes.ARMOR, ID_ARMOR);
        remove(p, Attributes.KNOCKBACK_RESISTANCE, ID_KNOCKBACK);
        remove(p, Attributes.MOVEMENT_SPEED, ID_SPEED);
        remove(p, ForgeMod.BLOCK_REACH.get(), ID_BLOCK_REACH);
        remove(p, ForgeMod.ENTITY_REACH.get(), ID_ENTITY_REACH);
        if (p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
    }

    static void add(Player p, Attribute attribute, UUID id, String name, double value,
                            AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst != null && inst.getModifier(id) == null) {
            inst.addPermanentModifier(new AttributeModifier(id, name, value, op));
        }
    }

    static void remove(Player p, Attribute attribute, UUID id) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst != null && inst.getModifier(id) != null) {
            inst.removeModifier(id);
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            SculkTitanMod.LOGGER.error("Sculk Titan: ошибка эффекта", t);
        }
    }

    // ------------------------------------------------------------------ сеть

    public static void sync(ServerPlayer p) {
        ModNetwork.broadcast(p, packetFor(p));
        Abilities.syncSelf(p);
    }

    public static SyncPacket packetFor(ServerPlayer p) {
        return new SyncPacket(p.getUUID(), p.getPersistentData().getBoolean(K_TITAN), remnants(p), Abilities.berserk(p));
    }

    private TitanData() {
    }
}
