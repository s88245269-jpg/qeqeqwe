package com.mortgagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Iterator;
import java.util.Random;

/** Вся «банковская» логика: платежи, ставка, арест, коллекторы. */
public final class Bank {
    private static final Random RNG = new Random();
    private Bank() {}

    public static double r1(double x) { return Math.round(x * 10) / 10.0; }
    public static void msg(ServerPlayer p, String s) { p.sendSystemMessage(Component.literal("§6[Банк] §f" + s)); }
    public static void broadcast(MinecraftServer s, String t) {
        s.getPlayerList().broadcastSystemMessage(Component.literal("§6[Банк] §f" + t), false);
    }

    public static int count(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(Items.EMERALD)) n += s.getCount();
        return n;
    }

    /** Забирает n изумрудов из инвентаря целиком или не забирает ничего. */
    public static boolean take(ServerPlayer p, int n) {
        if (count(p) < n) return false;
        int left = n;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (s.is(Items.EMERALD)) { int t = Math.min(left, s.getCount()); s.shrink(t); left -= t; }
        }
        p.getInventory().setChanged();
        return true;
    }

    public static ServerLevel levelOf(MinecraftServer s, String dim) {
        return s.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
    }

    public static void describe(ServerPlayer p, Contract c, long now) {
        String st = c.state == Contract.MORTGAGE ? "в ипотеке" : c.state == Contract.OWNED ? "в собственности" : "АРЕСТОВАН";
        String s = "Участок игрока " + c.ownerName + " (" + c.pos.getX() + ", " + c.pos.getZ() + "): " + st + ".";
        if (c.state == Contract.MORTGAGE) {
            s += " Долг " + Math.round(c.balance) + " изум., платёж " + c.payment + " изум./мес., осталось "
                    + c.monthsLeft + " мес., ставка " + r1(c.rate) + "% (" + (c.floating ? "плавающая" : "фиксированная")
                    + "), до платежа ~" + Math.max(0, (c.nextDue - now) / 1200) + " мин, просрочек: " + c.missed + ".";
        }
        msg(p, s);
    }

    // ---- ежемесячные платежи ----

    public static void charge(BankData d, ServerPlayer p, Contract c) {
        double r = c.rate / 100.0 / 12.0;
        c.nextDue += Rules.MONTH;
        if (take(p, c.payment)) {
            c.balance -= c.payment - c.balance * r;
            c.monthsLeft--;
            c.missed = 0;
            d.addScore(c.owner, 5);
            msg(p, "Платёж " + c.payment + " изум. списан. Остаток долга: " + Math.max(0, Math.round(c.balance)) + ".");
            if (c.balance <= 0.5 || c.monthsLeft <= 0) payoff(d, p, c);
        } else {
            c.missed++;
            c.balance += c.payment * 0.1;          // пеня 10% от платежа
            d.addScore(c.owner, -40);
            msg(p, "§cПросрочка! Не хватило " + c.payment + " изумрудов. Начислена пеня. Просрочек подряд: " + c.missed + " из 3.");
            spawnCollectors(p, c.missed);
            if (c.missed >= 3) foreclose(d, p, c);
        }
        d.setDirty();
    }

    public static void payoff(BankData d, ServerPlayer p, Contract c) {
        c.state = Contract.OWNED; c.balance = 0; c.payment = 0; c.monthsLeft = 0; c.missed = 0;
        d.addScore(c.owner, 30);
        msg(p, "§aИпотека погашена! Участок теперь ваш навсегда. Кредитный рейтинг: " + d.score(c.owner) + ".");
        d.setDirty();
    }

    public static void foreclose(BankData d, ServerPlayer p, Contract c) {
        MinecraftServer s = p.getServer();
        c.state = Contract.FORECLOSED; c.balance = 0; c.payment = 0;
        c.foreclosedAt = s.overworld().getGameTime();
        d.addScore(c.owner, -150);
        broadcast(s, "§cБанк изъял участок игрока " + c.ownerName + " за долги. Строить на нём нельзя.");
        spawnCollectors(p, 3);
        d.setDirty();
    }

    public static void spawnCollectors(ServerPlayer p, int n) {
        ServerLevel l = p.serverLevel();
        for (int i = 0; i < n; i++) {
            Vindicator v = EntityType.VINDICATOR.create(l);
            if (v == null) continue;
            double a = RNG.nextDouble() * Math.PI * 2;
            BlockPos bp = l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(p.getX() + Math.cos(a) * 7, p.getY(), p.getZ() + Math.sin(a) * 7));
            v.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, RNG.nextFloat() * 360f, 0f);
            v.finalizeSpawn(l, l.getCurrentDifficultyAt(bp), MobSpawnType.EVENT, null, null);
            v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            v.setDropChance(EquipmentSlot.HEAD, 0f);
            v.setCustomName(Component.literal("Коллектор"));
            v.setCustomNameVisible(true);
            v.setTarget(p);
            l.addFreshEntity(v);
        }
    }

    // ---- ключевая ставка ----

    static void changeRate(MinecraftServer s, BankData d, long now) {
        double old = d.keyRate;
        double nw = Math.round((old + (RNG.nextDouble() - 0.5) * 6) * 2) / 2.0;
        nw = Math.max(Rules.MIN_KEY, Math.min(Rules.MAX_KEY, nw));
        d.keyRate = nw;
        d.nextRateChange = now + Rules.RATE_PERIOD;
        for (Contract c : d.contracts) {
            if (c.state == Contract.MORTGAGE && c.floating) {
                c.rate = nw + c.spread;
                c.payment = BankData.annuity(c.balance, c.rate, Math.max(1, c.monthsLeft));
            }
        }
        d.setDirty();
        broadcast(s, "Центробанк меняет ключевую ставку: " + old + "% → " + nw + "%. Плавающие ипотеки пересчитаны.");
    }

    /** Вызывается раз в 5 секунд. */
    public static void tick(MinecraftServer s) {
        BankData d = BankData.get(s);
        long now = s.overworld().getGameTime();
        if (d.nextRateChange < 0) { d.nextRateChange = now + Rules.RATE_PERIOD; d.setDirty(); }
        if (now >= d.nextRateChange) changeRate(s, d, now);

        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            for (Contract c : d.contracts) {
                if (c.state != Contract.MORTGAGE || !c.owner.equals(p.getUUID())) continue;
                int guard = 0;
                while (c.state == Contract.MORTGAGE && now >= c.nextDue && guard++ < 12) charge(d, p, c);
            }
        }

        Iterator<Contract> it = d.contracts.iterator();
        while (it.hasNext()) {
            Contract c = it.next();
            if (c.state != Contract.FORECLOSED || now - c.foreclosedAt < Rules.RATE_PERIOD) continue;
            ServerLevel l = levelOf(s, c.dim);
            if (l != null) {
                if (!l.isLoaded(c.pos)) continue;
                l.setBlock(c.pos, Blocks.AIR.defaultBlockState(), 3);
            }
            it.remove();
            d.setDirty();
            broadcast(s, "Арестованный участок игрока " + c.ownerName + " выставлен на торги: защита снята.");
        }
    }
}
