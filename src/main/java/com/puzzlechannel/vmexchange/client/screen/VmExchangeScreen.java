package com.puzzlechannel.vmexchange.client.screen;

import com.puzzlechannel.vmexchange.menu.ExchangeLayout;
import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.network.ClientState;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** A compact, pixel-aligned trading counter. Real inventory slots stay vanilla. */
public final class VmExchangeScreen extends AbstractContainerScreen<VmExchangeMenu> {
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath("vmexchange", "textures/gui/exchange.png");
    private static final String COIN = "vmtrade:vmcoin";
    private Button exchangeButton;
    private boolean initialized;
    private int lastEarned;
    private long successUntil;
    private long pendingSince;
    private VmExchangeNetworking.SnapshotPayload sentState;

    public VmExchangeScreen(VmExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ExchangeLayout.WIDTH, ExchangeLayout.HEIGHT);
        titleLabelX = inventoryLabelX = -1000;
        titleLabelY = inventoryLabelY = -1000;
        // A snapshot from the previous menu/session must never enable this menu.
        ClientState.reset();
    }

    @Override
    protected void init() {
        super.init();
        exchangeButton = addRenderableWidget(Button.builder(Component.translatable("vmexchange.exchange"), button -> {
            if (canExchange(ClientState.get())) {
                sentState = ClientState.get();
                pendingSince = System.nanoTime();
                button.active = false;
                ClientPlayNetworking.send(new VmExchangeNetworking.ExchangeRequestPayload());
            }
        }).bounds(leftPos + ExchangeLayout.BUTTON_X, topPos + ExchangeLayout.BUTTON_Y,
                ExchangeLayout.BUTTON_WIDTH, ExchangeLayout.BUTTON_HEIGHT).build());
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        var state = ClientState.get();
        long now = System.nanoTime();
        if (sentState != null && (state != sentState || now - pendingSince > 2_000_000_000L)) sentState = null;
        if (!initialized && hasSnapshot(state)) {
            initialized = true;
            lastEarned = state.earned();
        } else if (initialized && state.earned() != lastEarned) {
            if (state.earned() > lastEarned) {
                successUntil = now + 1_200_000_000L;
                if (minecraft != null && minecraft.player != null)
                    minecraft.player.playSound(SoundEvents.VILLAGER_TRADE, 0.8F, 1.08F);
            }
            lastEarned = state.earned();
        }
        updateButton();
    }

    private void updateButton() {
        if (exchangeButton == null) return;
        exchangeButton.active = sentState == null && canExchange(ClientState.get());
        exchangeButton.setMessage(Component.translatable(sentState == null ? "vmexchange.exchange" : "vmexchange.waiting"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos, 0F, 0F,
                ExchangeLayout.WIDTH, ExchangeLayout.HEIGHT, 256, 256);
        var state = ClientState.get();
        for (int i = 0; i < 4; i++) {
            int x = leftPos + ExchangeLayout.INPUT_X[i];
            int y = topPos + ExchangeLayout.INPUT_Y;
            ItemStack stack = menu.stackInSlot(i);
            if (stack.isEmpty()) ghost(graphics, material(state, i), x, y);
            if (correct(stack, material(state, i)) && stack.getCount() >= state.price()) {
                graphics.fill(x - 1, y - 1, x + 17, y, 0xFF8EB956);
                graphics.fill(x - 1, y, x, y + 17, 0xFF8EB956);
            }
        }
        int x = leftPos + ExchangeLayout.REWARD_X;
        int y = topPos + ExchangeLayout.REWARD_Y;
        boolean funded = hasRequiredResources(state);
        // The reward well stays gray until every input covers the current price.
        graphics.fill(x - 3, y - 3, x + 19, y + 19, funded ? 0xFF786034 : 0xFF565656);
        graphics.fill(x - 3, y - 3, x + 18, y + 18, funded ? 0xFFF5D98C : 0xFFD0D0D0);
        graphics.fill(x - 2, y - 2, x + 18, y + 18, funded ? 0xFFB5A16F : 0xFF9A9A9A);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFE4DFD0);
        graphics.fill(x - 1, y - 1, x + 16, y + 16, 0xFF534F48);
        graphics.fill(x, y, x + 16, y + 16, funded ? 0xFF8B8375 : 0xFF858585);
        ItemStack coin = item(COIN);
        if (!coin.isEmpty()) {
            graphics.item(coin, x, y);
            if (!funded) graphics.fill(x, y, x + 16, y + 16, 0x997D7D7D);
        }
        else {
            // Missing dependency is visible, never represented as a fake usable coin.
            graphics.text(font, Component.literal("?"), x + 5, y + 4, 0xFFF0CC72, false);
        }
        int filled = (int) (190L * Math.max(0, Math.min(state.earned(), state.limit())) / Math.max(1, state.limit()));
        graphics.fill(leftPos + 17, topPos + 143, leftPos + 17 + filled, topPos + 147, 0xFF657F39);
        graphics.fill(leftPos + 17, topPos + 143, leftPos + 17 + filled, topPos + 144, 0xFFA7C76A);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Container labels are translated to leftPos/topPos by the vanilla screen.
        var state = ClientState.get();
        center(graphics, Component.translatable("container.vmexchange.exchange"), 11, 0xFFF3D58D, 112);
        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.stackInSlot(i);
            int have = correct(stack, material(state, i)) ? stack.getCount() : 0;
            boolean enough = have >= state.price();
            center(graphics, Component.literal(have + "/" + Math.max(1, state.price())), 65,
                    enough ? 0xFF45632B : 0xFF76513A, ExchangeLayout.INPUT_X[i] + 8);
        }
        Component rewardLabel = Component.translatable("vmexchange.reward");
        graphics.text(font, rewardLabel, ExchangeLayout.REWARD_X - 8 - font.width(rewardLabel), 88, 0xFF655136, false);
        graphics.text(font, Component.translatable("vmexchange.one_coin"), ExchangeLayout.REWARD_X + 24, 88, 0xFF655136, false);
        center(graphics, status(state), 130, statusColor(state), 112);
        // The exact numbers sit beside the bar in its hover tooltip, keeping the UI compact.
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        var state = ClientState.get();
        for (int i = 0; i < 4; i++) {
            if (menu.stackInSlot(i).isEmpty() && inside(mouseX, mouseY, ExchangeLayout.INPUT_X[i] - 1, ExchangeLayout.INPUT_Y - 1, 18, 18)) {
                ItemStack required = item(material(state, i));
                Component name = required.isEmpty() ? Component.translatable("vmexchange.loading") : required.getHoverName();
                graphics.setTooltipForNextFrame(font, Component.translatable("vmexchange.slot_hint", name, state.price()), mouseX, mouseY);
            }
        }
        if (inside(mouseX, mouseY, 16, 141, 192, 8)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("vmexchange.week_hint", state.earned(), state.limit()), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, ExchangeLayout.REWARD_X - 1, ExchangeLayout.REWARD_Y - 1, 18, 18)) {
            graphics.setTooltipForNextFrame(font, Component.translatable("vmexchange.reward_hint", state.price(), state.nextPrice()), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, ExchangeLayout.BUTTON_X, ExchangeLayout.BUTTON_Y, ExchangeLayout.BUTTON_WIDTH, ExchangeLayout.BUTTON_HEIGHT)) {
            graphics.setTooltipForNextFrame(font, Component.translatable(canExchange(state) ? "vmexchange.exchange_hint" : reason(state)), mouseX, mouseY);
        }
    }

    private Component status(VmExchangeNetworking.SnapshotPayload s) {
        if (!hasSnapshot(s)) return Component.translatable("vmexchange.loading");
        if (s.earned() >= s.limit()) return Component.translatable("vmexchange.limit_finished");
        if (System.nanoTime() < successUntil) return Component.translatable("vmexchange.success");
        return Component.translatable("vmexchange.received_short", s.earned(), s.limit());
    }

    private int statusColor(VmExchangeNetworking.SnapshotPayload s) {
        return s.earned() >= s.limit() ? 0xFF923D32 : System.nanoTime() < successUntil ? 0xFF45632B : 0xFF504537;
    }

    private boolean hasSnapshot(VmExchangeNetworking.SnapshotPayload s) {
        return !s.m1().isBlank() && !s.m2().isBlank() && !s.m3().isBlank() && !s.m4().isBlank();
    }

    private boolean hasRequiredResources(VmExchangeNetworking.SnapshotPayload s) {
        if (!hasSnapshot(s) || s.price() < 1) return false;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.stackInSlot(i);
            if (!correct(stack, material(s, i)) || stack.getCount() < s.price()) return false;
        }
        return true;
    }

    private String reason(VmExchangeNetworking.SnapshotPayload s) {
        if (!hasSnapshot(s)) return "vmexchange.loading";
        if (s.earned() >= s.limit()) return "vmexchange.next_week";
        if (item(COIN).isEmpty()) return "vmexchange.no_vmcoin";
        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.stackInSlot(i);
            if (!correct(stack, material(s, i)) || stack.getCount() < s.price()) return "vmexchange.missing_resources";
        }
        if (!hasCoinSpace()) return "vmexchange.no_space";
        return "vmexchange.exchange_hint";
    }

    private boolean canExchange(VmExchangeNetworking.SnapshotPayload s) {
        return "vmexchange.exchange_hint".equals(reason(s));
    }

    private boolean hasCoinSpace() {
        if (minecraft == null || minecraft.player == null) return false;
        ItemStack coin = item(COIN);
        if (coin.isEmpty()) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            if (stack.isEmpty() || (ItemStack.isSameItemSameComponents(stack, coin) && stack.getCount() < stack.getMaxStackSize())) return true;
        }
        return false;
    }

    private boolean correct(ItemStack stack, String id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
    }

    private String material(VmExchangeNetworking.SnapshotPayload s, int i) {
        return switch (i) { case 0 -> s.m1(); case 1 -> s.m2(); case 2 -> s.m3(); default -> s.m4(); };
    }

    private ItemStack item(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return ItemStack.EMPTY;
        var value = BuiltInRegistries.ITEM.getOptional(key).orElse(null);
        return value == null ? ItemStack.EMPTY : new ItemStack(value);
    }

    private void ghost(GuiGraphicsExtractor graphics, String id, int x, int y) {
        ItemStack stack = item(id);
        if (stack.isEmpty()) return;
        graphics.item(stack, x, y);
        graphics.fill(x, y, x + 16, y + 16, 0x887B7569);
    }

    private void center(GuiGraphicsExtractor graphics, Component text, int y, int color, int x) {
        graphics.text(font, text, x - font.width(text) / 2, y, color, false);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h;
    }
}
