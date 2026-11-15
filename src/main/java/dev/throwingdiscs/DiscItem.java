package dev.throwingdiscs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Throw from either hand. Surviving discs return to the hand or inventory. */
public final class DiscItem extends Item {
    private final DiscStats stats;

    public DiscItem(DiscStats stats, Properties properties) {
        super(properties);
        this.stats = stats;
    }

    public DiscStats stats() {
        return stats;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(held)) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner) || !(level instanceof ServerLevel world))
            return InteractionResult.PASS;

        boolean creative = owner.getAbilities().instabuild;
        ItemStack flying = held.copy();
        flying.setCount(1);
        if (!creative) {
            int newDamage = flying.getDamageValue() + 1;
            // Break before launching, as any tool reaching zero durability would.
            if (newDamage >= flying.getMaxDamage()) {
                held.shrink(1);
                world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                        SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 1f);
                return InteractionResult.SUCCESS;
            }
            flying.setDamageValue(newDamage);
        }

        DiscProjectile projectile = new DiscProjectile(world, owner, flying, hand, creative);
        if (!world.addFreshEntity(projectile)) return InteractionResult.FAIL;
        if (!creative) held.shrink(1);

        owner.getCooldowns().addCooldown(flying, stats.cooldownTicks());
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .75f, 1.45f);
        return InteractionResult.SUCCESS;
    }
}
