package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FieldMedicine extends Item {
    private final String kind;
    public FieldMedicine(Properties props,String kind){super(props);this.kind=kind;}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide())return InteractionResultHolder.success(stack);
        if(player instanceof ServerPlayer p&&RaidInjuries.treat(p,kind)){
            if(!p.getAbilities().instabuild)stack.shrink(1);
            RaidAudio.medicine(p,kind);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }
}
