package dev.shulkerpull;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/** Read-only helpers shared by client and server: how many of an item are inside a shulker box. */
final class Contents {
    private Contents() {}

    /** Items inside the shulker box, including items inside bundles that are inside the box. */
    static int countInShulker(ItemStack box, Item item) {
        ItemContainerContents c = box.get(DataComponents.CONTAINER);
        if (c == null) return 0;
        int n = 0;
        for (ItemStack in : (Iterable<ItemStack>) c.nonEmptyItemCopyStream()::iterator) {
            if (in.getItem() == item) n += in.getCount();
            n += countInBundle(in, item);
        }
        return n;
    }

    /** Items inside a bundle (0 if the stack is not a bundle). */
    static int countInBundle(ItemStack stack, Item item) {
        BundleContents b = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (b == null) return 0;
        int n = 0;
        for (ItemStackTemplate t : b.items()) {
            if (t.item().value() == item) n += t.count();
        }
        return n;
    }
}
