package com.sculktitan;

/**
 * Все способности. Первые три - способности титана, вторые три - слабые способности человека.
 * Порядковый номер = индекс в массиве перезарядок.
 */
public enum Ability {
    // ---- титан
    SHRIEK(true, "shriek", 20 * 30),     // Резонанс бездны
    SPIKES(true, "spikes", 20 * 15),     // Скалковые шипы
    DRAIN(true, "drain", 20 * 35),       // Пожирание душ
    // ---- человек (слабые)
    SENSE(false, "sense", 20 * 30),      // Вибро-чутьё
    DASH(false, "dash", 20 * 8),         // Скалковый рывок
    GRIP(false, "grip", 20 * 14);        // Хватка скалка

    public static final int PER_FORM = 3;
    public static final int COUNT = values().length;

    public final boolean titan;
    public final String id;
    /** Перезарядка в тиках. */
    public final int cooldown;

    Ability(boolean titan, String id, int cooldown) {
        this.titan = titan;
        this.id = id;
        this.cooldown = cooldown;
    }

    public static Ability of(boolean titan, int index) {
        int i = Math.max(0, Math.min(PER_FORM - 1, index));
        return values()[(titan ? 0 : PER_FORM) + i];
    }

    public String nameKey() {
        return "ability.sculktitan." + id + ".name";
    }

    public String descKey() {
        return "ability.sculktitan." + id + ".desc";
    }
}
