package dev.throwingdiscs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ThrowingDiscs implements ModInitializer {
    public static final String ID = "throwingdiscs";

    public static final DiscItem COPPER = register(new DiscStats("copper", 180, 3f, 1.10, 14, 1, 1, 12));
    public static final DiscItem IRON = register(new DiscStats("iron", 350, 4.5f, 1.25, 19, 2, 2, 11));
    public static final DiscItem GOLDEN = register(new DiscStats("golden", 110, 3.5f, 1.75, 20, 3, 2, 7));
    public static final DiscItem DIAMOND = register(new DiscStats("diamond", 900, 6f, 1.5, 26, 4, 4, 9));
    public static final DiscItem NETHERITE = register(new DiscStats("netherite", 1600, 8f, 1.65, 30, 5, 5, 8));

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
        return Registry.register(BuiltInRegistries.ITEM, key,
                new DiscItem(stats, new Item.Properties().setId(key).durability(stats.durability()).enchantable(12)));
    }

    @Override
    public void onInitialize() {
        // Hold references so registration occurs before the game freezes its registries.
        TAB.toString();
        ServerTickEvents.END_SERVER_TICK.register(DiscFlights::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DiscFlights.releaseAll());
    }
}
