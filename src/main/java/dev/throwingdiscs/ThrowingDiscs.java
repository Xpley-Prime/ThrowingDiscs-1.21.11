package dev.throwingdiscs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

public final class ThrowingDiscs implements ModInitializer {
    public static final String ID = "throwingdiscs";

    public static final DiscItem COPPER = register(new DiscStats("copper", 190, 3f, .85, 8, 12, 1, 4, 11));
    public static final DiscItem IRON = register(new DiscStats("iron", 400, 4f, .95, 11, 14, 2, 6, 10));
    public static final DiscItem GOLDEN = register(new DiscStats("golden", 140, 3.5f, 1.16, 10, 12, 3, 6, 7));
    public static final DiscItem DIAMOND = register(new DiscStats("diamond", 900, 6f, 1.05, 14, 16, 4, 9, 9));
    public static final DiscItem NETHERITE = register(new DiscStats("netherite", 1700, 7.5f, 1.14, 16, 18, 5, 12, 8));

    private static final Identifier PROJECTILE_ID = Identifier.fromNamespaceAndPath(ID, "throwing_disc");
    private static final ResourceKey<EntityType<?>> PROJECTILE_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PROJECTILE_ID);

    public static final EntityType<DiscProjectile> PROJECTILE =
            Registry.register(BuiltInRegistries.ENTITY_TYPE, PROJECTILE_KEY,
                    EntityType.Builder.<DiscProjectile>of(DiscProjectile::new, MobCategory.MISC)
                            .sized(.46f, .20f).clientTrackingRange(10).updateInterval(1)
                            .build(PROJECTILE_KEY));

    public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(ID, "discs"));
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.throwingdiscs"))
                    .icon(() -> new ItemStack(DIAMOND))
                    .displayItems((context, output) -> {
                        output.accept(COPPER);
                        output.accept(IRON);
                        output.accept(GOLDEN);
                        output.accept(DIAMOND);
                        output.accept(NETHERITE);
                    }).build());

    private static DiscItem register(DiscStats stats) {
        Identifier id = Identifier.fromNamespaceAndPath(ID, stats.name() + "_disc");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG, Identifier.fromNamespaceAndPath(ID, stats.name()));
        return Registry.register(BuiltInRegistries.ITEM, key,
                new DiscItem(stats, new Item.Properties().setId(key)
                        .durability(stats.durability()).enchantable(12).jukeboxPlayable(song)));
    }

    @Override
    public void onInitialize() {
        // Class initialization registers the five items, entity type and tab.
        TAB.toString();
    }
}
