package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public abstract class NoiseInterpolatorMixin
        implements DensityFunctions.MarkerOrMarked, NoiseChunk.NoiseChunkDensityFunction {
    @Shadow
    double[][] slice0;

    @Shadow
    double[][] slice1;

    @Shadow
    @Final
    private DensityFunction noiseFiller;

    @Shadow
    private double noise000;

    @Shadow
    private double noise001;

    @Shadow
    private double noise100;

    @Shadow
    private double noise101;

    @Shadow
    private double noise010;

    @Shadow
    private double noise011;

    @Shadow
    private double noise110;

    @Shadow
    private double noise111;

    @Shadow
    private double valueXZ00;

    @Shadow
    private double valueXZ10;

    @Shadow
    private double valueXZ01;

    @Shadow
    private double valueXZ11;

    @Shadow
    private double valueZ0;

    @Shadow
    private double valueZ1;

    @Shadow
    private double value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NoiseInterpolatorMixin that = (NoiseInterpolatorMixin) o;
        return Double.compare(that.noise000, noise000) == 0
                && Double.compare(that.noise001, noise001) == 0
                && Double.compare(that.noise100, noise100) == 0
                && Double.compare(that.noise101, noise101) == 0
                && Double.compare(that.noise010, noise010) == 0
                && Double.compare(that.noise011, noise011) == 0
                && Double.compare(that.noise110, noise110) == 0
                && Double.compare(that.noise111, noise111) == 0
                && Double.compare(that.valueXZ00, valueXZ00) == 0
                && Double.compare(that.valueXZ10, valueXZ10) == 0
                && Double.compare(that.valueXZ01, valueXZ01) == 0
                && Double.compare(that.valueXZ11, valueXZ11) == 0
                && Double.compare(that.valueZ0, valueZ0) == 0
                && Double.compare(that.valueZ1, valueZ1) == 0
                && Double.compare(that.value, value) == 0
                && Arrays.deepEquals(slice0, that.slice0)
                && Arrays.deepEquals(slice1, that.slice1)
                && Objects.equals(noiseFiller, that.noiseFiller);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                noiseFiller,
                noise000,
                noise001,
                noise100,
                noise101,
                noise010,
                noise011,
                noise110,
                noise111,
                valueXZ00,
                valueXZ10,
                valueXZ01,
                valueXZ11,
                valueZ0,
                valueZ1,
                value);
        result = 31 * result + Arrays.deepHashCode(slice0);
        result = 31 * result + Arrays.deepHashCode(slice1);
        return result;
    }

    @Override
    public String toString() {
        return "NoiseInterpolator{"
                + "slice0=[...], slice1=[...], noiseFiller="
                + noiseFiller
                + ", noise000="
                + noise000
                + ", noise001="
                + noise001
                + ", noise100="
                + noise100
                + ", noise101="
                + noise101
                + ", noise010="
                + noise010
                + ", noise011="
                + noise011
                + ", noise110="
                + noise110
                + ", noise111="
                + noise111
                + ", valueXZ00="
                + valueXZ00
                + ", valueXZ10="
                + valueXZ10
                + ", valueXZ01="
                + valueXZ01
                + ", valueXZ11="
                + valueXZ11
                + ", valueZ0="
                + valueZ0
                + ", valueZ1="
                + valueZ1
                + ", value="
                + value
                + '}';
    }
}
