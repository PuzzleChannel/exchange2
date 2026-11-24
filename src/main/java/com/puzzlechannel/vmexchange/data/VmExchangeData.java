package com.puzzlechannel.vmexchange.data;

import com.puzzlechannel.vmexchange.util.Ids;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class VmExchangeData extends SavedData {
    public static final Codec<PlayerState> PLAYER_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("weeklyEarned").forGetter(PlayerState::weeklyEarned),
            Codec.INT.fieldOf("tier").forGetter(PlayerState::tier)
    ).apply(i, PlayerState::new));

    public static final Codec<VmExchangeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("weekKey").forGetter(d -> d.weekKey),
            Codec.STRING.listOf().optionalFieldOf("materials", List.of()).forGetter(d -> List.of()),
            Codec.unboundedMap(Codec.STRING, PLAYER_CODEC).fieldOf("players").forGetter(d -> d.players),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                    .optionalFieldOf("groupMaterials", Map.of()).forGetter(d -> d.groupMaterials),
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .optionalFieldOf("exchangeGroups", Map.of()).forGetter(d -> Map.of())
    ).apply(i, (weekKey, legacyMaterials, players, groupMaterials, legacyBindings) ->
            new VmExchangeData(weekKey, new HashMap<>(players), new HashMap<>(groupMaterials))));

    @SuppressWarnings("DataFlowIssue")
    public static final SavedDataType<VmExchangeData> TYPE = new SavedDataType<>(
            Ids.id("exchange_data"),
            VmExchangeData::newEmpty,
            CODEC,
            null
    );

    private String weekKey;
    private final Map<String, PlayerState> players;
    private final Map<String, List<String>> groupMaterials;

    public record PlayerState(int weeklyEarned, int tier) {}

    public VmExchangeData() {
        this("", new HashMap<>(), new HashMap<>());
    }

    private VmExchangeData(String weekKey, Map<String, PlayerState> players,
                           Map<String, List<String>> groupMaterials) {
        this.weekKey = weekKey;
        this.players = players;
        this.groupMaterials = new HashMap<>();
        groupMaterials.forEach((group, ids) ->
                this.groupMaterials.put(group.toLowerCase(Locale.ROOT), new ArrayList<>(ids)));
    }

    public static VmExchangeData newEmpty() { return new VmExchangeData(); }

    public String weekKey() { return weekKey; }
    public void setWeekKey(String key) { weekKey = key; setDirty(); }

    public List<String> materialsForGroup(String group) {
        if (group == null) return List.of();
        List<String> values = groupMaterials.get(group.toLowerCase(Locale.ROOT));
        return values == null ? List.of() : List.copyOf(values);
    }

    public Map<String, List<String>> groupMaterials() {
        Map<String, List<String>> copy = new HashMap<>();
        groupMaterials.forEach((group, ids) -> copy.put(group, List.copyOf(ids)));
        return Collections.unmodifiableMap(copy);
    }

    public void setGroupMaterials(String group, Collection<String> ids) {
        if (group == null) return;
        groupMaterials.put(group.toLowerCase(Locale.ROOT), new ArrayList<>(ids.stream().limit(4).toList()));
        setDirty();
    }

    public Map<String, PlayerState> players() { return players; }
    public PlayerState player(UUID uuid) {
        return players.getOrDefault(uuid.toString(), new PlayerState(0, 0));
    }

    public void setPlayer(UUID uuid, int earned, int tier) {
        players.put(uuid.toString(), new PlayerState(Math.max(0, earned), Math.max(0, tier)));
        setDirty();
    }

    public void resetPlayers() {
        players.clear();
        setDirty();
    }
}
