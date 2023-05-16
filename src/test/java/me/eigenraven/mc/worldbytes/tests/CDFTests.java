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

    private void testCompiledEquivalency(final DensityFunction vanilla) {
        final DensityFunction compiled = DensityFunctionCompiler.compile(vanilla);
        assertEquals(vanilla.compute(null), compiled.compute(null));
    }

    @Property
    public void testConstant(@ForAll double value) {
        testCompiledEquivalency(DensityFunctions.constant(value));
    }

    @Property
    public void testSumOfConstants(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b)));
    }

    @Property
    public void testMulOfConstants(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.mul(DensityFunctions.constant(a), DensityFunctions.constant(b)));
    }

    @Property
    public void testSumOfConstantsDoubled(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.add(
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b)),
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b))));
    }

    @Property
    public void testMulOfConstantsDoubled(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.add(
                DensityFunctions.mul(DensityFunctions.constant(a), DensityFunctions.constant(b)),
                DensityFunctions.mul(DensityFunctions.constant(a), DensityFunctions.constant(b))));
    }

    @Property
    public void testSumOfConstantsMulled(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.mul(
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b)),
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b))));
    }
}
