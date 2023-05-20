package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Objects;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NormalNoise.class)
public class NormalNoiseMixin {
    @Shadow
    @Final
    private double valueFactor;

    @Shadow
    @Final
    private PerlinNoise first;

    @Shadow
    @Final
    private PerlinNoise second;

    @Shadow
    @Final
    private double maxValue;

    @Shadow
    @Final
    private NormalNoise.NoiseParameters parameters;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NormalNoiseMixin that = (NormalNoiseMixin) o;
        return Double.compare(that.valueFactor, valueFactor) == 0
                && Double.compare(that.maxValue, maxValue) == 0
                && Objects.equals(first, that.first)
                && Objects.equals(second, that.second)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valueFactor, first, second, maxValue, parameters);
    }

    @Override
    public String toString() {
        return "NormalNoise{"
                + "valueFactor="
                + valueFactor
                + ", first="
                + first
                + ", second="
                + second
                + ", maxValue="
                + maxValue
                + ", parameters="
                + parameters
                + '}';
    }
}
