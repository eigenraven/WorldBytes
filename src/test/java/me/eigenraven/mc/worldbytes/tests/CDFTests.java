package me.eigenraven.mc.worldbytes.tests;

import static org.junit.jupiter.api.Assertions.*;

import me.eigenraven.mc.worldbytes.DensityFunctionCompiler;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

public class CDFTests {

    static {
        synchronized (Bootstrap.class) {
            SharedConstants.tryDetectVersion();
            SharedConstants.enableDataFixerOptimizations();
            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
        }
    }

    @Property
    public void testConstant(@ForAll double value) {
        final DensityFunction vanilla = DensityFunctions.constant(value);
        final DensityFunction compiled = DensityFunctionCompiler.compile(vanilla);
        assertEquals(vanilla.compute(null), compiled.compute(null));
    }
}
