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

/** Right click with either hand: remove one disc until it flies back. */
public final class DiscItem extends Item {
    private final DiscStats stats;

    public DiscItem(DiscStats stats, Properties properties) {
        super(properties);
        this.stats = stats;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(held)) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner) || !(level instanceof ServerLevel world))
            return InteractionResult.PASS;

        // Preserve custom name, enchantments and durability for the returning copy.
        ItemStack flyingStack = held.copy();
        flyingStack.setCount(1);
        boolean creative = owner.getAbilities().instabuild;
        int nextDamage = flyingStack.getDamageValue() + 1;
        boolean breaksOnReturn = !creative && nextDamage >= flyingStack.getMaxDamage();
        if (!creative && !breaksOnReturn) flyingStack.setDamageValue(nextDamage);

        if (!DiscFlights.launch(world, owner, flyingStack, stats, !creative && !breaksOnReturn))
            return InteractionResult.FAIL;

        if (!creative) held.shrink(1);
        owner.getCooldowns().addCooldown(flyingStack, stats.cooldownTicks());
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.6F, 1.5F);
        return InteractionResult.SUCCESS;
    }
}
