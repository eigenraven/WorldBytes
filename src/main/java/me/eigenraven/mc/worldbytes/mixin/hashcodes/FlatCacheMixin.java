package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseChunk.FlatCache.class)
public class FlatCacheMixin {
    @Shadow
    @Final
    private DensityFunction noiseFiller;
    // Ignore the cached values

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlatCacheMixin that = (FlatCacheMixin) o;
        return Objects.equals(noiseFiller, that.noiseFiller);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noiseFiller);
    }

    @Override
    public String toString() {
        return "FlatCache{" + "noiseFiller=" + noiseFiller + '}';
    }
}
