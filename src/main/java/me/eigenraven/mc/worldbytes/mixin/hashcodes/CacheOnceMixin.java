package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseChunk.CacheOnce.class)
public class CacheOnceMixin {
    @Shadow
    @Final
    private DensityFunction function;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheOnceMixin that = (CacheOnceMixin) o;
        return Objects.equals(function, that.function);
    }

    @Override
    public int hashCode() {
        return Objects.hash(function);
    }

    @Override
    public String toString() {
        return "CacheOnce{" + "function=" + function + '}';
    }
}
