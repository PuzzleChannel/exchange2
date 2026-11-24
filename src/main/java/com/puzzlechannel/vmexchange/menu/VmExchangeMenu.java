package com.puzzlechannel.vmexchange.menu;

import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.network.ClientState;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class VmExchangeMenu extends AbstractContainerMenu implements VmExchangeManager.VmExchangeMenuAccess {
    public static final int EXCHANGE_SLOTS = 4;
    private final ResourceDepositContainer container = new ResourceDepositContainer(EXCHANGE_SLOTS);
    private final Inventory playerInventory;
    private final ResourceKey<Level> exchangeDimension;
    private final BlockPos exchangePos;
    private final String accessGroup;

    public VmExchangeMenu(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, null, null, null);
    }

    public VmExchangeMenu(int syncId, Inventory playerInventory,
                          ResourceKey<Level> exchangeDimension, BlockPos exchangePos, String accessGroup) {
        super(ModMenus.EXCHANGE, syncId);
        this.playerInventory = playerInventory;
        this.exchangeDimension = exchangeDimension;
        this.exchangePos = exchangePos == null ? null : exchangePos.immutable();
        this.accessGroup = accessGroup;

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
            var snapshot = ClientState.get();
            return id.equals(snapshot.m1()) || id.equals(snapshot.m2())
                    || id.equals(snapshot.m3()) || id.equals(snapshot.m4());
        }
        return VmExchangeManager.get(server).materialsForGroup(accessGroup).contains(id);
    }

    private boolean isAcceptedAt(ItemStack stack, int slotIndex) {
        if (stack.isEmpty()) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        MinecraftServer server = playerInventory.player.level().getServer();
        if (server != null) {
            var materials = VmExchangeManager.get(server).materialsForGroup(accessGroup);
            return slotIndex < materials.size() && materials.get(slotIndex).equals(id);
        }
        var snapshot = ClientState.get();
        String expected = switch (slotIndex) {
            case 0 -> snapshot.m1();
            case 1 -> snapshot.m2();
            case 2 -> snapshot.m3();
            case 3 -> snapshot.m4();
            default -> "";
        };
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
            for (int i = 0; i < EXCHANGE_SLOTS; i++) {
                Slot target = slots.get(i);
                if (!target.mayPlace(source)) continue;
                ItemStack existing = target.getItem();
                if (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(existing, source)) continue;
                int amount = Math.min(source.getCount(), ResourceDepositContainer.CAPACITY - existing.getCount());
                if (amount <= 0) continue;
                if (existing.isEmpty()) target.setByPlayer(source.split(amount));
                else {
                    existing.grow(amount);
                    source.shrink(amount);
                    target.setChanged();
                }
                moved = true;
                break;
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override public boolean stillValid(Player player) {
        if (!player.isAlive() || player.isRemoved()) return false;
        if (exchangePos == null || exchangeDimension == null || accessGroup == null) {
            return player.level().isClientSide();
        }
        if (!player.level().dimension().equals(exchangeDimension)) return false;
        if (!player.level().getBlockState(exchangePos).is(ModBlocks.EXCHANGE)) return false;
        if (player.distanceToSqr(Vec3.atCenterOf(exchangePos)) > 64.0D) return false;
        if (player.level().isClientSide()) return true;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        MinecraftServer server = serverPlayer.level().getServer();
        if (server == null) return false;
        String currentGroup = VmExchangeManager.get(server).groupForPlayer(serverPlayer);
        return currentGroup != null && currentGroup.equalsIgnoreCase(accessGroup);
    }

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
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        VmExchangeManager.get(serverPlayer.level().getServer()).exchange(serverPlayer, this);
        broadcastChanges();
    }

    public boolean canExchange(Player player) { return player instanceof ServerPlayer; }

    @Override public int exchangeSlots() { return EXCHANGE_SLOTS; }
    @Override public ItemStack stackInSlot(int slot) { return container.getItem(slot); }
    @Override public void removeFromSlot(int slot, int amount) { container.removeItem(slot, amount); }
    @Override public ResourceKey<Level> exchangeDimension() { return exchangeDimension; }
    @Override public BlockPos exchangePos() { return exchangePos; }
    @Override public String accessGroup() { return accessGroup; }
}
