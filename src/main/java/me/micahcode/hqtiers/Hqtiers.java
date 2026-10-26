package me.micahcode.hqtiers;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hqtiers implements ModInitializer {
    public static final String MOD_ID = "assets";

    public static final String USER_AGENT = "HQTiers/4.0 (micahcode)";

    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        logger.info("HQTiers has started.");
    }
}
