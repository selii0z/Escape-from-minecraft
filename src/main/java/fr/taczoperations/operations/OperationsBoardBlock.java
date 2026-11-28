package fr.taczoperations.operations;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public final class OperationsBoardBlock extends HorizontalDirectionalBlock {
    public enum Part implements StringRepresentable {
        LEGACY("legacy",0,0), LOWER_LEFT("lower_left",0,0), LOWER_RIGHT("lower_right",1,0),
        UPPER_LEFT("upper_left",0,1), UPPER_RIGHT("upper_right",1,1);
        final String id; final int x,y;
        Part(String id,int x,int y){this.id=id;this.x=x;this.y=y;}
        @Override public String getSerializedName(){return id;}
    }
    public static final EnumProperty<Part> PART=EnumProperty.create("part",Part.class);
    private static final Part[] LINKED={Part.LOWER_LEFT,Part.LOWER_RIGHT,Part.UPPER_LEFT,Part.UPPER_RIGHT};
    private static final VoxelShape[] NORTH_SHAPES={Shapes.block(),Shapes.or(Shapes.empty(),Block.box(1,0,3,6,3,13),Block.box(2,2,5,5,16,11),Block.box(4,9,5,16,16,11),Block.box(3,8,4,16,10,12),Block.box(3,9,4,5,16,12),Block.box(6,12,3.6,15,16,4.2)),
        Shapes.or(Shapes.empty(),Block.box(10,0,3,15,3,13),Block.box(11,2,5,14,16,11),Block.box(0,9,5,12,16,11),Block.box(0,8,4,13,10,12),Block.box(11,9,4,13,16,12),Block.box(1,12,3.6,10,16,4.2)),
        Shapes.or(Shapes.empty(),Block.box(2,0,5,5,14,11),Block.box(4,0,5,16,12,11),Block.box(3,11,4,16,14,12),Block.box(3,0,4,5,12,12),Block.box(9,12,3.7,16,15,12.3),Block.box(6,0,3.6,15,2,4.2),Block.box(6,4,3.6,15,10,4.2),Block.box(15,13,3.2,16,15,3.8)),
        Shapes.or(Shapes.empty(),Block.box(11,0,5,14,14,11),Block.box(0,0,5,12,12,11),Block.box(0,11,4,13,14,12),Block.box(11,0,4,13,12,12),Block.box(0,12,3.7,7,15,12.3),Block.box(1,0,3.6,10,2,4.2),Block.box(1,4,3.6,10,10,4.2),Block.box(0,13,3.2,1,15,3.8))};
    private static final MapCodec<OperationsBoardBlock> CODEC = simpleCodec(OperationsBoardBlock::new);

    @Override
    protected MapCodec<OperationsBoardBlock> codec() {
        return CODEC;
    }

    public OperationsBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART,Part.LEGACY));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing=context.getHorizontalDirection().getOpposite();
        Level level=context.getLevel();
        BlockPos origin=context.getClickedPos();
        for(Part part:LINKED){
            BlockPos target=position(origin,facing,part);
            if(level.isOutsideBuildHeight(target)||!level.getWorldBorder().isWithinBounds(target)
                ||!level.hasChunkAt(target)||!level.getBlockState(target).canBeReplaced(context))return null;
        }
        return defaultBlockState().setValue(FACING,facing).setValue(PART,Part.LOWER_LEFT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING,PART);
    }

    private static BlockPos position(BlockPos base,Direction facing,Part part){
        return base.relative(facing.getClockWise(),part.x).above(part.y);
    }
    private static BlockPos origin(BlockPos pos,Direction facing,Part part){
        return pos.relative(facing.getCounterClockWise(),part.x).below(part.y);
    }

    @Override
    public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level.isClientSide||state.getValue(PART)!=Part.LOWER_LEFT)return;
        Direction facing=state.getValue(FACING);
        for(Part part:LINKED)if(part!=Part.LOWER_LEFT)
            level.setBlock(position(pos,facing,part),state.setValue(PART,part),3);
    }

    @Override
    protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moved){
        if(!level.isClientSide&&!state.is(replacement.getBlock())&&state.getValue(PART)!=Part.LEGACY){
            Direction facing=state.getValue(FACING);
            BlockPos base=origin(pos,facing,state.getValue(PART));
            for(Part part:LINKED){
                BlockPos other=position(base,facing,part);
                if(other.equals(pos)||!level.hasChunkAt(other))continue;
                BlockState candidate=level.getBlockState(other);
                if(candidate.is(this)&&candidate.getValue(FACING)==facing&&candidate.getValue(PART)==part)
                    level.removeBlock(other,false);
            }
        }
        super.onRemove(state,level,pos,replacement,moved);
    }

    @Override
    protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        VoxelShape shape=NORTH_SHAPES[state.getValue(PART).ordinal()];
        int turns=switch(state.getValue(FACING)){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        for(int i=0;i<turns;i++){
            VoxelShape rotated=Shapes.empty();
            for(var a:shape.toAabbs())rotated=Shapes.or(rotated,Shapes.box(1-a.maxZ,a.minY,a.minX,1-a.minZ,a.maxY,a.maxX));
            shape=rotated;
        }
        return shape;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            OperationsMenu.open(serverPlayer, pos, OperationsMenu.FOREST, 0, "");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
