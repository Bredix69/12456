package com.sculktitan.client;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import com.sculktitan.Ability;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Иконки способностей: пиксельные «скалковые молнии» (как на референсе, но бирюзовые).
 * Рисуются кодом на сетке 32x32 - отдельные текстуры не нужны. Сетки кэшируются.
 * Значения клетки: 0 пусто, 1 тёмный, 2 средний, 3 яркий, 4 белое ядро.
 */
public final class AbilityIcons {
    private static final int N = 32;
    private static final Map<Ability, byte[][]> CACHE = new EnumMap<>(Ability.class);

    private static final int[] COLORS = {0, 0xFF0E6B6E, 0xFF1FC8BE, 0xFF5CFFF0, 0xFFEFFFFF};

    public static void draw(GuiGraphics g, Ability a, int cx, int cy, int px, long time, boolean bright) {
        byte[][] grid = CACHE.computeIfAbsent(a, AbilityIcons::build);
        int x0 = cx - (N / 2) * px;
        int y0 = cy - (N / 2) * px;
        int t = (int) (time / 3);

        // мягкое свечение под яркими пикселями
        if (bright) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    if (grid[y][x] >= 3) {
                        g.fill(x0 + x * px - 1, y0 + y * px - 1, x0 + (x + 1) * px + 1, y0 + (y + 1) * px + 1, 0x2200FFE6);
                    }
                }
            }
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int v = grid[y][x];
                if (v == 0) {
                    continue;
                }
                // мерцание слабых пикселей
                if (v <= 2 && Math.floorMod(x * 7 + y * 13 + t, 9) == 0) {
                    continue;
                }
                int color = COLORS[v];
                if (!bright) {
                    color = dim(color);
                }
                g.fill(x0 + x * px, y0 + y * px, x0 + (x + 1) * px, y0 + (y + 1) * px, color);
            }
        }
    }

    private static int dim(int argb) {
        int r = ((argb >> 16) & 0xFF) * 3 / 5;
        int gr = ((argb >> 8) & 0xFF) * 3 / 5;
        int b = (argb & 0xFF) * 3 / 5;
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    // ------------------------------------------------------------------ генерация

    private static byte[][] build(Ability a) {
        byte[][] g = new byte[N][N];
        Random r = new Random(4242L + a.ordinal() * 977L);
        switch (a) {
            case SHRIEK -> {             // ударная волна: кольца + лучи-молнии во все стороны
                ring(g, 16, 16, 7, 2);
                ring(g, 16, 16, 12, 1);
                for (int i = 0; i < 10; i++) {
                    bolt(g, r, 16, 16, i * Math.PI * 2 / 10 + (r.nextDouble() - 0.5) * 0.3, 15, 3, 0, 0.8);
                }
                center(g, 16, 16, 1, 4);
            }
            case SPIKES -> {             // линия земли и веер щупалец вверх
                for (int x = 2; x < 30; x++) {
                    set(g, x, 29, 1);
                    if (x % 3 != 0) {
                        set(g, x, 28, 2);
                    }
                }
                double[] angles = {-0.95, -0.5, 0.0, 0.5, 0.95};
                for (int i = 0; i < angles.length; i++) {
                    int lvl = i == 2 ? 3 : 2;
                    bolt(g, r, 16, 28, -Math.PI / 2 + angles[i], i == 2 ? 24 : 19, lvl, 0, 0.55);
                }
                bolt(g, r, 8, 28, -Math.PI / 2 - 0.2, 10, 1, 1, 0.5);
                bolt(g, r, 24, 28, -Math.PI / 2 + 0.2, 10, 1, 1, 0.5);
                center(g, 16, 28, 1, 3);
            }
            case DRAIN -> {              // молнии стекаются к тёмному ядру, вокруг - души
                for (int i = 0; i < 9; i++) {
                    double ang = i * Math.PI * 2 / 9 + 0.2;
                    bolt(g, r, 16 + Math.cos(ang) * 15, 16 + Math.sin(ang) * 15, ang + Math.PI, 13, 2, 1, 0.5);
                }
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI * 2 / 8;
                    set(g, (int) Math.round(16 + Math.cos(ang) * 9), (int) Math.round(16 + Math.sin(ang) * 9), 3);
                }
                center(g, 16, 16, 2, 1);
                center(g, 16, 16, 1, 3);
                set(g, 16, 16, 4);
            }
            case SENSE -> {              // вибро-чутьё: расходящиеся дуги
                ring(g, 16, 16, 4, 2);
                ring(g, 16, 16, 8, 2);
                ring(g, 16, 16, 12, 1);
                center(g, 16, 16, 1, 4);
                for (int i = 0; i < 4; i++) {
                    bolt(g, r, 16, 16, i * Math.PI / 2 + Math.PI / 4, 8, 2, 1, 0.5);
                }
            }
            case DASH -> {               // три рваных штриха и наконечник стрелки
                for (int k = -1; k <= 1; k++) {
                    bolt(g, r, 4, 16 + k * 6, 0.0, 20, k == 0 ? 3 : 2, 1, 0.35);
                }
                for (int i = 0; i < 7; i++) {
                    set(g, 28 - i, 16 - i, 3);
                    set(g, 28 - i, 16 + i, 3);
                }
                set(g, 29, 16, 4);
            }
            case GRIP -> {               // три когтя сходятся к точке захвата
                int[][] starts = {{6, 2}, {16, 0}, {26, 2}};
                for (int[] s : starts) {
                    double ang = Math.atan2(24 - s[1], 16 - s[0]);
                    bolt(g, r, s[0], s[1], ang, 25, 2, 1, 0.5);
                }
                center(g, 16, 25, 1, 3);
                set(g, 16, 25, 4);
            }
            default -> {
            }
        }
        return g;
    }

    private static void set(byte[][] g, int x, int y, int v) {
        if (x >= 0 && y >= 0 && x < N && y < N && g[y][x] < v) {
            g[y][x] = (byte) v;
        }
    }

    private static void center(byte[][] g, int cx, int cy, int radius, int v) {
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                set(g, cx + x, cy + y, v);
            }
        }
    }

    private static void ring(byte[][] g, int cx, int cy, int rad, int v) {
        for (double ang = 0; ang < Math.PI * 2; ang += 0.5 / rad) {
            if (Math.sin(ang * 4 + rad) > 0.55) {
                continue; // разрывы в кольце
            }
            set(g, (int) Math.round(cx + Math.cos(ang) * rad), (int) Math.round(cy + Math.sin(ang) * rad), v);
        }
    }

    /** Рваная линия с ответвлениями (jitter - насколько сильно она виляет). */
    private static void bolt(byte[][] g, Random r, double x, double y, double ang, double len, int lvl, int depth,
                             double jitter) {
        for (double d = 0; d < len; d += 0.8) {
            ang += (r.nextDouble() - 0.5) * jitter * 2.0;
            x += Math.cos(ang) * 0.8;
            y += Math.sin(ang) * 0.8;
            set(g, (int) Math.round(x), (int) Math.round(y), lvl);
            if (depth < 2 && r.nextDouble() < 0.10) {
                double side = (r.nextBoolean() ? 1 : -1) * (0.6 + r.nextDouble() * 0.5);
                bolt(g, r, x, y, ang + side, (len - d) * 0.55, Mth.clamp(lvl - 1, 1, 4), depth + 1, jitter);
            }
        }
    }

    private AbilityIcons() {
    }
}
