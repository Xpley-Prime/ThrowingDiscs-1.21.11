package dev.palimpsest.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.palimpsest.Palimpsest;
import dev.palimpsest.net.ManaPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public final class PalimpsestClient implements ClientModInitializer {
    public static int mana=100;
    @Override public void onInitializeClient() {
        KeyMapping.Category category=KeyMapping.Category.register(Palimpsest.id("casting"));
        KeyMapping open=KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.palimpsest.draw",InputConstants.Type.KEYSYM,InputConstants.KEY_G,category));
        ClientPlayNetworking.registerGlobalReceiver(ManaPayload.ID,(payload,context)->mana=payload.mana());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while(open.consumeClick()) {
                if(client.player!=null && client.screen==null &&
                    (client.player.getMainHandItem().is(Palimpsest.QUILL) ||
                     client.player.getOffhandItem().is(Palimpsest.QUILL)))
                    client.setScreen(new GlyphScreen());
            }
        });
    }
}
