package com.moysecamm.minecleaner;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MineCleanerMod implements ModInitializer {
    public static final String MOD_ID = "minecleaner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("MineCleaner loaded successfully");
    }
}
