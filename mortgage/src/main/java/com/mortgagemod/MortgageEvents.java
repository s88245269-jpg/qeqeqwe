package com.mortgagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = MortgageMod.MODID)
public final class MortgageEvents {
    private MortgageEvents() {}

    private static boolean admin(Player p) { return p != null && p.isCreative() && p.hasPermissions(2); }

    private static boolean canBuild(Contract c, Player p) {
        if (admin(p)) return true;
        return p != null && c.state != Contract.FORECLOSED && c.owner.equals(p.getUUID());
    }

    private static void deny(Player p, Contract c) {
        if (p instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal(c.state == Contract.FORECLOSED
                    ? "§cУчасток арестован банком." : "§cЧастная территория игрока " + c.ownerName + "."), true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel lvl)) return;
        BankData d = BankData.get(lvl.getServer());
        String dim = lvl.dimension().location().toString();
        BlockPos pos = e.getPos();
        Entity ent = e.getEntity();
        ServerPlayer sp = ent instanceof ServerPlayer s ? s : null;

        if (e.getPlacedBlock().is(MortgageMod.DEED_BLOCK.get())) {
            if (sp == null || !createContract(d, sp, lvl, dim, pos)) e.setCanceled(true);
            return;
        }
        Contract c = d.findAt(dim, pos);
        if (c != null && !canBuild(c, sp)) { e.setCanceled(true); deny(sp, c); }
    }

    private static boolean createContract(BankData d, ServerPlayer p, ServerLevel lvl, String dim, BlockPos pos) {
        if (d.activeOf(p.getUUID()) != null) { Bank.msg(p, "§cУ вас уже есть действующая ипотека."); return false; }
        if (d.score(p.getUUID()) < 400) { Bank.msg(p, "§cБанк отказал: слишком низкий кредитный рейтинг (" + d.score(p.getUUID()) + ")."); return false; }
        if (d.overlaps(dim, pos)) { Bank.msg(p, "§cУчасток пересекается с чужой территорией."); return false; }
        if (!Bank.take(p, Rules.DOWN)) { Bank.msg(p, "§cНужен первоначальный взнос: " + Rules.DOWN + " изумрудов."); return false; }

        Contract c = new Contract();
        c.owner = p.getUUID(); c.ownerName = p.getGameProfile().getName();
        c.dim = dim; c.pos = pos.immutable(); c.state = Contract.MORTGAGE;
        c.balance = Rules.PRICE - Rules.DOWN; c.monthsLeft = Rules.DEFAULT_MONTHS; c.floating = true;
        c.spread = d.spread(p.getUUID()); c.rate = d.keyRate + c.spread;
        c.payment = BankData.annuity(c.balance, c.rate, c.monthsLeft);
        c.nextDue = lvl.getServer().overworld().getGameTime() + Rules.MONTH;
        d.contracts.add(c); d.setDirty();
        Bank.msg(p, "§aИпотека оформлена! Участок " + (2 * Rules.RADIUS + 1) + "x" + (2 * Rules.RADIUS + 1)
                + " теперь под защитой. Кредит " + (int) c.balance + " изум. под " + Bank.r1(c.rate)
                + "% (плавающая), платёж " + c.payment + " изум. раз в 3 игровых дня. Изумруды берутся из инвентаря. /mortgage help");
        return true;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel lvl)) return;
        BankData d = BankData.get(lvl.getServer());
        String dim = lvl.dimension().location().toString();
        Contract c = d.findAt(dim, e.getPos());
        if (c == null) return;
        Player p = e.getPlayer();

        if (c.pos.equals(e.getPos())) {
            boolean paidOwner = c.state == Contract.OWNED && p.getUUID().equals(c.owner);
            if (paidOwner || admin(p)) { d.contracts.remove(c); d.setDirty(); return; }
            e.setCanceled(true);
            if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.literal("§cЭту плиту нельзя сломать, пока ипотека не погашена."), true);
            return;
        }
        if (!canBuild(c, p)) { e.setCanceled(true); deny(p, c); }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate e) {
        Level lvl = e.getLevel();
        if (lvl.isClientSide || lvl.getServer() == null) return;
        BankData d = BankData.get(lvl.getServer());
        String dim = lvl.dimension().location().toString();
        e.getAffectedBlocks().removeIf(bp -> d.findAt(dim, bp) != null);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null || s.getTickCount() % 100 != 0) return;
        Bank.tick(s);
    }
}
