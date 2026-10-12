package com.puzzlechannel.vmexchange.network;

import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.util.Ids;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;


public final class VmExchangeNetworking {
    public record ExchangeRequestPayload() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<ExchangeRequestPayload> TYPE = new Type<>(Ids.id("exchange_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeRequestPayload> CODEC = StreamCodec.of(
                (buf, value) -> {}, buf -> new ExchangeRequestPayload());
        @Override public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() { return TYPE; }
    }

    public record SnapshotPayload(String m1, String m2, String m3, String m4, int earned, int limit, int price, int nextPrice)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<SnapshotPayload> TYPE = new Type<>(Ids.id("snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SnapshotPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SnapshotPayload::m1,
                ByteBufCodecs.STRING_UTF8, SnapshotPayload::m2,
                ByteBufCodecs.STRING_UTF8, SnapshotPayload::m3,
                ByteBufCodecs.STRING_UTF8, SnapshotPayload::m4,
                ByteBufCodecs.VAR_INT, SnapshotPayload::earned,
                ByteBufCodecs.VAR_INT, SnapshotPayload::limit,
                ByteBufCodecs.VAR_INT, SnapshotPayload::price,
                ByteBufCodecs.VAR_INT, SnapshotPayload::nextPrice,
                SnapshotPayload::new);
        @Override public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Server-authoritative hint for the exact exchange block currently under the player's crosshair.
     * Coordinates let the client avoid showing a stale warning after the player looks elsewhere.
     */
    public record TargetHintPayload(boolean show, int x, int y, int z)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<TargetHintPayload> TYPE = new Type<>(Ids.id("target_hint"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TargetHintPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, TargetHintPayload::show,
                ByteBufCodecs.VAR_INT, TargetHintPayload::x,
                ByteBufCodecs.VAR_INT, TargetHintPayload::y,
                ByteBufCodecs.VAR_INT, TargetHintPayload::z,
                TargetHintPayload::new);
        @Override public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<ServerPlayer, TargetHintPayload> LAST_TARGET_HINT = new WeakHashMap<>();

    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(ExchangeRequestPayload.TYPE, ExchangeRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SnapshotPayload.TYPE, SnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TargetHintPayload.TYPE, TargetHintPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ExchangeRequestPayload.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof VmExchangeMenu menu) {
                menu.performExchange(context.player());
                sendSnapshot(context.player());
            }
        });
    }

    public static void initializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SnapshotPayload.TYPE, (payload, context) -> ClientState.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(TargetHintPayload.TYPE, (payload, context) -> ClientState.setTargetHint(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
    }

    /**
     * Send only when the target/permission state changes, avoiding a packet every server tick.
     */
    public static void updateTargetHint(ServerPlayer player, BlockPos pos, boolean show) {
        TargetHintPayload next = new TargetHintPayload(
                show,
                show && pos != null ? pos.getX() : 0,
                show && pos != null ? pos.getY() : 0,
                show && pos != null ? pos.getZ() : 0);
        TargetHintPayload previous = LAST_TARGET_HINT.get(player);
        if (next.equals(previous)) return;
        LAST_TARGET_HINT.put(player, next);
        ServerPlayNetworking.send(player, next);
    }

    public static void sendSnapshot(ServerPlayer player) {
        var manager = VmExchangeManager.get(player.level().getServer());
        var state = manager.state(player);
        var mats = manager.materials();
        String m1 = mats.size() > 0 ? mats.get(0) : "";
        String m2 = mats.size() > 1 ? mats.get(1) : "";
        String m3 = mats.size() > 2 ? mats.get(2) : "";
        String m4 = mats.size() > 3 ? mats.get(3) : "";
        int price = manager.currentPrice(player);
        int next = price;
        var cfg = com.puzzlechannel.vmexchange.config.VmExchangeConfig.get();
        if (state.weeklyEarned() < cfg.weeklyLimit) next = cfg.priceFor(Math.min(cfg.weeklyLimit - 1, state.weeklyEarned() + 1));
        ServerPlayNetworking.send(player, new SnapshotPayload(m1, m2, m3, m4, state.weeklyEarned(), cfg.weeklyLimit, price, next));
    }

    private VmExchangeNetworking() {}
}
