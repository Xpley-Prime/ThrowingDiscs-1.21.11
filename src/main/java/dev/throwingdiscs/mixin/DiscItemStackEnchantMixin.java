package dev.throwingdiscs.mixin;

import dev.throwingdiscs.DiscEnchantingRules;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The enchanting table can roll more than one enchantment at once. Checking
 * only the original input stack is insufficient for the 3-special limit:
 * check each successive enchantment write against the actual current stack.
 */
@Mixin(ItemStack.class)
public abstract class DiscItemStackEnchantMixin {
    @Inject(method = "enchant", at = @At("HEAD"), cancellable = true)
    private void throwingdiscs$limitEnchantments(Holder<Enchantment> enchantment,
                                                  int level, CallbackInfo ci) {
        ItemStack stack = (ItemStack)(Object)this;
        if (DiscEnchantingRules.isDisc(stack)
                && !DiscEnchantingRules.canApply(stack, enchantment)) {
            ci.cancel();
        }
    }
}
