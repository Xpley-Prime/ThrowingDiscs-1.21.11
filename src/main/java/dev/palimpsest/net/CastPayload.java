package dev.palimpsest.net;

import dev.palimpsest.Palimpsest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Raw traced points, never the result of client-side recognition. */
public record CastPayload(String ink) implements CustomPacketPayload {
    public static final Type<CastPayload> ID = new Type<>(Palimpsest.id("ink"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CastPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.stringUtf8(3500), CastPayload::ink, CastPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
