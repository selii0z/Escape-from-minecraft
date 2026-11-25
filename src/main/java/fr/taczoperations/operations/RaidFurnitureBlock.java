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
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer p&&level.getBlockEntity(pos) instanceof RaidFurnitureEntity furniture){
            if(ForestRaid.canSearchFurniture(p,furniture))p.openMenu(furniture);
            else p.displayClientMessage(net.minecraft.network.chat.Component.literal("Conteneur indisponible hors de ton raid."),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
