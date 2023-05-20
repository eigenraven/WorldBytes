package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.ToFloatFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CubicSpline.Multipoint.class)
public class CubicSplineMixin {
    @Final
    @Shadow
    ToFloatFunction coordinate;

    @Final
    @Shadow
    float[] locations;

    @Final
    @Shadow
    List values;

    @Final
    @Shadow
    float[] derivatives;

    @Final
    @Shadow
    float minValue;

    @Final
    @Shadow
    float maxValue;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CubicSplineMixin that = (CubicSplineMixin) o;
        return Float.compare(that.minValue, minValue) == 0
                && Float.compare(that.maxValue, maxValue) == 0
                && Objects.equals(coordinate, that.coordinate)
                && Arrays.equals(locations, that.locations)
                && Objects.equals(values, that.values)
                && Arrays.equals(derivatives, that.derivatives);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(coordinate, values, minValue, maxValue);
        result = 31 * result + Arrays.hashCode(locations);
        result = 31 * result + Arrays.hashCode(derivatives);
        return result;
    }
}
