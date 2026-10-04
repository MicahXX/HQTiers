package me.micahcode.hqtiers;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Initializes the mod and exposes shared client metadata. */
public class Hqtiers implements ModInitializer {
    public static final String MOD_ID = "assets";

    /** Identifies the mod consistently in outgoing API requests. */
    public static final String USER_AGENT = "HQTiers/4.0 (micahcode)";

    public static final Logger logger = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        logger.info("HQTiers has started.");
    }
}
