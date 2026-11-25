package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class FieldMedicine extends Item {
    private final String kind;
    public FieldMedicine(Properties props,String kind){super(props);this.kind=kind;}

    String kind(){return kind;}

    @Override public int getUseDuration(ItemStack stack,LivingEntity user){
        return switch(kind){
            case "bandage"->40;
            case "hemostat"->60;
            case "splint","salewa"->80;
            case "medkit"->100;
            default->60;
        };
    }
    private String treatmentDescription(){
        return switch(kind){
            case "bandage" -> "Arrête un saignement.";
            case "hemostat" -> "Arrête tous les saignements.";
            case "splint" -> "Traite une fracture à la jambe.";
            case "salewa" -> "Restaure 4 cœurs (+ bonus de médecine).";
            case "medkit" -> "Traite une blessure au bras.";
            default -> "Soin de terrain.";
        };
    }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,
            java.util.List<Component> tooltip,net.minecraft.world.item.TooltipFlag flag){
        tooltip.add(Component.literal(treatmentDescription()).withStyle(net.minecraft.ChatFormatting.GRAY));
        int seconds=getUseDuration(stack,null)/20;
        tooltip.add(Component.literal("Durée : "+seconds+" s").withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(Component.literal("Maintenir le clic droit en raid").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Consommé seulement si le soin réussit").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    // Vanilla held-use pose: no extra client dependency. No eating particles.
    @Override public UseAnim getUseAnimation(ItemStack stack){return UseAnim.BLOCK;}

    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND||!level.dimension().equals(ForestExpedition.FOREST))
            return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer p&&!RaidInjuries.canTreat(p,kind)){
            p.displayClientMessage(Component.literal("Ce soin n'est pas nécessaire actuellement."),true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if(player instanceof ServerPlayer p)
            p.displayClientMessage(Component.literal("Soin en cours · maintiens le clic droit."),true);
        return InteractionResultHolder.consume(stack);
    }

    @Override public void onUseTick(Level level,LivingEntity user,ItemStack stack,int remaining){
        if(user instanceof ServerPlayer p){
            if(!RaidInjuries.canTreat(p,kind)){
                p.stopUsingItem();
                return;
            }
            // Progress is rendered by the client HUD; leave extraction hints unobstructed.
        }
    }

    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity user){
        // The treatment and consumption happen together, server-side, only
        // after a full uninterrupted use. Recheck injury state and raid membership.
        if(user instanceof ServerPlayer p&&RaidInjuries.treat(p,kind)){
            if(!p.getAbilities().instabuild)stack.shrink(1);
            RaidAudio.medicine(p,kind);
            p.displayClientMessage(Component.literal("Soin terminé."),true);
        }
        return stack;
    }
}
