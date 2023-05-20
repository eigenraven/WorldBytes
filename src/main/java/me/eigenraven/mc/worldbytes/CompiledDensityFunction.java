package me.eigenraven.mc.worldbytes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

// Use mapping-independent method names to simplify the ASM class generation
public abstract class CompiledDensityFunction implements DensityFunction {
    private static final MethodType constructorType =
            MethodType.methodType(void.class, DensityFunction.class, DensityFunction[].class, NoiseHolder[].class);
    private static final MethodType constructorHandleType = MethodType.methodType(
            CompiledDensityFunction.class, DensityFunction.class, DensityFunction[].class, NoiseHolder[].class);
    public final DensityFunction[] functions;
    public final NoiseHolder[] noises;
    public final MethodHandle constructor;

    public CompiledDensityFunction(
            final DensityFunction original, final DensityFunction[] functions, NoiseHolder[] noises) {
        this.original = original;
        this.functions = functions;
        this.noises = noises;
        try {
            this.constructor = MethodHandles.publicLookup()
                    .findConstructor(getClass(), constructorType)
                    .asType(constructorHandleType);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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

        DensityFunction[] newFunctions =
                Arrays.stream(functions).map(visitor::apply).toArray(DensityFunction[]::new);
        NoiseHolder[] newNoises = Arrays.copyOf(noises, noises.length);
        try {
            return (CompiledDensityFunction) this.constructor.invokeExact(original, newFunctions, newNoises);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public double compiledMinValue() {
        return original.minValue();
    }

    public double compiledMaxValue() {
        return original.maxValue();
    }

    public long functionIndex() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompiledDensityFunction that = (CompiledDensityFunction) o;
        return Objects.deepEquals(functions, that.functions) && Objects.deepEquals(noises, that.noises);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionIndex(), Arrays.hashCode(functions), Arrays.hashCode(noises));
    }

    @Override
    public String toString() {
        return "CompiledDensityFunction#" + functionIndex();
    }
}
