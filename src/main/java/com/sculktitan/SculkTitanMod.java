package com.sculktitan;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(SculkTitanMod.MODID)
public class SculkTitanMod {
    public static final String MODID = "sculktitan";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public SculkTitanMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        ModNetwork.register();
    }
}
