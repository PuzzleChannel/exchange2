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
import net.minecraft.world.phys.BlockHitResult;
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

        // Only creative-mode players may break an exchange. Survival/adventure
        // players cannot break it even if they belong to the assigned group.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!state.is(ModBlocks.EXCHANGE)) return true;
            if (player.getAbilities().instabuild) return true;
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "Обменник нельзя разрушить в режиме выживания или приключения. Для разрушения нужен творческий режим."));
            }
            return false;
        });

        // Clear the saved group binding only after a successful creative-mode break.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (state.is(ModBlocks.EXCHANGE) && level.getServer() != null) {
                VmExchangeManager.get(level.getServer()).unbindGroup(level.dimension(), pos);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> VmExchangeManager.get(server).ensureCurrentWeek(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            if (tick % 20 == 0) VmExchangeManager.get(server).ensureCurrentWeek(server);

            // Re-check the block under each player's crosshair 10 times per second.
            // LuckPerms is evaluated server-side; only the result is sent to the client.
            if (tick % 2 == 0) {
                VmExchangeManager manager = VmExchangeManager.get(server);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    BlockPos targetPos = null;
                    boolean showForeignExchangeHint = false;

                    if (player.pick(8.0D, 0.0F, false) instanceof BlockHitResult target
                            && player.level().getBlockState(target.getBlockPos()).is(ModBlocks.EXCHANGE)) {
                        targetPos = target.getBlockPos();
                        showForeignExchangeHint = !manager.hasExchangeAccess(
                                player, player.level().dimension(), targetPos);
                    }

                    VmExchangeNetworking.updateTargetHint(player, targetPos, showForeignExchangeHint);
                }
            }
        });
    }
}
