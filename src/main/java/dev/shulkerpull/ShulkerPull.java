package dev.shulkerpull;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Common entry point: registers the packet on both client and server. */
@Mod("shulkerpull")
public final class ShulkerPull {
    public static final String MOD_ID = "shulkerpull";

    public ShulkerPull(IEventBus modBus) {
        modBus.addListener(ShulkerPull::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional(): a client with this mod can still join a server without it (the feature just stays off).
        PayloadRegistrar registrar = event.registrar(MOD_ID).versioned("1").optional();
        registrar.playToServer(TakeFromShulker.TYPE, TakeFromShulker.CODEC, ServerHandler::handle);
    }
}
