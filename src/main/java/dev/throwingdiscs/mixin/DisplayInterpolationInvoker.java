package dev.throwingdiscs.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Mojang marks Display's position/rotation interpolation setter private.
 * Invoker exposes the vanilla synchronized teleport_duration setting without
 * reflection, manual packets, or a client-only movement workaround.
 */
@Mixin(Display.class)
public interface DisplayInterpolationInvoker {
    @Invoker("setPosRotInterpolationDuration")
    void throwingdiscs$setPosRotInterpolationDuration(int ticks);
}
