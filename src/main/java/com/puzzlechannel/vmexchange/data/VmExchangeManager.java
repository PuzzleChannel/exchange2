package com.puzzlechannel.vmexchange.data;

import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.menu.VmExchangeMenu;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import com.puzzlechannel.vmexchange.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.*;
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

    public void ensureCurrentWeek(MinecraftServer ignored) {
        String key = weekKey(ZonedDateTime.now());
        boolean resetOccurred = false;
        if (!key.equals(data.weekKey())) {
            resetWeek(false, null);
            resetOccurred = true;
        }

        VmExchangeConfig cfg = VmExchangeConfig.get();
        if (data.materials().size() != 4) {
            List<String> initial = sanitizeMaterials(cfg.currentMaterials);
            if (initial.size() == 4) setMaterials(initial, false);
            else resetWeek(false, null);
            resetOccurred = true;
        }

        // Migrate old saved selections so the new slot-role rules are enforced
        // without wiping the player's weekly progress.
        if (!matchesSlotRoles(data.materials(), cfg)) {
            List<String> migrated = migrateExistingMaterials(data.materials(), cfg);
            if (migrated.size() == 4) { data.setMaterials(migrated); resetOccurred = true; }
        }

        // Refresh still-open screens after a reset and close any whose exchange is
        // no longer a valid block or whose owner group access has changed.
        if (resetOccurred) refreshOpenMenus();
    }

    private boolean matchesSlotRoles(List<String> ids, VmExchangeConfig cfg) {
        if (ids.size() != 4) return false;
        if (ids.stream().distinct().count() != 4) return false;
        for (int i = 0; i < 3; i++) if (!cfg.enabledStandardMaterialIds().contains(ids.get(i))) return false;
        return cfg.enabledSpecialMaterialIds().contains(ids.get(3));
    }

    private List<String> migrateExistingMaterials(List<String> old, VmExchangeConfig cfg) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(3, old.size()); i++) {
            String id = old.get(i);
            if (cfg.enabledStandardMaterialIds().contains(id) && !result.contains(id)) result.add(id);
        }
        for (String id : cfg.enabledStandardMaterialIds()) {
            if (result.size() >= 3) break;
            if (!result.contains(id)) result.add(id);
        }
        if (result.size() != 3) return List.of();

        String fourth = old.size() > 3 ? old.get(3) : null;
        if (fourth == null || !cfg.enabledSpecialMaterialIds().contains(fourth) || result.contains(fourth)) {
            fourth = cfg.enabledSpecialMaterialIds().stream()
                    .filter(id -> !result.contains(id))
                    .findFirst().orElse(null);
        }
        if (fourth == null) return List.of();
        result.add(fourth);
        return result;
    }

    public void resetWeek(boolean forceRandom, Collection<String> forcedMaterials) {
        VmExchangeConfig cfg = VmExchangeConfig.get();
        List<String> next;
        if (forcedMaterials != null) next = sanitizeMaterials(forcedMaterials);
        else if (forceRandom || cfg.randomizeMaterials || data.materials().size() != 4) next = randomMaterials(cfg, 4);
        else next = sanitizeMaterials(cfg.currentMaterials);
        if (next.size() < 4) next = randomMaterials(cfg, 4);
        data.setMaterials(next);
        data.setWeekKey(weekKey(ZonedDateTime.now()));
        data.resetPlayers();
        refreshOpenMenus();
    }

    public void setMaterials(Collection<String> ids, boolean resetProgress) {
        List<String> clean = sanitizeMaterials(ids);
        if (clean.size() != 4) throw new IllegalArgumentException("Exactly 4 valid materials are required");
        data.setMaterials(clean);
        if (resetProgress) data.resetPlayers();
        refreshOpenMenus();
    }

    private List<String> sanitizeMaterials(Collection<String> ids) {
        List<String> raw = new ArrayList<>();
        for (String s : ids) {
            Identifier id = Identifier.tryParse(s);
            if (id == null) continue;
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null && !raw.contains(id.toString())) raw.add(id.toString());
        }
        if (raw.size() != 4) return raw.stream().limit(4).toList();

        VmExchangeConfig cfg = VmExchangeConfig.get();
        // Slots 1-3 use the standard pool; slot 4 uses the special pool.
        for (int i = 0; i < 3; i++) {
            if (!cfg.standardMaterialIds.contains(raw.get(i))) return List.of();
        }
        if (!cfg.isSpecialMaterial(raw.get(3))) return List.of();
        return raw;
    }

    private List<String> randomMaterials(VmExchangeConfig cfg, int count) {
        List<String> standard = new ArrayList<>(cfg.enabledStandardMaterialIds());
        List<String> special = new ArrayList<>(cfg.enabledSpecialMaterialIds());
        Collections.shuffle(standard, new Random());
        Collections.shuffle(special, new Random());

        List<String> result = new ArrayList<>(4);
        result.addAll(standard.stream().limit(Math.min(3, count - 1)).toList());
        for (String id : special) {
            if (!result.contains(id)) {
                result.add(id);
                break;
            }
        }

        if (result.size() < 4) {
            List<String> fallback = new ArrayList<>(cfg.enabledMaterialIds());
            Collections.shuffle(fallback, new Random());
            for (String id : fallback) {
                if (!result.contains(id)) result.add(id);
                if (result.size() == 4) break;
            }
        }
        return result.stream().limit(4).toList();
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

    public int currentPrice(ServerPlayer player) { return VmExchangeConfig.get().priceFor(state(player).weeklyEarned()); }
    public List<String> materials() { return data.materials(); }

    /** Returns the group bound to this exact block, dimension included. */
    public String exchangeGroup(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) return null;
        return data.exchangeGroup(dimension, pos);
    }

    public void bindGroup(ResourceKey<Level> dimension, BlockPos pos, String group) {
        data.setExchangeGroup(dimension, pos.immutable(), group.toLowerCase(Locale.ROOT));
    }

    public void unbindGroup(ResourceKey<Level> dimension, BlockPos pos) {
        data.removeExchangeGroup(dimension, pos);
    }

    public boolean hasExchangeAccess(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos) {
        String requiredGroup = exchangeGroup(dimension, pos);
        if (requiredGroup == null || requiredGroup.isBlank()) return false;
        return LuckPermsGroupAccess.playerHasGroup(player, requiredGroup);
    }

    private boolean isExchangeValid(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) return false;
        if (!player.level().dimension().equals(dimension)) return false;
        if (!player.level().getBlockState(pos).is(ModBlocks.EXCHANGE)) return false;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0D) return false;
        return hasExchangeAccess(player, dimension, pos);
    }

    public void refreshOpenMenus() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof VmExchangeMenu menu) {
                if (!menu.stillValid(player)) {
                    player.closeContainer();
                } else {
                    VmExchangeNetworking.sendSnapshot(player);
                }
            }
        }
    }

    private int reject(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
        return 0;
    }

    public int exchange(ServerPlayer player, VmExchangeMenuAccess menu) {
        ensureCurrentWeek(server);
        if (!isExchangeValid(player, menu.exchangeDimension(), menu.exchangePos())) {
            player.sendSystemMessage(Component.literal(
                    "Доступ к этому Обменнику закрыт: проверьте группу LuckPerms и расстояние до блока."));
            return 0;
        }

        VmExchangeConfig cfg = VmExchangeConfig.get();
        VmExchangeData.PlayerState ps = state(player);
        if (ps.weeklyEarned() >= cfg.weeklyLimit) return reject(player, "vmexchange.limit_reached");
        if (menu.exchangeSlots() != 4 || data.materials().size() != 4) return 0;

        Identifier coinId = Identifier.tryParse("vmtrade:vmcoin");
        if (coinId == null || !BuiltInRegistries.ITEM.containsKey(coinId)) return reject(player, "vmexchange.no_vmcoin");
        Item coin = BuiltInRegistries.ITEM.getOptional(coinId).orElse(null);
        if (coin == null) return reject(player, "vmexchange.no_vmcoin");
        if (!canFitCoins(player, coin, 1)) return reject(player, "vmexchange.no_space");

        // Exactly one coin per click; all four designated inputs must meet the current price.
        int price = Math.max(1, cfg.priceFor(ps.weeklyEarned()));
        String[] actual = new String[4];
        int[] counts = new int[4];
        for (int i = 0; i < 4; i++) {
            ItemStack stack = menu.stackInSlot(i);
            actual[i] = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            counts[i] = stack.getCount();
        }
        if (!ExchangeRules.hasPayment(ps.weeklyEarned(), cfg.weeklyLimit, price,
                data.materials().toArray(String[]::new), actual, counts)) {
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
            else if (ItemStack.isSameItemSameComponents(stack, reward)) free += Math.max(0, stack.getMaxStackSize() - stack.getCount());
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
    }
}
