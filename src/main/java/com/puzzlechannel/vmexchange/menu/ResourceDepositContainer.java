package com.puzzlechannel.vmexchange.menu;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Both the slot AND its storage must accept the temporary 64-item deposits. */
public final class ResourceDepositContainer extends SimpleContainer {
    public static final int CAPACITY = 64;
    public ResourceDepositContainer(int size) { super(size); }
    @Override public int getMaxStackSize() { return CAPACITY; }
    @Override public int getMaxStackSize(ItemStack stack) { return CAPACITY; }
}
