package com.mortgagemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

/** Закладная плита: ставишь её, и участок вокруг оформляется в ипотеку. ПКМ показывает договор. */
public class DeedBlock extends Block {
    public DeedBlock(Properties props) { super(props); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            BankData d = BankData.get(level.getServer());
            Contract c = null;
            for (Contract x : d.contracts) if (x.pos.equals(pos) && x.dim.equals(level.dimension().location().toString())) c = x;
            if (c == null) Bank.msg(sp, "Эта плита не зарегистрирована в банке.");
            else Bank.describe(sp, c, level.getServer().overworld().getGameTime());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
