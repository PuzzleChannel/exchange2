package com.puzzlechannel.vmexchange.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.puzzlechannel.vmexchange.config.VmExchangeConfig;
import com.puzzlechannel.vmexchange.data.VmExchangeManager;
import com.puzzlechannel.vmexchange.network.VmExchangeNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public final class VmExchangeCommands {
    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            var root = Commands.literal("vmexchange").requires(Commands.hasPermission(Commands.LEVEL_ADMINS));

            root.then(Commands.literal("info")
                    .executes(ctx -> info(ctx.getSource().getPlayerOrException())));

            root.then(Commands.literal("setmaterial")
                    .then(Commands.argument("group", StringArgumentType.word())
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1, 4))
                                    .then(Commands.argument("item", StringArgumentType.word())
                                            .executes(ctx -> setMaterial(
                                                    ctx.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(ctx, "group"),
                                                    IntegerArgumentType.getInteger(ctx, "slot"),
                                                    StringArgumentType.getString(ctx, "item")))))));

            root.then(Commands.literal("setmaterials")
                    .then(Commands.argument("group", StringArgumentType.word())
                            .then(Commands.argument("item1", StringArgumentType.word())
                                    .then(Commands.argument("item2", StringArgumentType.word())
                                            .then(Commands.argument("item3", StringArgumentType.word())
                                                    .then(Commands.argument("item4", StringArgumentType.word())
                                                            .executes(ctx -> setMaterials(
                                                                    ctx.getSource().getPlayerOrException(),
                                                                    StringArgumentType.getString(ctx, "group"),
                                                                    List.of(
                                                                            StringArgumentType.getString(ctx, "item1"),
                                                                            StringArgumentType.getString(ctx, "item2"),
                                                                            StringArgumentType.getString(ctx, "item3"),
                                                                            StringArgumentType.getString(ctx, "item4"))))))))));

            root.then(Commands.literal("randomize").executes(ctx -> {
                VmExchangeManager.get(ctx.getSource().getServer()).resetWeek();
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Для Европы, Африки и Азии выбраны новые наборы материалов. Недельный прогресс сброшен."), true);
                return 1;
            }));

            root.then(Commands.literal("limit")
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                        VmExchangeConfig.get().weeklyLimit = IntegerArgumentType.getInteger(ctx, "amount");
                        VmExchangeConfig.save();
                        VmExchangeManager.get(ctx.getSource().getServer()).refreshOpenMenus();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Недельный лимит: " + VmExchangeConfig.get().weeklyLimit), true);
                        return 1;
                    })));

            root.then(Commands.literal("price")
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                            .then(Commands.argument("price", IntegerArgumentType.integer(1)).executes(ctx -> {
                                int i = IntegerArgumentType.getInteger(ctx, "tier") - 1;
                                int price = IntegerArgumentType.getInteger(ctx, "price");
                                var cfg = VmExchangeConfig.get();
                                ensureTier(cfg, i);
                                cfg.tiers.get(i).price = price;
                                cfg.tiers.sort(Comparator.comparingInt(t -> t.limit));
                                VmExchangeConfig.save();
                                VmExchangeManager.get(ctx.getSource().getServer()).refreshOpenMenus();
                                ctx.getSource().sendSuccess(() -> Component.literal("Цена ступени обновлена."), true);
                                return 1;
                            }))));

            root.then(Commands.literal("tier")
                    .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                            .then(Commands.argument("limit", IntegerArgumentType.integer(1)).executes(ctx -> {
                                int i = IntegerArgumentType.getInteger(ctx, "tier") - 1;
                                int limit = IntegerArgumentType.getInteger(ctx, "limit");
                                var cfg = VmExchangeConfig.get();
                                ensureTier(cfg, i);
                                cfg.tiers.get(i).limit = limit;
                                cfg.tiers.sort(Comparator.comparingInt(t -> t.limit));
                                VmExchangeConfig.save();
                                VmExchangeManager.get(ctx.getSource().getServer()).refreshOpenMenus();
                                ctx.getSource().sendSuccess(() -> Component.literal("Лимит ступени обновлён."), true);
                                return 1;
                            }))));

            root.then(Commands.literal("resetweek").executes(ctx -> {
                VmExchangeManager.get(ctx.getSource().getServer()).resetWeek();
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Неделя обмена перезапущена: материалы всех стран обновлены, недельный прогресс сброшен."), true);
                return 1;
            }));

            root.then(Commands.literal("resetplayer")
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                        VmExchangeManager.get(ctx.getSource().getServer()).data().setPlayer(player.getUUID(), 0, 0);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Прогресс игрока сброшен: " + player.getGameProfile().name()), true);
                        VmExchangeNetworking.sendSnapshot(player);
                        return 1;
                    })));

            root.then(Commands.literal("player")
                    .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                        var manager = VmExchangeManager.get(ctx.getSource().getServer());
                        var state = manager.state(player);
                        var config = VmExchangeConfig.get();
                        String group = manager.groupForPlayer(player);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "Игрок: " + player.getGameProfile().name()
                                        + " | Страна: " + (group == null ? "нет доступа" : group)
                                        + " | Получено: " + state.weeklyEarned() + " / " + config.weeklyLimit
                                        + " | Уровень: " + state.tier()
                                        + " | Курс: " + manager.currentPrice(player)), false);
                        return 1;
                    })))));

            root.then(Commands.literal("reload").executes(ctx -> {
                VmExchangeConfig.load();
                var manager = VmExchangeManager.get(ctx.getSource().getServer());
                manager.ensureCurrentWeek(ctx.getSource().getServer());
                manager.refreshOpenMenus();
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Конфигурация ВМуткоин Обменника перезагружена."), true);
                return 1;
            }));

            dispatcher.register(root);
        });
    }

    private static int info(ServerPlayer player) {
        var manager = VmExchangeManager.get(player.level().getServer());
        var cfg = VmExchangeConfig.get();
        manager.ensureCurrentWeek(player.level().getServer());
        player.sendSystemMessage(Component.literal("Текущая неделя: " + manager.data().weekKey()));
        player.sendSystemMessage(Component.literal("До сброса: " + untilReset(cfg)));
        for (String group : VmExchangeManager.supportedGroups()) {
            player.sendSystemMessage(Component.literal(
                    group + ": " + String.join(", ", manager.materialsForGroup(group))));
        }
        player.sendSystemMessage(Component.literal("Недельный лимит: " + cfg.weeklyLimit));
        return 1;
    }

    private static int setMaterial(ServerPlayer player, String group, int slot, String id) {
        if (!VmExchangeManager.isSupportedGroup(group)) {
            player.sendSystemMessage(Component.literal("Допустимые группы: europe, africa, avstralia."));
            return 0;
        }
        if (Identifier.tryParse(id) == null) {
            player.sendSystemMessage(Component.literal("Некорректный ID предмета."));
            return 0;
        }
        var manager = VmExchangeManager.get(player.level().getServer());
        List<String> list = new ArrayList<>(manager.materialsForGroup(group));
        if (list.size() != 4) {
            player.sendSystemMessage(Component.literal(
                    "Сначала дождитесь генерации наборов материалов или используйте /vmexchange randomize."));
            return 0;
        }
        list.set(slot - 1, id);
        try {
            manager.setMaterials(group, list, false);
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage() == null
                    ? "Набор материалов невалиден." : exception.getMessage()));
            return 0;
        }
        player.sendSystemMessage(Component.literal(
                "Материал " + slot + " для группы " + group + " установлен: " + id));
        return 1;
    }

    private static int setMaterials(ServerPlayer player, String group, List<String> ids) {
        try {
            VmExchangeManager.get(player.level().getServer()).setMaterials(group, ids, false);
            player.sendSystemMessage(Component.literal("Набор материалов для группы " + group + " обновлён."));
            return 1;
        } catch (Exception exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage() == null
                    ? "Нужно указать 4 существующих ID без повторов: первые три — обычные ресурсы, четвёртый — специальный."
                    : exception.getMessage()));
            return 0;
        }
    }

    private static void ensureTier(VmExchangeConfig cfg, int i) {
        while (cfg.tiers.size() <= i) cfg.tiers.add(new VmExchangeConfig.Tier(cfg.weeklyLimit, 1));
    }

    private static String untilReset(VmExchangeConfig cfg) {
        DayOfWeek day;
        try { day = DayOfWeek.valueOf(cfg.reset.day.toUpperCase(Locale.ROOT)); }
        catch (Exception e) { day = DayOfWeek.MONDAY; }
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(day))
                .withHour(cfg.reset.hour).withMinute(cfg.reset.minute).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusWeeks(1);
        Duration d = Duration.between(now, next);
        return d.toDaysPart() + "д " + d.toHoursPart() + "ч " + d.toMinutesPart() + "м";
    }

    private VmExchangeCommands() {}
}
