package me.eigenraven.mc.worldbytes;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Fixed-name functions called from the compiled DF code */
@SuppressWarnings("unused")
public final class DensityFunctionUtils {
    private DensityFunctionUtils() {}

    public static double blendDensity(double d, DensityFunction.FunctionContext fctx) {
        return fctx.getBlender().blendDensity(fctx, d);
    }

    public static double clamp(double val, double min, double max) {
        return Mth.clamp(val, min, max);
    }

    public static double compute(DensityFunction df, DensityFunction.FunctionContext fctx) {
        return df.compute(fctx);
    }
}
