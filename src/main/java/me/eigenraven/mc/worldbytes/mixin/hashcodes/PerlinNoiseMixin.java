package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PerlinNoise.class)
public class PerlinNoiseMixin {
    @Shadow
    @Final
    private ImprovedNoise[] noiseLevels;

    @Shadow
    @Final
    private int firstOctave;

    @Shadow
    @Final
    private DoubleList amplitudes;

    @Shadow
    @Final
    private double lowestFreqValueFactor;

    @Shadow
    @Final
    private double lowestFreqInputFactor;

    @Shadow
    @Final
    private double maxValue;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PerlinNoiseMixin that = (PerlinNoiseMixin) o;
        return firstOctave == that.firstOctave
                && Double.compare(that.lowestFreqValueFactor, lowestFreqValueFactor) == 0
                && Double.compare(that.lowestFreqInputFactor, lowestFreqInputFactor) == 0
                && Double.compare(that.maxValue, maxValue) == 0
                && Arrays.equals(noiseLevels, that.noiseLevels)
                && Objects.equals(amplitudes, that.amplitudes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(firstOctave, amplitudes, lowestFreqValueFactor, lowestFreqInputFactor, maxValue);
        result = 31 * result + Arrays.hashCode(noiseLevels);
        return result;
    }

    @Override
    public String toString() {
        return "PerlinNoise{"
                + "noiseLevels="
                + Arrays.toString(noiseLevels)
                + ", firstOctave="
                + firstOctave
                + ", amplitudes=[x*"
                + (amplitudes == null ? 0 : amplitudes.size())
                + "], lowestFreqValueFactor="
                + lowestFreqValueFactor
                + ", lowestFreqInputFactor="
                + lowestFreqInputFactor
                + ", maxValue="
                + maxValue
                + '}';
    }
}
