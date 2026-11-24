package com.puzzlechannel.vmexchange.data;

import net.luckperms.api.LuckPermsProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the player's country from LuckPerms. Exchange blocks do not store group bindings.
 * The primary group wins; inherited supported country groups are a fallback.
 */
public final class LuckPermsGroupAccess {
    public static final List<String> SUPPORTED_GROUPS = List.of("europe", "africa", "avstralia");

    public static String findSupportedGroup(ServerPlayer player) {
        try {
            var user = LuckPermsProvider.get().getUserManager().getUser(player.getUUID());
            if (user == null) return null;

            String primary = user.getPrimaryGroup();
            for (String supported : SUPPORTED_GROUPS) {
                if (supported.equalsIgnoreCase(primary)) return supported;
            }

            var inherited = user.getInheritedGroups(user.getQueryOptions());
            for (String supported : SUPPORTED_GROUPS) {
                if (inherited.stream().anyMatch(group ->
                        group.getName().toLowerCase(Locale.ROOT).equals(supported))) {
                    return supported;
                }
            }
            return null;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    public static boolean isSupportedGroup(String groupName) {
        return groupName != null && SUPPORTED_GROUPS.stream()
                .anyMatch(group -> group.equalsIgnoreCase(groupName));
    }

    private LuckPermsGroupAccess() {}
}
