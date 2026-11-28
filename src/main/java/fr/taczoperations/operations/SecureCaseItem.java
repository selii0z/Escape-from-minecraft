package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SecureCaseItem extends Item {
    public enum Tier {
        LEGACY("secure_case","Coffre sécurisé",5),
        ALPHA("secure_alpha","Alpha",4),
        BETA("secure_beta","Beta",6),
        EPSILON("secure_epsilon","Epsilon",8),
        GAMMA("secure_gamma","Gamma",9),
        KAPPA("secure_kappa","Kappa",12);
        public final String id,title;
        public final int capacity;
        Tier(String id,String title,int capacity){this.id=id;this.title=title;this.capacity=capacity;}
    }
    private final Tier tier;
    public SecureCaseItem(Properties properties){this(properties,Tier.LEGACY);}
    public SecureCaseItem(Properties properties,Tier tier){super(properties);this.tier=tier;}
    public Tier tier(){return tier;}
    public static int capacity(ItemStack stack){return stack.getItem() instanceof SecureCaseItem c?c.tier.capacity:0;}
    @Override public net.minecraft.network.chat.Component getName(ItemStack stack){
        return net.minecraft.network.chat.Component.literal(tier==Tier.LEGACY?tier.title:"Conteneur sécurisé "+tier.title);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,
        java.util.List<net.minecraft.network.chat.Component> text,net.minecraft.world.item.TooltipFlag flag){
        text.add(net.minecraft.network.chat.Component.literal(tier.capacity+" emplacements · conservé après la mort"));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResultHolder.pass(stack);
        if(player instanceof ServerPlayer p)RaidSecurity.open(p);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide());
    }
}
