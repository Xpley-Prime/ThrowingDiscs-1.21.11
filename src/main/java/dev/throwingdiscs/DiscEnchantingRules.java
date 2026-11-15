package dev.throwingdiscs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Single whitelist shared by the enchanting table, anvil, and enchantment
 * writes on real discs. An enchanted book can still contain anything, but
 * only the permitted entries can be transferred onto a Throwing Disc.
 */
public final class DiscEnchantingRules {
    private DiscEnchantingRules() {}

    public static final int MAX_SPECIAL = 3;

    private static ResourceKey<Enchantment> key(String namespace, String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(namespace, name));
    }

    private static final Set<ResourceKey<Enchantment>> SPECIAL = Set.of(
            key("throwingdiscs", "otkid"),
            key("throwingdiscs", "prilipanie"),
            key("throwingdiscs", "otskok"),
            key("throwingdiscs", "dlitelnost"),
            key("minecraft", "wind_burst")
    );

    private static final Set<ResourceKey<Enchantment>> ALLOWED = Set.of(
            key("minecraft", "sharpness"),
            key("minecraft", "smite"),
            key("minecraft", "bane_of_arthropods"),
            key("minecraft", "fire_aspect"),
            key("minecraft", "unbreaking"),
            key("minecraft", "mending"),
            key("minecraft", "vanishing_curse"),
            key("minecraft", "wind_burst"),
            key("throwingdiscs", "otkid"),
            key("throwingdiscs", "prilipanie"),
            key("throwingdiscs", "otskok"),
            key("throwingdiscs", "dlitelnost")
    );

    public static boolean isDisc(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof DiscItem;
    }

    public static boolean isAllowed(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(ALLOWED::contains).orElse(false);
    }

    public static boolean isSpecial(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(SPECIAL::contains).orElse(false);
    }

    public static int specialCount(ItemStack stack) {
        int total = 0;
        for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            if (isSpecial(enchantment)) total++;
        }
        return total;
    }

    /**
     * Prevents the enchantment table and ordinary book/anvil operations from
     * adding a fourth distinct special enchantment. Upgrading an existing
     * special enchantment is fine and does not consume another slot.
     */
    public static boolean canApply(ItemStack stack, Holder<Enchantment> candidate) {
        if (!isDisc(stack) || !isAllowed(candidate)) return false;
        if (!isValid(stack)) return false;

        if (isSpecial(candidate) && specialCount(stack) >= MAX_SPECIAL
                && !stack.getEnchantments().keySet().contains(candidate)) {
            return false;
        }

        for (Holder<Enchantment> existing : stack.getEnchantments().keySet()) {
            if (!existing.equals(candidate) && !Enchantment.areCompatible(existing, candidate)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Also validates the complete output created by the vanilla anvil. This
     * protects against merging multiple enchantments from one book or from
     * pre-existing items made by older versions of the mod.
     */
    public static boolean isValid(ItemStack stack) {
        if (!isDisc(stack)) return true;
        if (specialCount(stack) > MAX_SPECIAL) return false;

        List<Holder<Enchantment>> all = new ArrayList<>(stack.getEnchantments().keySet());
        for (int i = 0; i < all.size(); i++) {
            if (!isAllowed(all.get(i))) return false;
            for (int j = i + 1; j < all.size(); j++) {
                if (!Enchantment.areCompatible(all.get(i), all.get(j))) return false;
            }
        }
        return true;
    }
}
