package dev.throwingdiscs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Data-pack enchantments are looked up from the live dynamic registry. */
public final class DiscEnchantments {
    private DiscEnchantments() {}

    public static final ResourceKey<Enchantment> KNOCKBACK =
            key("otkid");
    public static final ResourceKey<Enchantment> ADHESION =
            key("prilipanie");
    public static final ResourceKey<Enchantment> RICOCHET =
            key("otskok");
    public static final ResourceKey<Enchantment> DURATION =
            key("dlitelnost");

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(ThrowingDiscs.ID, id));
    }

    public static int level(ServerLevel world, ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(enchantment)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }
}
