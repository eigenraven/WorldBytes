package me.eigenraven.mc.worldbytes;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldBytesMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("worldbytes");

    @Override
    public void onInitialize() {
        if (DensityFunctionCompiler.compile(DensityFunctions.constant(0.5)).compute(null) != 0.5) {
            throw new RuntimeException("Invalid value");
        }

        LOGGER.info("Hello Fabric world!");
    }
}
