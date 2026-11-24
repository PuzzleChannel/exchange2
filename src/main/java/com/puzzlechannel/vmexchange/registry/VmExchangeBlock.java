package com.puzzlechannel.vmexchange.registry;

import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class VmExchangeBlock extends Block {
    public VmExchangeBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        VmExchangeManager manager = VmExchangeManager.get(serverPlayer.level().getServer());
        String group = manager.groupForPlayer(serverPlayer);
        if (group == null) {
            serverPlayer.sendSystemMessage(Component.literal("Вы не можете использовать это.").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        manager.ensureCurrentWeek(serverPlayer.level().getServer());
        if (manager.materialsForGroup(group).size() != 4) {
            serverPlayer.sendSystemMessage(Component.literal("Для вашей страны пока не настроен набор материалов."));
            return InteractionResult.SUCCESS;
        }

        serverPlayer.openMenu(new SimpleMenuProvider(
                (syncId, inventory, p) -> new VmExchangeMenu(syncId, inventory, level.dimension(), pos, group),
                Component.translatable("container.vmexchange.exchange")));
        VmExchangeNetworking.sendSnapshot(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
