package com.puzzlechannel.vmexchange;

import com.puzzlechannel.vmexchange.command.VmExchangeCommands;
import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import com.puzzlechannel.vmexchange.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VmExchangeMod implements ModInitializer {
    public static final String MOD_ID = "vmexchange";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VmExchangeConfig.load();
        ModBlocks.initialize();
        ModMenus.initialize();
        VmExchangeNetworking.initialize();
        VmExchangeCommands.initialize();

        // A player may break an exchange only when they belong to its bound
        // LuckPerms group. An unbound block is protected from everyone.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!state.is(ModBlocks.EXCHANGE)) return true;
            if (!(player instanceof ServerPlayer serverPlayer)) return false;
            if (level.getServer() == null) return false;

            VmExchangeManager manager = VmExchangeManager.get(level.getServer());
            String group = manager.exchangeGroup(level.dimension(), pos);
            if (group == null || group.isBlank() || !manager.hasExchangeAccess(serverPlayer, level.dimension(), pos)) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Нельзя разрушить Обменник: требуется привязка и членство в его группе LuckPerms."));
                return false;
            }
            return true;
        });

        // Remove the binding immediately on a normal player break. The manager
        // also periodically cleans up blocks removed by commands or explosions.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (state.is(ModBlocks.EXCHANGE) && level.getServer() != null) {
                VmExchangeManager.get(level.getServer()).unbindGroup(level.dimension(), pos);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> VmExchangeManager.get(server).ensureCurrentWeek(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) VmExchangeManager.get(server).ensureCurrentWeek(server);
        });
    }
}
