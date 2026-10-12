package com.puzzlechannel.vmexchange.data;

/** Shared, side-effect-free validation before the server consumes a single item. */
public final class ExchangeRules {
    public static boolean hasPayment(int earned, int limit, int price, String[] required, String[] actual, int[] counts) {
        if (earned < 0 || limit < 1 || earned >= limit || price < 1) return false;
        if (required.length != 4 || actual.length != 4 || counts.length != 4) return false;
        for (int i = 0; i < 4; i++) {
            if (required[i] == null || required[i].isBlank() || !required[i].equals(actual[i]) || counts[i] < price) return false;
        }
        return true;
    }
    private ExchangeRules() {}
}
