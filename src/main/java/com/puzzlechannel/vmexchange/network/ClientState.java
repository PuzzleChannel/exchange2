package com.puzzlechannel.vmexchange.network;

import net.minecraft.core.BlockPos;

public final class ClientState {
    private static volatile VmExchangeNetworking.SnapshotPayload snapshot = new VmExchangeNetworking.SnapshotPayload("", "", "", "", 0, 25, 1, 1);
    private static volatile VmExchangeNetworking.TargetHintPayload targetHint =
            new VmExchangeNetworking.TargetHintPayload(false, 0, 0, 0);

    public static void reset() {
        snapshot = new VmExchangeNetworking.SnapshotPayload("", "", "", "", 0, 25, 1, 1);
        targetHint = new VmExchangeNetworking.TargetHintPayload(false, 0, 0, 0);
    }

    public static void set(VmExchangeNetworking.SnapshotPayload value) { snapshot = value; }
    public static VmExchangeNetworking.SnapshotPayload get() { return snapshot; }
    public static void setTargetHint(VmExchangeNetworking.TargetHintPayload value) { targetHint = value; }

    public static boolean isForeignExchangeHintAt(BlockPos pos) {
        VmExchangeNetworking.TargetHintPayload hint = targetHint;
        return hint.show() && hint.x() == pos.getX() && hint.y() == pos.getY() && hint.z() == pos.getZ();
    }

    private ClientState() {}
}
