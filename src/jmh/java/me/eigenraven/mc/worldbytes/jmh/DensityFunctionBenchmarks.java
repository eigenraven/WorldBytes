package me.eigenraven.mc.worldbytes.jmh;

import me.eigenraven.mc.worldbytes.DensityFunctionCompiler;
import net.minecraft.CrashReport;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class DensityFunctionBenchmarks {
    public static final DensityFunction add22Vanilla, add22Compiled;

    static {
        synchronized (Bootstrap.class) {
            SharedConstants.tryDetectVersion();
            SharedConstants.enableDataFixerOptimizations();
            CrashReport.preload();
            Bootstrap.bootStrap();
            Bootstrap.validate();
        }
        add22Vanilla = DensityFunctions.add(DensityFunctions.constant(2D), DensityFunctions.constant(2D));
        add22Compiled = DensityFunctionCompiler.compile(add22Vanilla);
    }

    @Benchmark
    public void add22$vanilla(Blackhole bh) {
        bh.consume(add22Vanilla.compute(null));
    }

    @Benchmark
    public void add22$compiled(Blackhole bh) {
        bh.consume(add22Compiled.compute(null));
    }
}
