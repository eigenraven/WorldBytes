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

    private static final DensityFunction.FunctionContext dummyContext = new DensityFunction.FunctionContext() {
        @Override
        public int blockX() {
            return 13;
        }

        @Override
        public int blockY() {
            return 10;
        }

        @Override
        public int blockZ() {
            return 17;
        }
    };

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
        assertEquals(vanilla.compute(dummyContext), compiled.compute(dummyContext));
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
    public void testMinOfConstants(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.min(DensityFunctions.constant(a), DensityFunctions.constant(b)));
    }

    @Property
    public void testMaxOfConstants(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.max(DensityFunctions.constant(a), DensityFunctions.constant(b)));
    }

    @Property
    public void testClampOfConstants(@ForAll double a, @ForAll double min, @ForAll double max) {
        testCompiledEquivalency(DensityFunctions.constant(a).clamp(min, max));
    }

    @Property
    public void testBeardifierMarker() {
        testCompiledEquivalency(DensityFunctions.BeardifierMarker.INSTANCE);
    }

    @Property
    public void testBlendAlpha() {
        testCompiledEquivalency(DensityFunctions.blendAlpha());
    }

    @Property
    public void testBlendOffset() {
        testCompiledEquivalency(DensityFunctions.blendOffset());
    }

    @Property
    public void testBeardifierMarker2() {
        testCompiledEquivalency(DensityFunctions.add(
                DensityFunctions.BeardifierMarker.INSTANCE, DensityFunctions.BeardifierMarker.INSTANCE));
    }

    @Property
    public void testBlendAlpha2() {
        testCompiledEquivalency(DensityFunctions.add(DensityFunctions.blendAlpha(), DensityFunctions.blendAlpha()));
    }

    @Property
    public void testBlendOffset2() {
        testCompiledEquivalency(DensityFunctions.add(DensityFunctions.blendOffset(), DensityFunctions.blendOffset()));
    }

    @Property
    public void testBlendDensityOfConstant(@ForAll double v) {
        testCompiledEquivalency(DensityFunctions.blendDensity(DensityFunctions.constant(v)));
    }

    @Property
    public void testBlendDensityOfConstant2(@ForAll double v) {
        testCompiledEquivalency(DensityFunctions.add(
                DensityFunctions.blendDensity(DensityFunctions.constant(v)),
                DensityFunctions.blendDensity(DensityFunctions.constant(v))));
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

    @Property
    public void testSumsOfConstantsMin(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.min(
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(a)),
                DensityFunctions.add(DensityFunctions.constant(b), DensityFunctions.constant(b))));
    }

    @Property
    public void testSumsOfConstantsMax(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.max(
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(a)),
                DensityFunctions.add(DensityFunctions.constant(b), DensityFunctions.constant(b))));
    }

    @Property
    public void testSumsOfConstantsClamp(@ForAll double a, @ForAll double min, @ForAll double max) {
        testCompiledEquivalency(DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(a))
                .clamp(min, max));
    }
}
