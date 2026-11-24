package com.puzzlechannel.vmexchange.data;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Shared, side-effect-free validation before the server consumes a single item. */
public final class ExchangeRules {
    private static final Set<String> COUNTRY_GROUPS = Set.of("europe", "africa", "avstralia");

    public static boolean hasPayment(int earned, int limit, int price, String[] required, String[] actual, int[] counts) {
        if (earned < 0 || limit < 1 || earned >= limit || price < 1) return false;
        if (required.length != 4 || actual.length != 4 || counts.length != 4) return false;
        for (int i = 0; i < 4; i++) {
            if (required[i] == null || required[i].isBlank() || !required[i].equals(actual[i]) || counts[i] < price) return false;
        }
        return true;
    }

    public static boolean isSupportedCountryGroup(String group) {
        return group != null && COUNTRY_GROUPS.contains(group.toLowerCase(Locale.ROOT));
    }

    public static boolean areCountryMaterialSetsDistinct(Map<String, List<String>> sets) {
        if (sets == null || !sets.keySet().containsAll(COUNTRY_GROUPS)) return false;
        Set<List<String>> unique = new HashSet<>();
        for (String group : COUNTRY_GROUPS) {
            List<String> materials = sets.get(group);
            if (materials == null || materials.size() != 4 || materials.stream().distinct().count() != 4) return false;
            if (!unique.add(materials.stream().sorted().toList())) return false;
        }
        return true;
    }

    private ExchangeRules() {}
}
