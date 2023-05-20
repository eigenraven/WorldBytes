package me.eigenraven.mc.worldbytes.mixin;

import me.eigenraven.mc.worldbytes.DensityFunctionCompiler;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RandomState.class)
public class RandomStateMixin {
    @Redirect(
            method =
                    "Lnet/minecraft/world/level/levelgen/RandomState;<init>(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private NoiseRouter worldbytes$compileNoiseRouter(NoiseRouter instance, DensityFunction.Visitor visitor) {
        final NoiseRouter mapped = instance.mapAll(visitor);
        final NoiseRouter compiled = DensityFunctionCompiler.compileNoiseRouter(mapped);
        return compiled;
    }
}
