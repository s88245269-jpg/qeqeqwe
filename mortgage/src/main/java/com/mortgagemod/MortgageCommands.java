package com.mortgagemod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MortgageMod.MODID)
public final class MortgageCommands {
    private MortgageCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("mortgage")
                .then(Commands.literal("help").executes(c -> help(c.getSource().getPlayerOrException())))
                .then(Commands.literal("status").executes(c -> status(c.getSource().getPlayerOrException())))
                .then(Commands.literal("rate").executes(c -> rate(c.getSource().getPlayerOrException())))
                .then(Commands.literal("pay").then(Commands.argument("emeralds", IntegerArgumentType.integer(1, 2304))
                        .executes(c -> pay(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "emeralds")))))
                .then(Commands.literal("refinance").then(Commands.argument("months", IntegerArgumentType.integer(6, 24))
                        .then(Commands.literal("fixed").executes(c -> refi(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "months"), false)))
                        .then(Commands.literal("floating").executes(c -> refi(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "months"), true))))));
    }

    private static int help(ServerPlayer p) {
        Bank.msg(p, "Скрафти и поставь Закладную плиту: внесёшь " + Rules.DOWN + " изумрудов, остальное банк даст в кредит на " + Rules.DEFAULT_MONTHS + " мес.");
        Bank.msg(p, "/mortgage status | rate | pay <N> (досрочно) | refinance <6-24> <fixed|floating>");
        Bank.msg(p, "Платёж раз в 3 игровых дня списывается изумрудами из инвентаря. 3 просрочки подряд = арест участка.");
        return 1;
    }

    private static int status(ServerPlayer p) {
        BankData d = BankData.get(p.getServer());
        long now = p.getServer().overworld().getGameTime();
        Bank.msg(p, "Кредитный рейтинг: " + d.score(p.getUUID()) + " (надбавка банка +" + d.spread(p.getUUID()) + "%). Ключевая ставка: " + d.keyRate + "%.");
        boolean any = false;
        for (Contract c : d.contracts) if (c.owner.equals(p.getUUID())) { Bank.describe(p, c, now); any = true; }
        if (!any) Bank.msg(p, "У вас нет договоров. Поставьте Закладную плиту.");
        return 1;
    }

    private static int rate(ServerPlayer p) {
        BankData d = BankData.get(p.getServer());
        long left = Math.max(0, d.nextRateChange - p.getServer().overworld().getGameTime()) / 1200;
        Bank.msg(p, "Ключевая ставка " + d.keyRate + "%. Для вас: плавающая " + Bank.r1(d.keyRate + d.spread(p.getUUID()))
                + "%, фиксированная " + Bank.r1(d.keyRate + d.spread(p.getUUID()) + Rules.FIXED_PREMIUM) + "%. Следующий пересмотр через ~" + left + " мин.");
        return 1;
    }

    private static int pay(ServerPlayer p, int n) {
        BankData d = BankData.get(p.getServer());
        Contract c = d.activeOf(p.getUUID());
        if (c == null) { Bank.msg(p, "У вас нет действующей ипотеки."); return 0; }
        n = Math.min(n, (int) Math.ceil(c.balance));
        if (!Bank.take(p, n)) { Bank.msg(p, "Не хватает изумрудов в инвентаре."); return 0; }
        c.balance -= n;
        if (c.balance <= 0.5) Bank.payoff(d, p, c);
        else {
            c.payment = BankData.annuity(c.balance, c.rate, Math.max(1, c.monthsLeft));
            Bank.msg(p, "Внесено " + n + " изум. Долг: " + Math.round(c.balance) + ", новый платёж: " + c.payment + ".");
        }
        d.setDirty();
        return 1;
    }

    private static int refi(ServerPlayer p, int months, boolean floating) {
        BankData d = BankData.get(p.getServer());
        Contract c = d.activeOf(p.getUUID());
        if (c == null) { Bank.msg(p, "У вас нет действующей ипотеки."); return 0; }
        if (c.missed > 0) { Bank.msg(p, "Рефинансирование недоступно при просрочке."); return 0; }
        int fee = (int) Math.ceil(c.balance * 0.05);
        if (!Bank.take(p, fee)) { Bank.msg(p, "Комиссия за рефинансирование " + fee + " изум. (5% долга), в инвентаре не хватает."); return 0; }
        c.spread = d.spread(p.getUUID());
        c.floating = floating;
        c.rate = d.keyRate + c.spread + (floating ? 0 : Rules.FIXED_PREMIUM);
        c.monthsLeft = months;
        c.payment = BankData.annuity(c.balance, c.rate, months);
        d.setDirty();
        Bank.msg(p, "Рефинансировано: " + Bank.r1(c.rate) + "% (" + (floating ? "плавающая" : "фиксированная") + "), " + months + " мес., платёж " + c.payment + ". Комиссия: " + fee + ".");
        return 1;
    }
}
