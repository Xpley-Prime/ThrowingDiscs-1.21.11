package dev.palimpsest;

import dev.palimpsest.glyph.*;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.item.CreativeModeTabs;
import dev.palimpsest.content.ArcaneTools;
import dev.palimpsest.content.ArcaneStations;
import dev.palimpsest.content.ArcaneRituals;
import dev.palimpsest.content.ArcaneCatalogue;
import dev.palimpsest.content.ArcanePersistence;
import dev.palimpsest.magic.MagicEngine;
import dev.palimpsest.net.CastPayload;
import dev.palimpsest.net.ManaPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class Palimpsest implements ModInitializer {
    public static final String MOD_ID="palimpsest";
    public static final ResourceKey<Item> QUILL_KEY = ResourceKey.create(Registries.ITEM,id("scribe_quill"));
    public static Item QUILL;
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID,path); }

    @Override public void onInitialize() {
        QUILL = Registry.register(BuiltInRegistries.ITEM,QUILL_KEY,
            new Item(new Item.Properties().setId(QUILL_KEY).stacksTo(1)));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> entries.accept(QUILL));
        ArcaneTools.register();
        ArcaneStations.register();
        PayloadTypeRegistry.playC2S().register(CastPayload.ID,CastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ManaPayload.ID,ManaPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CastPayload.ID,(payload,context) -> {
            ServerPlayer player=context.player();
            try {
                List<List<Point>> strokes=GlyphWire.decode(payload.ink());
                GlyphRecognizer.Match first=GlyphRecognizer.recognize(strokes.get(0),Glyph.Role.INTENT);
                GlyphRecognizer.Match second=GlyphRecognizer.recognize(strokes.get(1),Glyph.Role.ELEMENT);
                GlyphRecognizer.Match third=strokes.size()==3
                    ? GlyphRecognizer.recognize(strokes.get(2),Glyph.Role.INFLECTION)
                    : new GlyphRecognizer.Match(null,1);
                if(!first.accepted() || !second.accepted() || (strokes.size()==3 && !third.accepted())) {
                    player.displayClientMessage(Component.literal("§cЧернила рассыпались: незнакомый символ."),true);
                    return;
                }
                float calligraphy=(float)((first.confidence()+second.confidence()+
                    (strokes.size()==3?third.confidence():0.0)) / strokes.size());
                MagicEngine.cast(player,first.glyph(),second.glyph(),third.glyph(),calligraphy);
            } catch (IllegalArgumentException ex) {
                // Invalid or malicious packets never get to the effect engine.
                player.displayClientMessage(Component.literal("§cНеправильные линии печати."),true);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(MagicEngine::tick);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            handler.player.sendSystemMessage(Component.literal(
                "§5✦ PALIMPSEST §7— Возьми перо из «Инструментов» или скрафти. §bG§7 — начертания, §bK§7 — справочник."));
            handler.player.sendSystemMessage(Component.literal(
                "§7Формула: §eнамерение → стихия → необязательный оттенок. §7В книге есть образцы, а в атласе — все страницы."));
        });
        ArcaneCatalogue.register();
        ArcanePersistence.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(Commands.literal("palimpsest")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§dPALIMPSEST §7— Возьми перо и нажми G. Для книги рун: /palimpsest guide"),false);
                    return 1;
                })
                .then(Commands.literal("guide").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§b1. НАМЕРЕНИЕ: §fНить (стрела), Ореол (круг), Волна (W)"),false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6 2. СТИХИЯ: §fУголь (пламя), Прорастание (S), Иней (кристалл), Завеса (спираль)"),false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§d3. НЕОБЯЗАТЕЛЬНО: §fОтголосок (8), Отражение (песочные часы), Цена (молния)"),false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7ЛКМ и веди мышью. Отпусти — руна записана. Enter — исполнить. Backspace — отменить."),false);
                    return 1;
                }))
                .then(Commands.literal("mana").executes(ctx -> {
                    if(ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§bТвои чернила: §f"+Math.round(MagicEngine.mana(player))+" / 100"),false);
                    }
                    return 1;
                }))
            )
        );
    }
}
