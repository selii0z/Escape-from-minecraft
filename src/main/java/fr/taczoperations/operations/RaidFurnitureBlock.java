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
        for(int i=0;i<fr.taczoperations.OperationsMod.DECOR.size();i++)if(state.is(fr.taczoperations.OperationsMod.DECOR.get(i).get())){kind=6+i;break;}
        return switch(kind){
            // Bounds of the original converted meshes, rounded outwards.
            case 0 -> box(.98,0,1.28,15.02,15.36,14.72);
            case 1 -> box(.32,0,3.50,15.68,10.88,12.50);
            case 2 -> box(4.95,0,.32,11.05,4.87,15.68);
            case 3,10 -> box(.32,0,.32,15.68,15.36,15.68);
            case 4 -> box(.32,0,.32,15.68,13.44,15.68);
            case 5 -> box(2.70,0,3.29,13.30,15.36,12.71);
            case 6 -> box(3.63,0,.32,12.37,15.36,15.68);
            case 7 -> box(.32,0,.32,15.68,15.22,15.68);
            case 8 -> box(3.98,0,4.06,12.02,15.36,11.94);
            case 9 -> box(.76,0,.32,15.24,15.36,15.68);
            default -> box(0,0,0,16,16,16);
        };
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(fr.taczoperations.OperationsMod.DECOR.stream().anyMatch(b->state.is(b.get())))return InteractionResult.PASS;
        if(player instanceof ServerPlayer p&&level.getBlockEntity(pos) instanceof RaidFurnitureEntity furniture){
            if(ForestRaid.canSearchFurniture(p,furniture))p.openMenu(furniture);
            else p.displayClientMessage(net.minecraft.network.chat.Component.literal("Conteneur indisponible hors de ton raid."),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
