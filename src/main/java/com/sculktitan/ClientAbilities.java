package com.sculktitan;

/**
 * Личное состояние способностей локального игрока (приходит пакетом SelfStatePacket).
 * Не использует клиентские классы Minecraft, поэтому безопасен для загрузки на сервере.
 */
public final class ClientAbilities {
    private static boolean unlocked;
    private static int berserkLeft;
    private static int berserkCd;
    private static final int[] SEL = new int[2];
    private static final int[] CDS = new int[Ability.COUNT];

    public static void set(boolean unlockedNow, int bersLeft, int bersCd, int selTitan, int selHuman, int[] cds) {
        unlocked = unlockedNow;
        berserkLeft = bersLeft;
        berserkCd = bersCd;
        SEL[0] = selTitan;
        SEL[1] = selHuman;
        for (int i = 0; i < CDS.length; i++) {
            CDS[i] = i < cds.length ? cds[i] : 0;
        }
    }

    /** Раз в тик клиента (не на паузе): перезарядки идут вдвое быстрее в берсерке - как и на сервере. */
    public static void tick() {
        int dec = berserkLeft > 0 ? 2 : 1;
        for (int i = 0; i < CDS.length; i++) {
            if (CDS[i] > 0) {
                CDS[i] = Math.max(0, CDS[i] - dec);
            }
        }
        if (berserkLeft > 0) {
            berserkLeft--;
        } else if (berserkCd > 0) {
            berserkCd--;
        }
    }

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static int berserkLeft() {
        return berserkLeft;
    }

    public static int selected(boolean titan) {
        return Math.max(0, Math.min(Ability.PER_FORM - 1, SEL[titan ? 0 : 1]));
    }

    public static void select(boolean titan, int index) {
        SEL[titan ? 0 : 1] = index;
    }

    public static int cooldown(Ability a) {
        return CDS[a.ordinal()];
    }

    public static void clear() {
        unlocked = false;
        berserkLeft = 0;
        berserkCd = 0;
        SEL[0] = 0;
        SEL[1] = 0;
        java.util.Arrays.fill(CDS, 0);
    }

    private ClientAbilities() {
    }
}
