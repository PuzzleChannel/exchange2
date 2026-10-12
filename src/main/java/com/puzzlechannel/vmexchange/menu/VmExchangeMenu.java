package com.puzzlechannel.vmexchange.menu;

import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.network.ClientState;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class VmExchangeMenu extends AbstractContainerMenu implements VmExchangeManager.VmExchangeMenuAccess {
    public static final int EXCHANGE_SLOTS = 4;
    private final ResourceDepositContainer container = new ResourceDepositContainer(EXCHANGE_SLOTS);
    private final Inventory playerInventory;

    public VmExchangeMenu(int syncId, Inventory playerInventory) {
        super(ModMenus.EXCHANGE, syncId);
        this.playerInventory = playerInventory;
        // Four resource inputs use real Minecraft-style slot coordinates matching the screen.
        for (int i = 0; i < EXCHANGE_SLOTS; i++) {
            final int index = i;
            addSlot(new ResourceDepositSlot(container, i, ExchangeLayout.INPUT_X[i], ExchangeLayout.INPUT_Y,
                    stack -> isAcceptedAt(stack, index)));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        ExchangeLayout.INVENTORY_X + col * 18, ExchangeLayout.INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, ExchangeLayout.INVENTORY_X + col * 18, ExchangeLayout.HOTBAR_Y));
        }
    }

    private boolean isAccepted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        MinecraftServer server = playerInventory.player.level().getServer();
        if (server == null) {
            var s = ClientState.get();
            return id.equals(s.m1()) || id.equals(s.m2()) || id.equals(s.m3()) || id.equals(s.m4());
        }
        return VmExchangeManager.get(server).materials().contains(id);
    }

    private boolean isAcceptedAt(ItemStack stack, int slotIndex) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        MinecraftServer server = playerInventory.player.level().getServer();
        if (server != null) {
            var materials = VmExchangeManager.get(server).materials();
            return slotIndex < materials.size() && materials.get(slotIndex).equals(id);
        }
        var state = ClientState.get();
        String expected = switch (slotIndex) { case 0 -> state.m1(); case 1 -> state.m2(); case 2 -> state.m3(); case 3 -> state.m4(); default -> ""; };
        return id.equals(expected);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index < EXCHANGE_SLOTS) {
            if (!moveItemStackTo(slot.getItem(), EXCHANGE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            ItemStack source = slot.getItem();
            if (!isAccepted(source)) return ItemStack.EMPTY;
            boolean moved = false;
            // Vanilla quick-move caps at the item's ordinary stack limit. Our
            // deposit wells deliberately accept up to 64 of each resource.
            for (int i = 0; i < EXCHANGE_SLOTS; i++) {
                Slot target = slots.get(i);
                if (!target.mayPlace(source)) continue;
                ItemStack existing = target.getItem();
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, source)) continue;
                int amount = Math.min(source.getCount(), ResourceDepositContainer.CAPACITY - existing.getCount());
                if (amount <= 0) continue;
                if (existing.isEmpty()) target.setByPlayer(source.split(amount));
                else { existing.grow(amount); source.shrink(amount); target.setChanged(); }
                moved = true;
                break;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override public boolean stillValid(Player player) { return player.isAlive() && !player.isRemoved(); }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            for (int i = 0; i < EXCHANGE_SLOTS; i++) {
                ItemStack stack = container.removeItemNoUpdate(i);
                if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    public void performExchange(Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        VmExchangeManager.get(sp.level().getServer()).exchange(sp, this);
        broadcastChanges();
    }
    public boolean canExchange(Player player) { return player instanceof net.minecraft.server.level.ServerPlayer; }
    @Override public int exchangeSlots() { return EXCHANGE_SLOTS; }
    @Override public ItemStack stackInSlot(int slot) { return container.getItem(slot); }
    @Override public void removeFromSlot(int slot, int amount) { container.removeItem(slot, amount); }
}
