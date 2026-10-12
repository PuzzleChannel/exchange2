package com.puzzlechannel.vmexchange.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/** Temporary deposits accept 64 resources, including normal 16-stack pearls. */
public final class ResourceDepositSlot extends Slot {
    private final Predicate<ItemStack> accepts;
    public ResourceDepositSlot(Container container, int index, int x, int y, Predicate<ItemStack> accepts) {
        super(container, index, x, y);
        this.accepts = accepts;
    }
    @Override public boolean mayPlace(ItemStack stack) { return accepts.test(stack); }
    @Override public int getMaxStackSize() { return ResourceDepositContainer.CAPACITY; }
    @Override public int getMaxStackSize(ItemStack stack) { return ResourceDepositContainer.CAPACITY; }
}
