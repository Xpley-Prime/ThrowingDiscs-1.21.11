package dev.throwingdiscs.mixin;

import dev.throwingdiscs.DiscEnchantingRules;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Book+disc and disc+disc merges can bypass ItemStack.enchant by using a
 * complete enchantment component. Reject any anvil output that violates
 * the whitelist, vanilla exclusivity or three-special limit.
 */
@Mixin(AnvilMenu.class)
public abstract class DiscAnvilMenuMixin {
    @Inject(method = "createResult", at = @At("TAIL"))
    private void throwingdiscs$validateAnvilResult(CallbackInfo ci) {
        AnvilMenu menu = (AnvilMenu)(Object)this;
        Slot output = menu.getSlot(AnvilMenu.RESULT_SLOT);
        ItemStack stack = output.getItem();
        if (DiscEnchantingRules.isDisc(stack) && !DiscEnchantingRules.isValid(stack)) {
            output.set(ItemStack.EMPTY);
        }
    }
}
