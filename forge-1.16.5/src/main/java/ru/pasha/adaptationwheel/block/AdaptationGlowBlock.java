package ru.pasha.adaptationwheel.block;
import java.util.Random;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.state.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.*;
import net.minecraft.world.*;
import net.minecraft.world.server.ServerWorld;
import ru.pasha.adaptationwheel.server.DarknessLight;
public final class AdaptationGlowBlock extends Block {
 public static final IntegerProperty LEVEL=IntegerProperty.create("level",0,15);
 public AdaptationGlowBlock(){super(AbstractBlock.Properties.of(Material.DECORATION).noCollission().noOcclusion().noDrops().strength(-1).lightLevel(s->s.getValue(LEVEL)));registerDefaultState(stateDefinition.any().setValue(LEVEL,0));}
 @Override protected void createBlockStateDefinition(StateContainer.Builder<Block,BlockState> b){b.add(LEVEL);}
 @Override public BlockRenderType getRenderShape(BlockState s){return BlockRenderType.INVISIBLE;}
 @Override public VoxelShape getShape(BlockState s,IBlockReader w,BlockPos p,ISelectionContext c){return VoxelShapes.empty();}
 @Override public void onPlace(BlockState s,World w,BlockPos p,BlockState old,boolean moved){super.onPlace(s,w,p,old,moved);if(!w.isClientSide)w.getBlockTicks().scheduleTick(p,this,10);}
 @Override public void tick(BlockState s,ServerWorld w,BlockPos p,Random random){if(!DarknessLight.fresh(w,p))w.setBlock(p,Blocks.AIR.defaultBlockState(),3);else w.getBlockTicks().scheduleTick(p,this,10);}
}
