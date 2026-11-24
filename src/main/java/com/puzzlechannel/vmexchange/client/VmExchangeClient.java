package com.puzzlechannel.vmexchange.client;

import com.puzzlechannel.vmexchange.network.ClientState;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.client.screen.VmExchangeScreen;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import com.puzzlechannel.vmexchange.util.Ids;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.BlockHitResult;

public final class VmExchangeClient implements ClientModInitializer {
    private static final String ACCESS_WARNING = "Вы не можете использовать это.";

    @Override
    public void onInitializeClient() {
        VmExchangeNetworking.initializeClient();
        MenuScreens.register(ModMenus.EXCHANGE, VmExchangeScreen::new);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Ids.id("exchange_access_warning"),
                VmExchangeClient::renderAccessWarning);
    }

    private static void renderAccessWarning(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.screen() != null) return;
        if (!(client.hitResult instanceof BlockHitResult hit)) return;

        var pos = hit.getBlockPos();
        if (!client.level.getBlockState(pos).is(ModBlocks.EXCHANGE)) return;
        if (!ClientState.isAccessWarningAt(pos)) return;

        int x = graphics.guiWidth() / 2 - client.font.width(ACCESS_WARNING) / 2;
        int y = graphics.guiHeight() / 2 + 14;
        graphics.text(client.font, ACCESS_WARNING, x, y, 0xFFFF5555, true);
    }
}
