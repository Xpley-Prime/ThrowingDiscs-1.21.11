package dev.palimpsest.net;

import dev.palimpsest.Palimpsest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ManaPayload(int mana) implements CustomPacketPayload {
    public static final Type<ManaPayload> ID = new Type<>(Palimpsest.id("mana"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ManaPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, ManaPayload::mana, ManaPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
