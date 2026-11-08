package dev.shulkerpull;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "move up to {count} of {item} out of shulker boxes in my inventory into my inventory". */
public record TakeFromShulker(Identifier item, int count) implements CustomPacketPayload {
    public static final Type<TakeFromShulker> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ShulkerPull.MOD_ID, "take_from_shulker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TakeFromShulker> CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, TakeFromShulker::item,
            ByteBufCodecs.VAR_INT, TakeFromShulker::count,
            TakeFromShulker::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
