package ru.pasha.adaptationwheel.block;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import ru.pasha.adaptationwheel.server.DarknessLight;

/** Scheduled expiry also removes stale lights after a save/restart or chunk reload. */
public final class AdaptationGlowBlock extends LightBlock {
    public AdaptationGlowBlock() { super(BlockBehaviour.Properties.copy(Blocks.LIGHT).noLootTable()); }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if(!level.isClientSide) level.scheduleTick(pos,this,10);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random) {
        if(!DarknessLight.fresh(level,pos)) level.setBlock(pos,
            state.getFluidState().isEmpty()?Blocks.AIR.defaultBlockState():Blocks.WATER.defaultBlockState(),3);
        else level.scheduleTick(pos,this,10);
    }
}
