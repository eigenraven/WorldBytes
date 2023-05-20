package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Objects;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlendedNoise.class)
public class BlendedNoiseMixin {
    @Shadow
    @Final
    private PerlinNoise minLimitNoise;

    @Shadow
    @Final
    private PerlinNoise maxLimitNoise;

    @Shadow
    @Final
    private PerlinNoise mainNoise;

    @Shadow
    @Final
    private double xzMultiplier;

    @Shadow
    @Final
    private double yMultiplier;

    @Shadow
    @Final
    private double xzFactor;

    @Shadow
    @Final
    private double yFactor;

    @Shadow
    @Final
    private double smearScaleMultiplier;

    @Shadow
    @Final
    private double maxValue;

    @Shadow
    @Final
    private double xzScale;

    @Shadow
    @Final
    private double yScale;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlendedNoiseMixin that = (BlendedNoiseMixin) o;
        return Double.compare(that.xzMultiplier, xzMultiplier) == 0
                && Double.compare(that.yMultiplier, yMultiplier) == 0
                && Double.compare(that.xzFactor, xzFactor) == 0
                && Double.compare(that.yFactor, yFactor) == 0
                && Double.compare(that.smearScaleMultiplier, smearScaleMultiplier) == 0
                && Double.compare(that.maxValue, maxValue) == 0
                && Double.compare(that.xzScale, xzScale) == 0
                && Double.compare(that.yScale, yScale) == 0
                && Objects.equals(minLimitNoise, that.minLimitNoise)
                && Objects.equals(maxLimitNoise, that.maxLimitNoise)
                && Objects.equals(mainNoise, that.mainNoise);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                minLimitNoise,
                maxLimitNoise,
                mainNoise,
                xzMultiplier,
                yMultiplier,
                xzFactor,
                yFactor,
                smearScaleMultiplier,
                maxValue,
                xzScale,
                yScale);
    }

    @Override
    public String toString() {
        return "BlendedNoise{"
                + "minLimitNoise="
                + minLimitNoise
                + ", maxLimitNoise="
                + maxLimitNoise
                + ", mainNoise="
                + mainNoise
                + ", xzMultiplier="
                + xzMultiplier
                + ", yMultiplier="
                + yMultiplier
                + ", xzFactor="
                + xzFactor
                + ", yFactor="
                + yFactor
                + ", smearScaleMultiplier="
                + smearScaleMultiplier
                + ", maxValue="
                + maxValue
                + ", xzScale="
                + xzScale
                + ", yScale="
                + yScale
                + '}';
    }
}
