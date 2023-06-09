package me.eigenraven.mc.worldbytes.tests;

import static org.junit.jupiter.api.Assertions.*;

import me.eigenraven.mc.worldbytes.DensityFunctionCompiler;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

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
    public void testSumOfConstantsHolder(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(new DensityFunctions.HolderHolder(
                Holder.direct(DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b)))));
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

    @Example
    public void testBeardifierMarker() {
        testCompiledEquivalency(DensityFunctions.BeardifierMarker.INSTANCE);
    }

    @Example
    public void testBlendAlpha() {
        testCompiledEquivalency(DensityFunctions.blendAlpha());
    }

    @Example
    public void testBlendOffset() {
        testCompiledEquivalency(DensityFunctions.blendOffset());
    }

    @Example
    public void testBeardifierMarker2() {
        testCompiledEquivalency(DensityFunctions.add(
                DensityFunctions.BeardifierMarker.INSTANCE, DensityFunctions.BeardifierMarker.INSTANCE));
    }

    @Example
    public void testBlendAlpha2() {
        testCompiledEquivalency(DensityFunctions.add(DensityFunctions.blendAlpha(), DensityFunctions.blendAlpha()));
    }

    @Example
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
    public void testEndIslands(@ForAll long v) {
        testCompiledEquivalency(DensityFunctions.endIslands(v));
    }

    @Property
    public void testConstantMapped(@ForAll double value, @ForAll DensityFunctions.Mapped.Type type) {
        testCompiledEquivalency(DensityFunctions.map(DensityFunctions.constant(value), type));
    }

    @Property
    public void testConstantMappedMarked(@ForAll double value, @ForAll DensityFunctions.Mapped.Type type) {
        final DensityFunction fn =
                DensityFunctions.interpolated(DensityFunctions.map(DensityFunctions.constant(value), type));
        testCompiledEquivalency(fn);
        final DensityFunction cfn = DensityFunctionCompiler.compile(fn);
        assertInstanceOf(DensityFunctions.Marker.class, cfn);
    }

    @Property
    public void testConstantMarkedMapped(@ForAll double value, @ForAll DensityFunctions.Mapped.Type type) {
        final DensityFunction fn =
                DensityFunctions.map(DensityFunctions.interpolated(DensityFunctions.constant(value)), type);
        testCompiledEquivalency(fn);
        final DensityFunction cfn = DensityFunctionCompiler.compile(fn);
    }

    @Property
    public void testBlendDensityOfEndIslands(@ForAll long v) {
        testCompiledEquivalency(DensityFunctions.blendDensity(DensityFunctions.endIslands(v)));
    }

    @Property
    public void testNoise(@ForAll double amplitude) {
        testCompiledEquivalency(DensityFunctions.noise(
                Holder.direct(new NormalNoise.NoiseParameters(1, amplitude, 0.5 * amplitude, 0.2 * amplitude))));
    }

    @Property
    public void testShiftNoise(@ForAll double amplitude) {
        testCompiledEquivalency(DensityFunctions.shift(
                Holder.direct(new NormalNoise.NoiseParameters(1, amplitude, 0.5 * amplitude, 0.2 * amplitude))));
    }

    @Property
    public void testShiftANoise(@ForAll double amplitude) {
        testCompiledEquivalency(DensityFunctions.shiftA(
                Holder.direct(new NormalNoise.NoiseParameters(1, amplitude, 0.5 * amplitude, 0.2 * amplitude))));
    }

    @Property
    public void testShiftBNoise(@ForAll double amplitude) {
        testCompiledEquivalency(DensityFunctions.shiftB(
                Holder.direct(new NormalNoise.NoiseParameters(1, amplitude, 0.5 * amplitude, 0.2 * amplitude))));
    }

    @Property(tries = 4000)
    public void testShiftedNoise2D(
            @ForAll double amplitude, @ForAll double shiftX, @ForAll double shiftZ, @ForAll double xzScale) {
        testCompiledEquivalency(DensityFunctions.shiftedNoise2d(
                DensityFunctions.constant(shiftX),
                DensityFunctions.constant(shiftZ),
                xzScale,
                Holder.direct(new NormalNoise.NoiseParameters(1, amplitude, 0.5 * amplitude, 0.2 * amplitude))));
    }

    @Property(tries = 4000)
    public void testYClampedGradient(
            @ForAll int fromY, @ForAll int toY, @ForAll double fromValue, @ForAll double toValue) {
        testCompiledEquivalency(DensityFunctions.yClampedGradient(fromY, toY, fromValue, toValue));
    }

    @Property
    public void testSumOfConstantsDoubled(@ForAll double a, @ForAll double b) {
        testCompiledEquivalency(DensityFunctions.add(
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b)),
                DensityFunctions.add(DensityFunctions.constant(a), DensityFunctions.constant(b))));
    }

    @Property(tries = 4000)
    public void testChoiceOfConstants(
            @ForAll double choice,
            @ForAll double min,
            @ForAll double max,
            @ForAll double ifTrue,
            @ForAll double ifFalse) {
        testCompiledEquivalency(DensityFunctions.rangeChoice(
                DensityFunctions.constant(choice),
                min,
                max,
                DensityFunctions.constant(ifTrue),
                DensityFunctions.constant(ifFalse)));
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
