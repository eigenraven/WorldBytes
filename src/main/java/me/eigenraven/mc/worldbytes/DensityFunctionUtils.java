package me.eigenraven.mc.worldbytes;

import net.minecraft.world.level.levelgen.DensityFunction;

/** Fixed-name functions called from the compiled DF code */
@SuppressWarnings("unused")
public final class DensityFunctionUtils {
    private DensityFunctionUtils() {}

    public static double blendDensity(double d, DensityFunction.FunctionContext fctx) {
        return fctx.getBlender().blendDensity(fctx, d);
    }
}
