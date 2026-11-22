package dev.shulkerpull;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side: does the actual taking, with all the checks, so a modified client cannot cheat.
 *
 * This only ever MOVES items that already exist (shulker box -> inventory). It never creates items.
 * As a last line of defence the handler counts the item before and after; if the numbers differ
 * even by one, everything is rolled back to how it was.
 */
final class ServerHandler {
    private static final int SHULKER_SLOTS = 27;
    private static final System.Logger LOG = System.getLogger("shulkerpull");

    private ServerHandler() {}

    static void handle(TakeFromShulker msg, ServerPlayer player) {
        {
            if (player.isSpectator()) return;
            if (!BuiltInRegistries.ITEM.containsKey(msg.item())) return;
            Item item = BuiltInRegistries.ITEM.getValue(msg.item());
            // Shulker boxes cannot hold shulker boxes, and nothing else is worth taking out here.
            if (item.getDefaultInstance().is(ItemTags.SHULKER_BOXES)) return;

            int maxStack = item.getDefaultMaxStackSize();
            int want = Math.max(1, Math.min(msg.count(), maxStack));

            Inventory inv = player.getInventory();
            // Only take when it will fit, so nothing is ever dropped or lost.
            if (inv.getSlotWithRemainingSpace(item.getDefaultInstance()) == -1 && inv.getFreeSlot() == -1) return;

            // Snapshot for the safety check / rollback.
            List<ItemStack> snapshot = new ArrayList<>(inv.getContainerSize());
            for (int i = 0; i < inv.getContainerSize(); i++) snapshot.add(inv.getItem(i).copy());
            int before = totalOf(inv, item);

            int taken = takeOut(inv, item, want);

            int after = totalOf(inv, item);
            if (after != before) {
                // Should be impossible. If it ever happens, undo everything.
                for (int i = 0; i < snapshot.size(); i++) inv.setItem(i, snapshot.get(i));
                LOG.log(System.Logger.Level.WARNING,
                        "Shulker Pull: item count changed ({0} -> {1}) for {2}; rolled back.", before, after, msg.item());
                inv.setChanged();
                player.containerMenu.broadcastChanges();
                return;
            }
            if (taken > 0) {
                inv.setChanged();
                player.containerMenu.broadcastChanges();
            }
        }
    }

    /** Moves up to {@code want} of the item from shulker boxes in the inventory into the inventory. */
    private static int takeOut(Inventory inv, Item item, int want) {
        int taken = 0;
        for (int i = 0; i < inv.getContainerSize() && taken < want; i++) {
            ItemStack box = inv.getItem(i);
            if (box.isEmpty() || !box.is(ItemTags.SHULKER_BOXES)) continue;
            ItemContainerContents contents = box.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            NonNullList<ItemStack> slots = NonNullList.withSize(Math.max(SHULKER_SLOTS, contents.getSlots()), ItemStack.EMPTY);
            contents.copyInto(slots);

            boolean changed = false;
            for (int s = 0; s < slots.size() && taken < want; s++) {
                ItemStack in = slots.get(s);
                if (in.isEmpty()) continue;
                if (in.getItem() == item) {
                    int move = Math.min(in.getCount(), want - taken);
                    ItemStack out = in.copyWithCount(move);
                    in.shrink(move);
                    if (!inv.add(out)) {
                        // Did not all fit: put the leftover straight back into the shulker slot.
                        in.grow(out.getCount());
                        move -= out.getCount();
                        if (move <= 0) want = taken;
                    }
                    taken += move;
                    changed = true;
                } else {
                    // A bundle inside the shulker box: take from inside it.
                    int got = takeFromBundle(inv, in, item, want - taken);
                    if (got > 0) {
                        taken += got;
                        changed = true;
                    }
                }
            }
            if (changed) {
                box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
            }
        }
        return taken;
    }

    /** Moves up to {@code want} of the item out of the bundle (if the stack is one) into the inventory. */
    private static int takeFromBundle(Inventory inv, ItemStack bundle, Item item, int want) {
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || want <= 0) return 0;
        List<ItemStackTemplate> remaining = new ArrayList<>();
        int taken = 0;
        for (ItemStackTemplate t : contents.items()) {
            if (t.item().value() != item || taken >= want) {
                remaining.add(t);
                continue;
            }
            int move = Math.min(t.count(), want - taken);
            ItemStack out = t.create().copyWithCount(move);
            if (!inv.add(out)) {
                // Did not all fit: leave the leftover in the bundle.
                move -= out.getCount();
            }
            taken += move;
            int left = t.count() - move;
            if (left > 0) remaining.add(t.withCount(left));
        }
        if (taken > 0) bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(remaining));
        return taken;
    }

    /** All of the item the player owns: loose in the inventory plus inside shulker boxes in the inventory. */
    private static int totalOf(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() == item) n += s.getCount();
            if (s.is(ItemTags.SHULKER_BOXES)) n += Contents.countInShulker(s, item);
        }
        return n;
    }
}
