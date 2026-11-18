package com.mortgagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Состояние банка в мире: ключевая ставка, договоры, кредитные рейтинги. */
public class BankData extends SavedData {
    public double keyRate = 10;
    public long nextRateChange = -1;
    public final List<Contract> contracts = new ArrayList<>();
    public final Map<UUID, Integer> scores = new HashMap<>();

    public static BankData get(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(BankData::load, BankData::new, "mortgage_bank");
    }

    public static BankData load(CompoundTag t) {
        BankData d = new BankData();
        d.keyRate = t.getDouble("key"); if (d.keyRate <= 0) d.keyRate = 10;
        d.nextRateChange = t.contains("next") ? t.getLong("next") : -1;
        ListTag cl = t.getList("contracts", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) d.contracts.add(Contract.load(cl.getCompound(i)));
        ListTag sl = t.getList("scores", Tag.TAG_COMPOUND);
        for (int i = 0; i < sl.size(); i++) { CompoundTag e = sl.getCompound(i); d.scores.put(e.getUUID("id"), e.getInt("s")); }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putDouble("key", keyRate); t.putLong("next", nextRateChange);
        ListTag cl = new ListTag(); for (Contract c : contracts) cl.add(c.save()); t.put("contracts", cl);
        ListTag sl = new ListTag();
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
            CompoundTag x = new CompoundTag(); x.putUUID("id", e.getKey()); x.putInt("s", e.getValue()); sl.add(x);
        }
        t.put("scores", sl);
        return t;
    }

    public int score(UUID u) { return scores.getOrDefault(u, 600); }
    public void addScore(UUID u, int delta) { scores.put(u, Math.max(300, Math.min(850, score(u) + delta))); setDirty(); }

    /** Надбавка банка к ключевой ставке зависит от кредитного рейтинга. */
    public double spread(UUID u) { int s = score(u); return s >= 750 ? 1 : s >= 650 ? 2 : s >= 550 ? 4 : s >= 450 ? 7 : 11; }

    /** Аннуитетный платёж в изумрудах (округляется вверх). Год = 12 игровых «месяцев». */
    public static int annuity(double balance, double annualPct, int months) {
        double r = annualPct / 100.0 / 12.0;
        double p = r <= 0 ? balance / months : balance * r / (1 - Math.pow(1 + r, -months));
        return Math.max(1, (int) Math.ceil(p));
    }

    public Contract findAt(String dim, BlockPos p) {
        for (Contract c : contracts) if (c.covers(dim, p)) return c;
        return null;
    }

    public Contract activeOf(UUID u) {
        for (Contract c : contracts) if (c.state == Contract.MORTGAGE && c.owner.equals(u)) return c;
        return null;
    }

    public boolean overlaps(String dim, BlockPos p) {
        for (Contract c : contracts)
            if (c.dim.equals(dim) && Math.abs(c.pos.getX() - p.getX()) <= 2 * Rules.RADIUS
                    && Math.abs(c.pos.getZ() - p.getZ()) <= 2 * Rules.RADIUS) return true;
        return false;
    }
}
