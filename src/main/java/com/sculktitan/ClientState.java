package com.sculktitan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние игроков, которое знает клиент (приходит пакетом с сервера).
 * Не использует клиентские классы Minecraft, поэтому безопасен для загрузки на сервере.
 */
public final class ClientState {
    private static final class Entry {
        boolean titan;
        boolean berserk;
        int remnants;
    }

    private static final Map<UUID, Entry> MAP = new ConcurrentHashMap<>();

    public static void set(UUID id, boolean titan, int remnants, boolean berserk) {
        Entry e = MAP.computeIfAbsent(id, k -> new Entry());
        e.titan = titan;
        e.berserk = berserk;
        e.remnants = remnants;
    }

    public static boolean isTitan(UUID id) {
        Entry e = MAP.get(id);
        return e != null && e.titan;
    }

    public static boolean isBerserk(UUID id) {
        Entry e = MAP.get(id);
        return e != null && e.titan && e.berserk;
    }

    public static int remnants(UUID id) {
        Entry e = MAP.get(id);
        return e == null ? 0 : e.remnants;
    }

    /** Вызывается раз в тик клиента для каждого игрока. */
    public static void tick(UUID id) {
        Entry e = MAP.get(id);
        if (e != null && e.remnants > 0) {
            e.remnants--;
        }
    }

    public static void clear() {
        MAP.clear();
    }

    private ClientState() {
    }
}
