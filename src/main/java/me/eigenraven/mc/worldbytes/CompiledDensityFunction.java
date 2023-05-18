package me.eigenraven.mc.worldbytes;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

// Use mapping-independent method names to simplify the ASM class generation
public abstract class CompiledDensityFunction implements DensityFunction {
    public final DensityFunction[] functions;
    public final NoiseHolder[] noises;

    public CompiledDensityFunction(
            final DensityFunction original, final DensityFunction[] functions, NoiseHolder[] noises) {
        this.original = original;
        this.functions = functions;
        this.noises = noises;
    }

    public final DensityFunction original;

    @Override
    public double compute(FunctionContext context) {
        return compiledCompute(context);
    }

    @Override
    public void fillArray(double[] vals, ContextProvider contextProvider) {
        compiledFillArray(vals, contextProvider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return compiledMapAll(visitor);
    }

    @Override
    public double minValue() {
        return compiledMinValue();
    }

    @Override
    public double maxValue() {
        return compiledMaxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return original.codec();
    }

    public double compiledCompute(FunctionContext context) {
        return original.compute(context);
    }

    public void compiledFillArray(double[] vals, ContextProvider contextProvider) {
        for (int i = 0; i < vals.length; i++) {
            vals[i] = this.compiledCompute(contextProvider.forIndex(i));
        }
    }

    public DensityFunction compiledMapAll(Visitor visitor) {
        return DensityFunctionCompiler.compile(original.mapAll(visitor));
    }

    public double compiledMinValue() {
        return original.minValue();
    }

    public double compiledMaxValue() {
        return original.maxValue();
    }
}
