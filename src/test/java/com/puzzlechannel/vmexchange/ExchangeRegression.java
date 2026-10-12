package com.puzzlechannel.vmexchange;

import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.data.ExchangeRules;
import com.puzzlechannel.vmexchange.data.WeekSchedule;
import com.puzzlechannel.vmexchange.menu.ExchangeLayout;
import java.time.ZonedDateTime;

/** Dependency-free test runner: executes as part of Gradle check. */
public final class ExchangeRegression {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String[] ids = {"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:diamond", "minecraft:ender_pearl"};
        int[] enough = {25,25,25,25};
        check(ExchangeRules.hasPayment(24,25,25,ids,ids,enough), "25th coin accepts exactly 25 of all four resources");
        check(!ExchangeRules.hasPayment(25,25,25,ids,ids,enough), "No 26th coin");
        check(!ExchangeRules.hasPayment(0,25,0,ids,ids,enough), "No free exchange from a zero price");
        for (int slot = 0; slot < 4; slot++) {
            int[] missing = enough.clone(); missing[slot] = 24;
            check(!ExchangeRules.hasPayment(24,25,25,ids,ids,missing), "Any one underpaid slot blocks payment");
            String[] wrong = ids.clone(); wrong[slot] = "minecraft:dirt";
            check(!ExchangeRules.hasPayment(24,25,25,ids,wrong,enough), "Any one wrong resource blocks payment");
        }
        check(!ExchangeRules.hasPayment(0,25,1,new String[]{"a"},ids,enough), "Incomplete recipes are rejected");
        var cfg = new VmExchangeConfig();
        int[] expected = {1,1,1,1,1,5,5,5,5,5,10,10,10,10,10,15,15,15,15,15,20,20,20,20,25};
        for (int earned=0; earned<25; earned++) check(cfg.priceFor(earned)==expected[earned], "Default price of coin "+(earned+1));
        String before = WeekSchedule.key(ZonedDateTime.parse("2026-10-05T05:59:59+03:00[Europe/Minsk]"),"MONDAY",6,0);
        String at = WeekSchedule.key(ZonedDateTime.parse("2026-10-05T06:00:00+03:00[Europe/Minsk]"),"MONDAY",6,0);
        check(before.equals("2026-09-28@MONDAY"), "No early weekly reset");
        check(at.equals("2026-10-05@MONDAY"), "Reset at the exact boundary");
        check(WeekSchedule.key(ZonedDateTime.parse("2026-10-08T16:00:00Z"),null,0,0).equals("2026-10-05@MONDAY"), "Invalid reset day uses Monday");
        check(ExchangeLayout.HEIGHT <= 240, "Fits minimum vanilla scaled viewport height");
        for (int x : ExchangeLayout.INPUT_X) check(x >= 1 && x+17 < ExchangeLayout.WIDTH, "Input slots inside panel");
        check(ExchangeLayout.HOTBAR_Y+17 < ExchangeLayout.HEIGHT, "Hotbar inside panel");
        // Exercise the actual Minecraft slot insertion code, not a fake stack model.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // In 26.2 these prototypes are normally bound by the resource loader.
        net.minecraft.world.item.Items.ENDER_PEARL.builtInRegistryHolder().bindComponents(
                net.minecraft.core.component.DataComponentMap.builder()
                        .addAll(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS)
                        .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,16).build());
        net.minecraft.world.item.Items.DIRT.builtInRegistryHolder().bindComponents(
                net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        var deposits = new com.puzzlechannel.vmexchange.menu.ResourceDepositContainer(1);
        var pearlSlot = new com.puzzlechannel.vmexchange.menu.ResourceDepositSlot(deposits,0,0,0,stack -> stack.is(net.minecraft.world.item.Items.ENDER_PEARL));
        for (int i=0;i<4;i++) check(pearlSlot.safeInsert(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL,16)).isEmpty(), "Deposit accepts each 16-pearl stack");
        check(pearlSlot.getItem().getCount()==64, "Four pearl stacks accumulate to 64");
        check(pearlSlot.safeInsert(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL,16)).getCount()==16, "Deposit overflow remains on the cursor");
        check(pearlSlot.safeInsert(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT,16)).getCount()==16, "Wrong resource is not swallowed");
        check(pearlSlot.remove(25).getCount()==25 && pearlSlot.getItem().getCount()==39, "High-tier price removes exactly 25 pearls");
        System.out.println("PASS: " + assertions + " exchange regression assertions");
    }
}
