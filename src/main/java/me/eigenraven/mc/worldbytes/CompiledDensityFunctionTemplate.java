package me.eigenraven.mc.worldbytes;

import net.minecraft.world.level.levelgen.DensityFunction;

public class CompiledDensityFunctionTemplate extends CompiledDensityFunction {
    public CompiledDensityFunctionTemplate(
            DensityFunction original, DensityFunction[] functions, NoiseHolder[] noises) {
        super(original, functions, noises);
    }

    @Override
    public long functionIndex() {
        return super.functionIndex();
    }

    @Override
    public double compiledCompute(FunctionContext context) {
        return super.compiledCompute(context);
    }

    @Override
    public void compiledFillArray(double[] vals, ContextProvider contextProvider) {
        super.compiledFillArray(vals, contextProvider);
    }

    @Override
    public DensityFunction compiledMapAll(Visitor visitor) {
        return super.compiledMapAll(visitor);
    }

    @Override
    public double compiledMinValue() {
        return super.compiledMinValue();
    }

    @Override
    public double compiledMaxValue() {
        return super.compiledMaxValue();
    }
}
