package fr.taczoperations.operations;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

public final class RaidFurnitureBlock extends BaseEntityBlock {
    public static final MapCodec<RaidFurnitureBlock> CODEC=simpleCodec(RaidFurnitureBlock::new);
    public RaidFurnitureBlock(Properties p){super(p);}
    @Override protected MapCodec<RaidFurnitureBlock> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RaidFurnitureEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        int kind=-1;
        for(int i=0;i<fr.taczoperations.OperationsMod.FURNITURE.size();i++)if(state.is(fr.taczoperations.OperationsMod.FURNITURE.get(i).get())){kind=i;break;}
        return switch(kind){
            case 0 -> box(2,0,1,14,10,13);
            case 1 -> box(0,0,2,16,12,14);
            case 2 -> box(0,0,1,16,10,15);
            case 3 -> box(1,0,0,15,15,15);
            case 4 -> box(3,0,0,13,16,15);
            case 5 -> box(0,0,0,16,16,16);
            default -> box(0,0,0,16,16,16);
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p&&level.getBlockEntity(pos) instanceof RaidFurnitureEntity furniture){
            if(ForestRaid.canSearchFurniture(p,furniture))p.openMenu(furniture);
            else p.displayClientMessage(net.minecraft.network.chat.Component.literal("Conteneur indisponible hors de ton raid."),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
