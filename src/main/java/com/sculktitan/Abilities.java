package com.sculktitan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Серверная логика способностей (титан + человек) и фазы берсерка титана.
 * Всё состояние лежит в persistent data игрока, а текущие "длинные" способности
 * (волна, шипы, пожирание) - во временных задачах, которые тикают каждый тик.
 */
public final class Abilities {
    private static final String K_CDS = "sculktitan_cds";
    private static final String K_SEL = "sculktitan_sel";
    private static final String K_BERS = "sculktitan_bers";
    private static final String K_BERS_CD = "sculktitan_bers_cd";

    /** Длительность фазы берсерка: 30 секунд. */
    public static final int BERSERK_TICKS = 20 * 30;
    /** Перезарядка берсерка после окончания: 2 минуты. */
    public static final int BERSERK_COOLDOWN = 20 * 120;
    /** Берсерк включается, когда здоровье титана упало до этой доли от максимума. */
    public static final float BERSERK_HP = 0.35F;
    /** Во сколько раз сильнее способности в берсерке. */
    public static final float BERSERK_POWER = 1.6F;

    private static final UUID ID_B_DAMAGE = UUID.fromString("7a3b9c10-1111-4222-8333-000000000011");
    private static final UUID ID_B_SPEED = UUID.fromString("7a3b9c10-1111-4222-8333-000000000012");
    private static final UUID ID_B_ATKSPD = UUID.fromString("7a3b9c10-1111-4222-8333-000000000013");
    private static final UUID ID_B_ARMOR = UUID.fromString("7a3b9c10-1111-4222-8333-000000000014");

    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.16F, 0.88F, 0.92F), 1.3F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.04F, 0.14F, 0.19F), 1.4F);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.85F, 0.05F, 0.12F), 1.6F);

    /** Длительная способность, которая тикает каждый тик, пока не вернёт true. */
    private interface Task {
        boolean tick(ServerPlayer p);
    }

    private static final Map<UUID, List<Task>> TASKS = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ данные

    public static int[] cooldowns(Player p) {
        int[] a = p.getPersistentData().getIntArray(K_CDS);
        return a.length == Ability.COUNT ? a.clone() : new int[Ability.COUNT];
    }

    public static int selected(Player p, boolean titan) {
        int[] s = p.getPersistentData().getIntArray(K_SEL);
        if (s.length != 2) {
            return 0;
        }
        return Mth.clamp(s[titan ? 0 : 1], 0, Ability.PER_FORM - 1);
    }

    public static void select(ServerPlayer p, boolean titan, int index) {
        int[] s = p.getPersistentData().getIntArray(K_SEL);
        int[] n = new int[]{s.length == 2 ? s[0] : 0, s.length == 2 ? s[1] : 0};
        n[titan ? 0 : 1] = Mth.clamp(index, 0, Ability.PER_FORM - 1);
        p.getPersistentData().putIntArray(K_SEL, n);
        syncSelf(p);
    }

    public static void copySelection(Player original, Player fresh) {
        int[] s = original.getPersistentData().getIntArray(K_SEL);
        if (s.length == 2) {
            fresh.getPersistentData().putIntArray(K_SEL, s.clone());
        }
    }

    public static boolean berserk(Player p) {
        return p.getPersistentData().getInt(K_BERS) > 0;
    }

    /** Множитель силы способностей и взрывов. */
    public static float power(Player p) {
        return berserk(p) ? BERSERK_POWER : 1.0F;
    }

    public static void syncSelf(ServerPlayer p) {
        if (p.connection == null) {
            return;
        }
        CompoundTag d = p.getPersistentData();
        ModNetwork.sendSelf(p, new SelfStatePacket(TitanData.isUnlocked(p), d.getInt(K_BERS), d.getInt(K_BERS_CD),
                selected(p, true), selected(p, false), cooldowns(p)));
    }

    // ------------------------------------------------------------------ жизненный цикл

    /** Каждый тик на сервере. */
    public static void tick(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();

        // перезарядки (в берсерке идут вдвое быстрее)
        int dec = d.getInt(K_BERS) > 0 ? 2 : 1;
        int[] cds = cooldowns(p);
        boolean any = false;
        for (int i = 0; i < cds.length; i++) {
            if (cds[i] > 0) {
                cds[i] = Math.max(0, cds[i] - dec);
                any = true;
            }
        }
        if (any) {
            d.putIntArray(K_CDS, cds);
        }

        // длительные способности
        List<Task> list = TASKS.get(p.getUUID());
        if (list != null) {
            list.removeIf(t -> {
                try {
                    return t.tick(p);
                } catch (Throwable e) {
                    SculkTitanMod.LOGGER.error("Sculk Titan: ошибка способности", e);
                    return true;
                }
            });
            if (list.isEmpty()) {
                TASKS.remove(p.getUUID());
            }
        }

        tickBerserk(p, d);

        if (p.tickCount % 100 == 0) {
            syncSelf(p); // страховка от расхождения таймеров клиента
        }
    }

    public static void resetAll(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.remove(K_CDS);
        d.remove(K_BERS);
        d.remove(K_BERS_CD);
        removeBerserkMods(p);
        TASKS.remove(p.getUUID());
    }

    public static void onLogin(ServerPlayer p) {
        TASKS.remove(p.getUUID());
        if (berserk(p) && TitanData.isTitan(p)) {
            applyBerserkMods(p);
        } else {
            p.getPersistentData().remove(K_BERS);
            removeBerserkMods(p);
        }
    }

    public static void onLogout(ServerPlayer p) {
        TASKS.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ применение

    public static void cast(ServerPlayer p) {
        boolean titan = TitanData.isTitan(p);
        if (!titan && !TitanData.isUnlocked(p)) {
            p.displayClientMessage(Component.translatable("message.sculktitan.locked"), true);
            return;
        }
        if (TitanData.isBusy(p)) {
            return;
        }
        Ability a = Ability.of(titan, selected(p, titan));
        int[] cds = cooldowns(p);
        if (cds[a.ordinal()] > 0) {
            p.displayClientMessage(Component.translatable("message.sculktitan.ability_cd",
                    String.format(Locale.ROOT, "%.1f", cds[a.ordinal()] / 20.0)), true);
            return;
        }
        boolean ok;
        try {
            ok = run(p, a);
        } catch (Throwable t) {
            SculkTitanMod.LOGGER.error("Sculk Titan: ошибка запуска способности " + a, t);
            return;
        }
        if (!ok) {
            return;
        }
        cds[a.ordinal()] = a.cooldown;
        p.getPersistentData().putIntArray(K_CDS, cds);
        syncSelf(p);
    }

    private static boolean run(ServerPlayer p, Ability a) {
        switch (a) {
            case SHRIEK:
                addTask(p, new ShriekTask(p));
                return true;
            case SPIKES:
                addTask(p, new SpikesTask(p));
                return true;
            case DRAIN:
                addTask(p, new DrainTask(p));
                return true;
            case SENSE:
                return sense(p);
            case DASH:
                return dash(p);
            case GRIP:
                return grip(p);
            default:
                return false;
        }
    }

    private static void addTask(ServerPlayer p, Task t) {
        TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(t);
    }

    // ------------------------------------------------------------------ вспомогательное

    private static boolean isTarget(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || e instanceof Warden) {
            return false;
        }
        return !(e instanceof TamableAnimal t && t.isOwnedBy(p));
    }

    private static void sound(ServerPlayer p, SoundEvent ev, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ev, SoundSource.PLAYERS, volume, pitch);
    }

    private static void knock(LivingEntity e, double fromX, double fromZ, double horizontal, double up) {
        double dx = e.getX() - fromX;
        double dz = e.getZ() - fromZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        double resist = 1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        e.setDeltaMovement(e.getDeltaMovement().add(dx / len * horizontal * resist, up * (0.4 + 0.6 * resist),
                dz / len * horizontal * resist));
        e.hurtMarked = true;
    }

    private static double groundY(ServerLevel level, double x, double refY, double z) {
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(refY) + 1, Mth.floor(z));
        for (int i = 0; i < 8; i++) {
            BlockState s = level.getBlockState(bp);
            if (!s.getCollisionShape(level, bp).isEmpty()) {
                return bp.getY() + s.getCollisionShape(level, bp).max(Direction.Axis.Y);
            }
            bp.move(Direction.DOWN);
        }
        return refY;
    }

    /** Мгновенный взрыв вокруг игрока (используется при входе в берсерк). */
    private static void blast(ServerPlayer p, double radius, float damage, double knockback) {
        ServerLevel level = p.serverLevel();
        AABB box = p.getBoundingBox().inflate(radius, radius * 0.6, radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> isTarget(p, x))) {
            double dx = e.getX() - p.getX();
            double dz = e.getZ() - p.getZ();
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            e.hurt(p.damageSources().sonicBoom(p), damage);
            knock(e, p.getX(), p.getZ(), knockback, 0.5);
        }
    }

    // ================================================================== ТИТАН

    /** Резонанс бездны: расходящаяся ударная волна радиусом 16 блоков. */
    private static final class ShriekTask implements Task {
        private static final int STEPS = 14;
        private static final double RADIUS = 16.0;

        private int t;
        private final double ox;
        private final double oy;
        private final double oz;
        private final float damage;
        private final Set<Integer> hit = new HashSet<>();

        ShriekTask(ServerPlayer p) {
            this.ox = p.getX();
            this.oy = p.getY();
            this.oz = p.getZ();
            this.damage = 14.0F * power(p);
            ServerLevel level = p.serverLevel();
            level.sendParticles(ParticleTypes.SONIC_BOOM, ox, oy + p.getBbHeight() * 0.6, oz, 1, 0, 0, 0, 0);
            sound(p, SoundEvents.WARDEN_ROAR, 2.5F, 0.7F);
            sound(p, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.8F);
        }

        @Override
        public boolean tick(ServerPlayer p) {
            ServerLevel level = p.serverLevel();
            double r = RADIUS * (t + 1) / STEPS;

            int n = (int) Math.min(72, Math.max(24, r * 5));
            for (int k = 0; k < n; k++) {
                double a = k * (Math.PI * 2.0 / n) + t * 0.2;
                double x = ox + Math.cos(a) * r;
                double z = oz + Math.sin(a) * r;
                level.sendParticles(ParticleTypes.SCULK_SOUL, x, oy + 0.2, z, 1, 0.1, 0.05, 0.1, 0.02);
                if (k % 2 == 0) {
                    level.sendParticles(TEAL, x, oy + 0.5 + (k % 3) * 0.6, z, 1, 0, 0, 0, 0);
                }
                if (k % 3 == 0) {
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, oy + 0.15, z, 1, 0.05, 0.02, 0.05, 0.02);
                }
            }
            if (t % 3 == 0) {
                level.playSound(null, ox, oy, oz, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS,
                        1.2F, 0.5F + t * 0.03F);
            }

            AABB box = new AABB(ox - r - 1.5, oy - 2, oz - r - 1.5, ox + r + 1.5, oy + 6, oz + r + 1.5);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> isTarget(p, x))) {
                if (hit.contains(e.getId())) {
                    continue;
                }
                double dx = e.getX() - ox;
                double dz = e.getZ() - oz;
                if (Math.sqrt(dx * dx + dz * dz) > r + 1.0) {
                    continue;
                }
                hit.add(e.getId());
                e.hurt(p.damageSources().sonicBoom(p), damage);
                knock(e, ox, oz, 1.8, 0.6);
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            }
            t++;
            return t >= STEPS;
        }
    }

    /** Скалковые шипы: веер из трёх линий щупалец, бегущих вперёд на 18 блоков. */
    private static final class SpikesTask implements Task {
        private static final int STEPS = 9;
        private static final double STEP = 2.0;

        private int t;
        private final double ox;
        private final double oy;
        private final double oz;
        private final Vec3[] dirs = new Vec3[3];
        private final float damage;
        private final Set<Integer> hit = new HashSet<>();

        SpikesTask(ServerPlayer p) {
            this.ox = p.getX();
            this.oy = p.getY();
            this.oz = p.getZ();
            this.damage = 12.0F * power(p);
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = Vec3.directionFromRotation(0, p.getYRot());
            }
            flat = flat.normalize();
            double[] angles = {-0.38, 0.0, 0.38};
            for (int i = 0; i < 3; i++) {
                double c = Math.cos(angles[i]);
                double s = Math.sin(angles[i]);
                dirs[i] = new Vec3(flat.x * c - flat.z * s, 0, flat.x * s + flat.z * c);
            }
            sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 2.0F, 0.5F);
            sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.6F);
        }

        @Override
        public boolean tick(ServerPlayer p) {
            ServerLevel level = p.serverLevel();
            RandomSource rnd = p.getRandom();
            double d = 2.0 + STEP * t;
            for (Vec3 dir : dirs) {
                double x = ox + dir.x * d;
                double z = oz + dir.z * d;
                double y = groundY(level, x, oy, z);

                // сам шип: сужающийся к вершине столб частиц
                for (double h = 0; h <= 3.0; h += 0.5) {
                    double spread = 0.4 * (1.0 - h / 3.4);
                    double px = x + (rnd.nextDouble() - 0.5) * spread * 2;
                    double pz = z + (rnd.nextDouble() - 0.5) * spread * 2;
                    level.sendParticles(DARK, px, y + h, pz, 1, 0, 0, 0, 0);
                }
                level.sendParticles(TEAL, x, y + 3.0, z, 2, 0.08, 0.1, 0.08, 0);
                level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 2.6, z, 1, 0.1, 0.1, 0.1, 0.05);
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y + 0.1, z, 3, 0.5, 0.05, 0.5, 0.03);

                AABB box = new AABB(x - 1.7, y - 1.0, z - 1.7, x + 1.7, y + 3.2, z + 1.7);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, v -> isTarget(p, v))) {
                    if (!hit.add(e.getId())) {
                        continue;
                    }
                    e.hurt(p.damageSources().playerAttack(p), damage);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.95, 0));
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 5)); // «корни»
                }
            }
            level.playSound(null, ox + dirs[1].x * d, oy, oz + dirs[1].z * d, SoundEvents.SCULK_BLOCK_SPREAD,
                    SoundSource.PLAYERS, 1.4F, 0.6F + t * 0.05F);
            t++;
            return t >= STEPS;
        }
    }

    /** Пожирание душ: 5 импульсов высасывания жизни вокруг титана с исцелением. */
    private static final class DrainTask implements Task {
        private static final int PULSES = 5;
        private static final int INTERVAL = 12;
        private static final double RANGE = 14.0;

        private int t;
        private final float damage;

        DrainTask(ServerPlayer p) {
            this.damage = 3.5F * power(p);
            sound(p, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
            sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 1.8F, 0.4F);
        }

        @Override
        public boolean tick(ServerPlayer p) {
            ServerLevel level = p.serverLevel();
            Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);

            // души стекаются к титану
            for (int i = 0; i < 4; i++) {
                double a = t * 0.5 + i * (Math.PI / 2.0);
                double rr = 4.0;
                double sx = c.x + Math.cos(a) * rr;
                double sz = c.z + Math.sin(a) * rr;
                level.sendParticles(ParticleTypes.SCULK_SOUL, sx, c.y + (i - 1.5) * 0.8, sz, 0,
                        (c.x - sx) * 0.12, 0.0, (c.z - sz) * 0.12, 1.0);
            }

            if (t % INTERVAL == 0) {
                float healed = 0.0F;
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE),
                        v -> isTarget(p, v))) {
                    if (e.distanceTo(p) > RANGE) {
                        continue;
                    }
                    float before = e.getHealth();
                    e.hurt(p.damageSources().indirectMagic(p, p), damage);
                    healed += Math.max(0.0F, before - e.getHealth()) * 0.6F;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));

                    Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0);
                    Vec3 v = c.subtract(from).normalize().scale(0.5);
                    for (int i = 0; i < 4; i++) {
                        level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y + i * 0.25, from.z, 0,
                                v.x, v.y, v.z, 1.0);
                    }
                }
                if (healed > 0.0F) {
                    p.heal(Math.min(healed, 8.0F));
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, c.x, c.y, c.z, 12, 0.8, 1.2, 0.8, 0.03);
                }
                sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 1.4F, 0.8F + (t / INTERVAL) * 0.1F);
            }
            t++;
            return t >= PULSES * INTERVAL;
        }
    }

    // ================================================================== ЧЕЛОВЕК (слабые)

    /** Вибро-чутьё: подсвечивает всех существ в радиусе 20 блоков на 8 секунд. */
    private static boolean sense(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20.0),
                x -> x != p && x.isAlive() && !(x instanceof ArmorStand))) {
            if (e.distanceTo(p) <= 20.0) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0, false, false, false));
            }
        }
        for (int ring = 1; ring <= 3; ring++) {
            double r = ring * 3.0;
            for (int k = 0; k < 28; k++) {
                double a = k * (Math.PI * 2.0 / 28);
                level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX() + Math.cos(a) * r, p.getY() + 0.2,
                        p.getZ() + Math.sin(a) * r, 1, 0, 0.03, 0, 0.01);
            }
        }
        sound(p, SoundEvents.SCULK_CLICKING, 1.5F, 0.9F);
        sound(p, SoundEvents.SCULK_BLOCK_SPREAD, 1.0F, 1.2F);
        return true;
    }

    /** Скалковый рывок: короткий рывок вперёд. */
    private static boolean dash(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = Vec3.directionFromRotation(0, p.getYRot());
        }
        flat = flat.normalize();
        p.setDeltaMovement(flat.x * 1.5, 0.3, flat.z * 1.5);
        p.hurtMarked = true;
        p.fallDistance = 0.0F;
        if (p.connection != null) {
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 12; i++) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX() - flat.x * i * 0.3, p.getY() + 0.3 + (i % 3) * 0.4,
                    p.getZ() - flat.z * i * 0.3, 1, 0.1, 0.1, 0.1, 0.02);
        }
        level.sendParticles(TEAL, p.getX(), p.getY() + 0.5, p.getZ(), 10, 0.3, 0.4, 0.3, 0);
        sound(p, SoundEvents.SCULK_BLOCK_SPREAD, 1.2F, 1.4F);
        return true;
    }

    /** Хватка скалка: замедляет и ослабляет существо, на которое смотрит игрок. */
    private static boolean grip(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestDot = 0.96;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10.0),
                x -> isTarget(p, x))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > 10.0 || dist < 0.1) {
                continue;
            }
            double dot = to.normalize().dot(look);
            if (dot > bestDot && p.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        if (best == null) {
            p.displayClientMessage(Component.translatable("message.sculktitan.no_target"), true);
            return false;
        }
        best.hurt(p.damageSources().indirectMagic(p, p), 3.0F);
        best.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        best.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));

        Vec3 target = best.getBoundingBox().getCenter();
        Vec3 delta = target.subtract(eye);
        int n = (int) (delta.length() * 3);
        for (int i = 0; i <= n; i++) {
            Vec3 pt = eye.add(delta.scale(i / (double) Math.max(1, n))).add(0, -0.3, 0);
            level.sendParticles(i % 2 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SCULK_CHARGE_POP,
                    pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.01);
        }
        level.sendParticles(DARK, target.x, best.getY() + 0.2, target.z, 14, 0.4, 0.2, 0.4, 0);
        sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7F, 1.6F);
        return true;
    }

    // ================================================================== БЕРСЕРК

    private static void tickBerserk(ServerPlayer p, CompoundTag d) {
        int bers = d.getInt(K_BERS);
        if (bers > 0) {
            if (!TitanData.isTitan(p)) {
                endBerserk(p, false);
                TitanData.sync(p);
                return;
            }
            bers--;
            d.putInt(K_BERS, bers);
            berserkFx(p, bers);
            if (p.tickCount % 20 == 0) {
                refreshBerserkEffects(p);
            }
            if (bers == 0) {
                endBerserk(p, true);
                TitanData.sync(p);
            }
            return;
        }
        int cd = d.getInt(K_BERS_CD);
        if (cd > 0) {
            d.putInt(K_BERS_CD, cd - 1);
            return;
        }
        if (TitanData.isTitan(p) && !TitanData.isBusy(p) && p.isAlive()
                && p.getHealth() <= p.getMaxHealth() * BERSERK_HP) {
            startBerserk(p);
        }
    }

    private static void startBerserk(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.putInt(K_BERS, BERSERK_TICKS);
        applyBerserkMods(p);
        refreshBerserkEffects(p);
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BERSERK_TICKS, 1, false, false, true));

        if (p.connection != null) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("message.sculktitan.berserk.subtitle").withStyle(ChatFormatting.AQUA)));
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("message.sculktitan.berserk.title")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }

        ServerLevel level = p.serverLevel();
        double cy = p.getY() + p.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), cy, p.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(RED, p.getX(), cy, p.getZ(), 120, 1.4, p.getBbHeight() * 0.45, 1.4, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), cy, p.getZ(), 120, 1.2, p.getBbHeight() * 0.45, 1.2, 0.15);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), cy, p.getZ(), 80, 1.6, p.getBbHeight() * 0.45, 1.6, 0.08);
        sound(p, SoundEvents.WARDEN_ROAR, 3.0F, 0.5F);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 2.5F, 0.6F);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.5F);
        blast(p, 9.0, 10.0F, 1.5);
        TitanData.sync(p);
    }

    /** Завершение берсерка. natural = время вышло (тогда титан "перегорает"). Синхронизацию делает вызывающий. */
    public static void endBerserk(ServerPlayer p, boolean natural) {
        CompoundTag d = p.getPersistentData();
        boolean was = d.getInt(K_BERS) > 0;
        d.remove(K_BERS);
        removeBerserkMods(p);
        if (!was) {
            return;
        }
        d.putInt(K_BERS_CD, natural ? BERSERK_COOLDOWN : 20 * 30);
        if (natural) {
            p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            p.removeEffect(MobEffects.REGENERATION);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            p.displayClientMessage(Component.translatable("message.sculktitan.berserk.end"), true);
            sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 2.0F, 0.5F);
        }
    }

    private static void refreshBerserkEffects(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false, true));
    }

    private static void berserkFx(ServerPlayer p, int left) {
        if (left % 2 != 0) {
            return;
        }
        ServerLevel level = p.serverLevel();
        RandomSource rnd = p.getRandom();
        double h = p.getBbHeight();
        for (int i = 0; i < 3; i++) {
            level.sendParticles(RED, p.getX() + (rnd.nextDouble() - 0.5) * 2.4, p.getY() + rnd.nextDouble() * h,
                    p.getZ() + (rnd.nextDouble() - 0.5) * 2.4, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX() + (rnd.nextDouble() - 0.5) * 1.6, p.getY() + rnd.nextDouble() * h * 0.3,
                p.getZ() + (rnd.nextDouble() - 0.5) * 1.6, 1, 0.0, 0.1, 0.0, 0.06);
        if (rnd.nextInt(6) == 0) {
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX() + (rnd.nextDouble() - 0.5) * 2.0, p.getY() + rnd.nextDouble() * h,
                    p.getZ() + (rnd.nextDouble() - 0.5) * 2.0, 1, 0.05, 0.05, 0.05, 0.02);
        }
        if (left % 40 == 0) {
            sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.8F, 0.8F);
        }
    }

    private static void applyBerserkMods(ServerPlayer p) {
        TitanData.add(p, Attributes.ATTACK_DAMAGE, ID_B_DAMAGE, "Sculk berserk damage", 14.0, AttributeModifier.Operation.ADDITION);
        TitanData.add(p, Attributes.MOVEMENT_SPEED, ID_B_SPEED, "Sculk berserk speed", 0.35, AttributeModifier.Operation.MULTIPLY_BASE);
        TitanData.add(p, Attributes.ATTACK_SPEED, ID_B_ATKSPD, "Sculk berserk attack speed", 0.5, AttributeModifier.Operation.MULTIPLY_BASE);
        TitanData.add(p, Attributes.ARMOR, ID_B_ARMOR, "Sculk berserk armor", 6.0, AttributeModifier.Operation.ADDITION);
    }

    private static void removeBerserkMods(Player p) {
        TitanData.remove(p, Attributes.ATTACK_DAMAGE, ID_B_DAMAGE);
        TitanData.remove(p, Attributes.MOVEMENT_SPEED, ID_B_SPEED);
        TitanData.remove(p, Attributes.ATTACK_SPEED, ID_B_ATKSPD);
        TitanData.remove(p, Attributes.ARMOR, ID_B_ARMOR);
    }

    /** Вампиризм берсерка: каждый удар титана в ярости лечит его. */
    public static void onMeleeHit(ServerPlayer attacker, float amount) {
        if (berserk(attacker)) {
            attacker.heal(Math.min(4.0F, amount * 0.2F));
        }
    }

    private Abilities() {
    }
}
