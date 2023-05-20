package me.eigenraven.mc.worldbytes.mixin.hashcodes;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ImprovedNoise.class)
public class ImprovedNoiseMixin {
    @Shadow
    @Final
    private byte[] p;

    @Shadow
    @Final
    public double xo;

    @Shadow
    @Final
    public double yo;

    @Shadow
    @Final
    public double zo;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImprovedNoiseMixin that = (ImprovedNoiseMixin) o;
        return Double.compare(that.xo, xo) == 0
                && Double.compare(that.yo, yo) == 0
                && Double.compare(that.zo, zo) == 0
                && Arrays.equals(p, that.p);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(xo, yo, zo);
        result = 31 * result + Arrays.hashCode(p);
        return result;
    }

    @Override
    public String toString() {
        return "ImprovedNoise{" + "p=[x*" + ArrayUtils.getLength(p) + "], xo=" + xo + ", yo=" + yo + ", zo=" + zo + '}';
    }
}
