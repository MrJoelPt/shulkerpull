package dev.shulkerpull;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Client side: watches what BuildPrint still needs near you, and when a needed block is only
 * available inside a shulker box in your inventory, asks the server to move some into your inventory.
 * From there BuildPrint's easy place / printer use it like any other block.
 */
public final class ShulkerPullClient implements ClientModInitializer {
    /** Run the check twice a second. */
    private static final int CHECK_EVERY_TICKS = 10;
    /** Do not ask for the same item again for this many ticks. */
    private static final int ITEM_COOLDOWN_TICKS = 30;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ShulkerPull.MOD_ID, "main"));
    /** Toggle key. Default: backslash. Change it under Options > Controls > Shulker Pull. */
    private static final KeyMapping TOGGLE =
            new KeyMapping("key.shulkerpull.toggle", InputConstants.KEY_BACKSLASH, CATEGORY);

    private static boolean enabled = false;

    private static final Map<String, Long> LAST_REQUEST = new HashMap<>();
    private static boolean buildprintPresent;
    private static long tick;

    @Override
    public void onInitializeClient() {
        buildprintPresent = FabricLoader.getInstance().isModLoaded("buildprint");
        Config.load(FabricLoader.getInstance().getConfigDir());
        KeyMappingHelper.registerKeyMapping(TOGGLE);
        // Badge in the world: drawn right after the vanilla hotbar.
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(ShulkerPull.MOD_ID, "status"), (graphics, delta) -> {
                    // While a screen is open the screen hook below draws the badge on top of it instead.
                    if (Minecraft.getInstance().gui.screen() == null) drawBadge(graphics);
                });
        // Badge with the inventory (or any container screen) open.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                ScreenEvents.afterExtract(screen).register((s2, graphics, mx, my, dt) -> drawBadge(graphics));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
    }

    /** Small status badge, bottom-left next to the hotbar. Shown only while the add-on is switched on. */
    private static void drawBadge(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || !buildprintPresent || mc.player == null || mc.getConnection() == null) return;
        boolean serverReady = ClientPlayNetworking.canSend(TakeFromShulker.TYPE);
        Font font = mc.font;
        Component label = Component.translatable(serverReady ? "shulkerpull.hud.on" : "shulkerpull.hud.no_server");
        int dot = serverReady ? 0xFF55FF55 : 0xFFFFAA00;
        int w = font.width(label) + 16;
        int h = 14;
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 91 - 34 - w;
        int y = mc.getWindow().getGuiScaledHeight() - h - 4;
        if (x < 2) x = 2;
        g.fill(x, y, x + w, y + h, 0xC0000000);          // dark background
        g.fill(x + 3, y + 4, x + 8, y + 9, dot);        // status dot
        g.text(font, label, x + 11, y + 3, 0xFFFFFFFF, true);
    }

    private static void onClientTick() {
        while (TOGGLE.consumeClick()) {
            enabled = !enabled;
            LocalPlayer me = Minecraft.getInstance().player;
            if (me != null) {
                me.sendOverlayMessage(Component.translatable(
                        enabled ? "shulkerpull.message.on" : "shulkerpull.message.off"));
            }
        }
        if (!enabled || !buildprintPresent) return;
        tick++;
        if (tick % CHECK_EVERY_TICKS != 0) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) return;
        if (mc.gui.screen() != null || player.isSpectator()) return;
        // Server must have this mod, otherwise there is nobody to answer.
        if (!ClientPlayNetworking.canSend(TakeFromShulker.TYPE)) return;

        Map<String, Integer> needed;
        try {
            String dim = mc.level.dimension().identifier().toString();
            needed = BuildprintBridge.stillNeeded(dim, player.getX(), player.getY(), player.getZ(), Config.range());
        } catch (Throwable t) {
            return; // BuildPrint not ready or API changed: do nothing rather than crash.
        }
        if (needed.isEmpty()) return;

        Inventory inv = player.getInventory();
        for (Map.Entry<String, Integer> e : needed.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.getValue(id);

            int have = countLoose(inv, item);
            // Never more than one stack, and never more than the build still needs.
            int target = Math.min(e.getValue(), item.getDefaultMaxStackSize());
            // Refill once the stack in your inventory has dropped below half of the target.
            if (have * 2 >= target) continue;

            Long last = LAST_REQUEST.get(e.getKey());
            if (last != null && tick - last < ITEM_COOLDOWN_TICKS) continue;
            if (!insideAShulker(inv, item)) continue;

            int ask = target - have;
            if (ask <= 0) continue;
            LAST_REQUEST.put(e.getKey(), tick);
            ClientPlayNetworking.send(new TakeFromShulker(id, ask));
            return; // one request per check keeps traffic tiny
        }
    }

    /** How many of the item are lying loose in the inventory (not counting what is inside shulker boxes). */
    private static int countLoose(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() == item) n += s.getCount();
        }
        return n;
    }

    private static boolean insideAShulker(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack box = inv.getItem(i);
            if (box.isEmpty() || !box.is(ItemTags.SHULKER_BOXES)) continue;
            if (Contents.countInShulker(box, item) > 0) return true;
        }
        return false;
    }
}
