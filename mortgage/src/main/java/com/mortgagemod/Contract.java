package com.mortgagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public class Contract {
    public static final int MORTGAGE = 0, OWNED = 1, FORECLOSED = 2;
    public UUID owner;
    public String ownerName = "", dim = "";
    public BlockPos pos = BlockPos.ZERO;
    public int state, monthsLeft, payment, missed;
    public double balance, rate, spread;
    public boolean floating;
    public long nextDue, foreclosedAt;

    public boolean covers(String d, BlockPos p) {
        return dim.equals(d) && Math.abs(p.getX() - pos.getX()) <= Rules.RADIUS
                && Math.abs(p.getZ() - pos.getZ()) <= Rules.RADIUS;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("owner", owner); t.putString("name", ownerName); t.putString("dim", dim);
        t.putLong("pos", pos.asLong()); t.putInt("state", state); t.putInt("months", monthsLeft);
        t.putInt("payment", payment); t.putInt("missed", missed);
        t.putDouble("balance", balance); t.putDouble("rate", rate); t.putDouble("spread", spread);
        t.putBoolean("floating", floating); t.putLong("due", nextDue); t.putLong("fc", foreclosedAt);
        return t;
    }

    public static Contract load(CompoundTag t) {
        Contract c = new Contract();
        c.owner = t.getUUID("owner"); c.ownerName = t.getString("name"); c.dim = t.getString("dim");
        c.pos = BlockPos.of(t.getLong("pos")); c.state = t.getInt("state"); c.monthsLeft = t.getInt("months");
        c.payment = t.getInt("payment"); c.missed = t.getInt("missed");
        c.balance = t.getDouble("balance"); c.rate = t.getDouble("rate"); c.spread = t.getDouble("spread");
        c.floating = t.getBoolean("floating"); c.nextDue = t.getLong("due"); c.foreclosedAt = t.getLong("fc");
        return c;
    }
}
