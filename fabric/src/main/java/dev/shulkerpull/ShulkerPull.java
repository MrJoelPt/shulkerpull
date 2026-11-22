package dev.shulkerpull;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Common entry point: registers the packet and the server-side handler. */
public final class ShulkerPull implements ModInitializer {
    public static final String MOD_ID = "shulkerpull";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(TakeFromShulker.TYPE, TakeFromShulker.CODEC);
        // Fabric runs this on the server thread already.
        ServerPlayNetworking.registerGlobalReceiver(TakeFromShulker.TYPE,
                (payload, context) -> ServerHandler.handle(payload, context.player()));
    }
}
