package dev.throwingdiscs;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * The projectile's own hitbox is invisible. Its linked vanilla item_display
 * draws the thin ring and performs its yaw spin/wobble.
 */
public final class ThrowingDiscsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ThrowingDiscs.PROJECTILE, NoopRenderer::new);
    }
}
