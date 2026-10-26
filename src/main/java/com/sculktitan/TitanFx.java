package com.sculktitan;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** Партиклы и звуки: превращение в титана, обратно в человека и остатки скалка. */
public final class TitanFx {
    private static final DustParticleOptions TEAL = new DustParticleOptions(new Vector3f(0.16F, 0.88F, 0.92F), 1.4F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.04F, 0.14F, 0.19F), 1.3F);

    /** Высота титана. */
    private static final double H = 1.8 * TitanData.SCALE;

    /** Один тик анимации превращения. t = 0 .. ANIM_TICKS-1 */
    public static void animation(ServerPlayer p, int mode, int t) {
        ServerLevel level = p.serverLevel();
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        RandomSource rnd = p.getRandom();
        boolean grow = mode == TitanData.MODE_GROW;
        float prog = (t + 1) / (float) TitanData.ANIM_TICKS;

        // Три спирали из скалковых душ поднимаются (или опускаются) вокруг игрока
        double h = grow ? prog * H : (1.0F - prog) * H;
        double r = grow ? 0.9 + prog * 0.9 : 1.9 - prog * 1.1;
        for (int i = 0; i < 3; i++) {
            double a = t * 0.55 + i * (Math.PI * 2.0 / 3.0);
            double px = x + Math.cos(a) * r;
            double pz = z + Math.sin(a) * r;
            level.sendParticles(ParticleTypes.SCULK_SOUL, px, y + h, pz, 1, 0.0, 0.03, 0.0, 0.02);
            level.sendParticles(TEAL, px, y + h, pz, 2, 0.06, 0.06, 0.06, 0.0);
            level.sendParticles(DARK, px, y + h * 0.5, pz, 1, 0.1, 0.1, 0.1, 0.0);
        }

        // Расходящееся (или сходящееся) кольцо скалка по земле
        if (t % 2 == 0) {
            double rr = grow ? prog * 4.5 : (1.0F - prog) * 4.5;
            int n = 16;
            for (int k = 0; k < n; k++) {
                double a = k * (Math.PI * 2.0 / n) + t * 0.12;
                double px = x + Math.cos(a) * rr;
                double pz = z + Math.sin(a) * rr;
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, y + 0.1, pz, 1, 0.05, 0.02, 0.05, 0.02);
                if (k % 4 == 0) {
                    level.sendParticles(ParticleTypes.SCULK_SOUL, px, y + 0.15, pz, 1, 0.0, 0.05, 0.0, 0.02);
                }
            }
        }

        // Столб скалка у самых ног
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.2, z, 2, 0.45, 0.15, 0.45, 0.04);

        // Звуки: пульсация скалка с растущей высотой тона
        if (t > 0 && t % 12 == 0) {
            sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 1.4F, 0.6F + prog * 0.8F);
        }
        if (t == 36) {
            sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.2F, 0.6F);
        }
    }

    /** Взрыв скалка в момент, когда игрок стал титаном. */
    public static void growBurst(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        level.sendParticles(ParticleTypes.SONIC_BOOM, x, y + H * 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + H * 0.5, z, 140, 1.0, H * 0.45, 1.0, 0.14);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y + H * 0.5, z, 90, 1.2, H * 0.45, 1.2, 0.08);
        level.sendParticles(TEAL, x, y + H * 0.5, z, 90, 1.2, H * 0.45, 1.2, 0.0);
        level.sendParticles(DARK, x, y + 1.0, z, 60, 1.5, 0.8, 1.5, 0.0);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.8F);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5F, 0.6F);
    }

    /** Взрыв скалка в момент возврата в человеческий облик. */
    public static void shrinkBurst(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + H * 0.5, z, 120, 1.0, H * 0.45, 1.0, 0.12);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y + H * 0.5, z, 80, 1.2, H * 0.45, 1.2, 0.08);
        level.sendParticles(TEAL, x, y + H * 0.5, z, 80, 1.2, H * 0.45, 1.2, 0.0);
        level.sendParticles(DARK, x, y + 1.0, z, 50, 1.3, 0.8, 1.3, 0.0);
        sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 2.0F, 0.5F);
        sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 1.2F);
    }

    /** Остатки скалка на теле человека (вызывается каждый тик). */
    public static void remnants(ServerPlayer p, int remaining) {
        if (p.tickCount % 2 != 0) {
            return;
        }
        ServerLevel level = p.serverLevel();
        RandomSource rnd = p.getRandom();
        int n = remaining > 150 ? 3 : remaining > 70 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            double px = p.getX() + (rnd.nextDouble() - 0.5) * 0.7;
            double py = p.getY() + rnd.nextDouble() * 1.8;
            double pz = p.getZ() + (rnd.nextDouble() - 0.5) * 0.7;
            if (rnd.nextBoolean()) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, px, py, pz, 1, 0.0, 0.03, 0.0, 0.01);
            } else {
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, py, pz, 1, 0.0, 0.02, 0.0, 0.01);
            }
            if (rnd.nextInt(3) == 0) {
                level.sendParticles(DARK, px, py, pz, 1, 0.1, 0.0, 0.1, 0.0);
            }
        }
        if (p.tickCount % 40 == 0) {
            sound(p, SoundEvents.SCULK_BLOCK_SPREAD, 0.5F, 1.0F);
        }
    }

    /** Остатки скалка исчезли. */
    public static void remnantsGone(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double x = p.getX();
        double y = p.getY() + 0.9;
        double z = p.getZ();
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, 30, 0.35, 0.8, 0.35, 0.06);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 25, 0.35, 0.8, 0.35, 0.05);
        sound(p, SoundEvents.SCULK_BLOCK_BREAK, 0.8F, 1.0F);
    }

    private static void sound(ServerPlayer p, SoundEvent ev, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ev, SoundSource.PLAYERS, volume, pitch);
    }

    private TitanFx() {
    }
}
