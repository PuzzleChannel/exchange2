package com.puzzlechannel.vmexchange.data;

import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.ZonedDateTime;
import java.util.*;

public final class VmExchangeManager {
    private static final Map<MinecraftServer, VmExchangeManager> INSTANCES = new WeakHashMap<>();
    private final MinecraftServer server;
    private final VmExchangeData data;

    private VmExchangeManager(MinecraftServer server) {
        this.server = server;
        this.data = server.overworld().getDataStorage().computeIfAbsent(VmExchangeData.TYPE);
    }

    public static synchronized VmExchangeManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, VmExchangeManager::new);
    }

    public VmExchangeData data() { return data; }

    public static List<String> supportedGroups() { return LuckPermsGroupAccess.SUPPORTED_GROUPS; }
    public static boolean isSupportedGroup(String group) { return LuckPermsGroupAccess.isSupportedGroup(group); }

    public String groupForPlayer(ServerPlayer player) {
        return LuckPermsGroupAccess.findSupportedGroup(player);
    }

    public boolean hasExchangeAccess(ServerPlayer player) {
        return groupForPlayer(player) != null;
    }

    public void ensureCurrentWeek(MinecraftServer ignored) {
        String currentWeek = weekKey(ZonedDateTime.now());
        if (!currentWeek.equals(data.weekKey())) {
            resetWeek();
            return;
        }

        VmExchangeConfig cfg = VmExchangeConfig.get();
        Set<String> usedSets = new HashSet<>();
        boolean changed = false;
        for (String group : supportedGroups()) {
            List<String> existing = data.materialsForGroup(group);
            if (matchesSlotRoles(existing, cfg) && usedSets.add(materialKey(existing))) continue;

            List<String> next = randomMaterials(cfg, usedSets);
            if (next.size() != 4) {
                throw new IllegalStateException("Не удалось подобрать 4 материала для группы LuckPerms " + group);
            }
            data.setGroupMaterials(group, next);
            usedSets.add(materialKey(next));
            changed = true;
        }

        if (changed) refreshOpenMenus();
    }

    public void resetWeek() {
        VmExchangeConfig cfg = VmExchangeConfig.get();
        Set<String> usedSets = new HashSet<>();
        for (String group : supportedGroups()) {
            List<String> next = randomMaterials(cfg, usedSets);
            if (next.size() != 4) {
                throw new IllegalStateException("Пулы материалов должны содержать минимум 3 обычных и 1 специальный материал.");
            }
            data.setGroupMaterials(group, next);
            usedSets.add(materialKey(next));
        }
        data.setWeekKey(weekKey(ZonedDateTime.now()));
        data.resetPlayers();
        refreshOpenMenus();
    }

    public void setMaterials(String group, Collection<String> ids, boolean resetProgress) {
        String normalizedGroup = normalizeSupportedGroup(group);
        List<String> clean = sanitizeMaterials(ids);
        if (clean.size() != 4) {
            throw new IllegalArgumentException("Нужно указать 4 существующих материала: первые три из обычного пула, четвёртый из специального.");
        }
        for (String other : supportedGroups()) {
            if (other.equals(normalizedGroup)) continue;
            if (materialKey(data.materialsForGroup(other)).equals(materialKey(clean))) {
                throw new IllegalArgumentException("Этот набор уже используется другой страной. У каждой страны должен быть свой набор.");
            }
        }
        data.setGroupMaterials(normalizedGroup, clean);
        if (resetProgress) data.resetPlayers();
        refreshOpenMenus();
    }

    private String normalizeSupportedGroup(String group) {
        if (!isSupportedGroup(group)) {
            throw new IllegalArgumentException("Поддерживаются только группы LuckPerms: europe, africa, avstralia.");
        }
        return group.toLowerCase(Locale.ROOT);
    }

    private boolean matchesSlotRoles(List<String> ids, VmExchangeConfig cfg) {
        if (ids.size() != 4 || ids.stream().distinct().count() != 4) return false;
        for (int i = 0; i < 3; i++) {
            if (!cfg.enabledStandardMaterialIds().contains(ids.get(i))) return false;
        }
        return cfg.enabledSpecialMaterialIds().contains(ids.get(3));
    }

    private List<String> sanitizeMaterials(Collection<String> ids) {
        List<String> raw = new ArrayList<>();
        for (String value : ids) {
            Identifier id = Identifier.tryParse(value);
            if (id == null) continue;
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null && !raw.contains(id.toString())) raw.add(id.toString());
        }
        if (raw.size() != 4) return List.of();

        VmExchangeConfig cfg = VmExchangeConfig.get();
        return matchesSlotRoles(raw, cfg) ? List.copyOf(raw) : List.of();
    }

    private List<String> randomMaterials(VmExchangeConfig cfg, Set<String> alreadyUsed) {
        List<String> standard = new ArrayList<>(cfg.enabledStandardMaterialIds());
        List<String> special = new ArrayList<>(cfg.enabledSpecialMaterialIds());
        if (standard.size() < 3 || special.isEmpty()) return List.of();

        List<String> lastCandidate = List.of();
        for (int attempt = 0; attempt < 128; attempt++) {
            Collections.shuffle(standard);
            Collections.shuffle(special);
            List<String> candidate = new ArrayList<>(standard.subList(0, 3));
            String fourth = special.stream().filter(id -> !candidate.contains(id)).findFirst().orElse(null);
            if (fourth == null) continue;
            candidate.add(fourth);
            lastCandidate = List.copyOf(candidate);
            if (!alreadyUsed.contains(materialKey(candidate))) return lastCandidate;
        }
        return lastCandidate;
    }

    private String materialKey(List<String> ids) {
        return ids == null ? "" : String.join("|", ids);
    }

    public VmExchangeData.PlayerState state(ServerPlayer player) {
        VmExchangeData.PlayerState state = data.player(player.getUUID());
        int tier = VmExchangeConfig.get().tierFor(state.weeklyEarned());
        if (tier != state.tier()) {
            data.setPlayer(player.getUUID(), state.weeklyEarned(), tier);
            return new VmExchangeData.PlayerState(state.weeklyEarned(), tier);
        }
        return state;
    }

    public int currentPrice(ServerPlayer player) {
        return VmExchangeConfig.get().priceFor(state(player).weeklyEarned());
    }

    public List<String> materials(ServerPlayer player) {
        return materialsForGroup(groupForPlayer(player));
    }

    public List<String> materialsForGroup(String group) {
        if (!isSupportedGroup(group)) return List.of();
        return data.materialsForGroup(group);
    }

    private boolean isExchangeValid(ServerPlayer player, ResourceKey<Level> dimension,
                                    BlockPos pos, String openedForGroup) {
        if (dimension == null || pos == null || !isSupportedGroup(openedForGroup)) return false;
        if (!player.level().dimension().equals(dimension)) return false;
        if (!player.level().getBlockState(pos).is(ModBlocks.EXCHANGE)) return false;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) return false;
        String currentGroup = groupForPlayer(player);
        return currentGroup != null && currentGroup.equalsIgnoreCase(openedForGroup);
    }

    public void refreshOpenMenus() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof VmExchangeMenu menu) {
                if (!menu.stillValid(player)) player.closeContainer();
                else VmExchangeNetworking.sendSnapshot(player);
            }
        }
    }

    private int reject(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
        return 0;
    }

    public int exchange(ServerPlayer player, VmExchangeMenuAccess menu) {
        ensureCurrentWeek(server);
        if (!isExchangeValid(player, menu.exchangeDimension(), menu.exchangePos(), menu.accessGroup())) {
            player.sendSystemMessage(Component.literal("Вы не можете использовать это."));
            return 0;
        }

        String group = groupForPlayer(player);
        List<String> requiredMaterials = materialsForGroup(group);
        VmExchangeConfig cfg = VmExchangeConfig.get();
        VmExchangeData.PlayerState ps = state(player);
        if (ps.weeklyEarned() >= cfg.weeklyLimit) return reject(player, "vmexchange.limit_reached");
        if (menu.exchangeSlots() != 4 || requiredMaterials.size() != 4) return 0;

        Identifier coinId = Identifier.tryParse("vmtrade:vmcoin");
        Item coin = coinId == null ? null : BuiltInRegistries.ITEM.getOptional(coinId).orElse(null);
        if (coin == null) return reject(player, "vmexchange.no_vmcoin");
        if (!canFitCoins(player, coin, 1)) return reject(player, "vmexchange.no_space");

        int price = Math.max(1, cfg.priceFor(ps.weeklyEarned()));
        String[] actual = new String[4];
        int[] counts = new int[4];
        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.stackInSlot(i);
            actual[i] = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts[i] = stack.getCount();
        }
        if (!ExchangeRules.hasPayment(ps.weeklyEarned(), cfg.weeklyLimit, price,
                requiredMaterials.toArray(String[]::new), actual, counts)) {
            return reject(player, "vmexchange.missing_resources");
        }

        for (int i = 0; i < 4; i++) menu.removeFromSlot(i, price);
        player.getInventory().placeItemBackInInventory(new ItemStack(coin, 1));
        data.setPlayer(player.getUUID(), ps.weeklyEarned() + 1, cfg.tierFor(ps.weeklyEarned() + 1));
        return 1;
    }

    private boolean canFitCoins(ServerPlayer player, Item coin, int count) {
        int free = 0;
        ItemStack reward = new ItemStack(coin);
        int max = reward.getMaxStackSize();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) free += max;
            else if (ItemStack.isSameItemSameComponents(stack, reward)) {
                free += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
            if (free >= count) return true;
        }
        return false;
    }

    public String weekKey(ZonedDateTime now) {
        VmExchangeConfig cfg = VmExchangeConfig.get();
        return WeekSchedule.key(now, cfg.reset.day, cfg.reset.hour, cfg.reset.minute);
    }

    public interface VmExchangeMenuAccess {
        int exchangeSlots();
        ItemStack stackInSlot(int slot);
        void removeFromSlot(int slot, int amount);
        ResourceKey<Level> exchangeDimension();
        BlockPos exchangePos();
        String accessGroup();
    }
}
