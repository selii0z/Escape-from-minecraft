package fr.taczoperations.operations;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public record WipeAccount(int menuId,String confirmation) implements CustomPacketPayload {
    public static final Type<WipeAccount> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("taczoperations","wipe_account"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WipeAccount> CODEC=StreamCodec.of(
        (b,v)->{b.writeVarInt(v.menuId);b.writeUtf(v.confirmation,4);},
        b->new WipeAccount(b.readVarInt(),b.readUtf(4)));
    @Override public Type<WipeAccount> type(){return TYPE;}
    static String problem(ServerPlayer p){
        if(!PlayerMarket.available(p)||!p.level().dimension().equals(Level.OVERWORLD)
            ||ForestRaid.isParticipant(p)||ForestExpedition.hasReturnPoint(p)||p.isPassenger()||p.isSleeping())
            return "Wipe uniquement dans le monde principal, hors raid.";
        if(!p.containerMenu.getCarried().isEmpty())return "Range d'abord l'objet tenu par le curseur.";
        if(ScavRun.active(p)||ScavRun.pending(p)>0)return "Récupère d'abord ton équipement et ton butin Scav.";
        if(PlayerMarket.of(p).hasPending(p))return "Retire tes annonces et crédits du marché avant le wipe.";
        if(!SupplyContract.account(p).getList("secure_cases_pending",10).isEmpty()
            ||!p.getPersistentData().getList("secure_cases_death",10).isEmpty())return "Récupère d'abord ton coffre sécurisé.";
        return "";
    }
    static String reset(ServerPlayer p){
        String problem=problem(p);if(!problem.isEmpty())return problem;
        var account=SupplyContract.account(p);
        long epoch=account.getLong("insurance_epoch")+1;
        // Only EFM's own account compound; no player inventory, vanilla XP, other mods or world edits.
        for(String key:new java.util.ArrayList<>(account.getAllKeys()))account.remove(key);
        account.putLong("insurance_epoch",epoch);
        var root=p.getPersistentData();
        for(String key:new java.util.ArrayList<>(root.getAllKeys())){
            if(key.startsWith("taczoperations_run_")||key.startsWith("taczoperations_raid_")
                ||key.startsWith("efm_skills_")||key.equals("efm_skill_extraction_awarded")
                ||key.equals("efm_story_run_v1")||key.equals("taczoperations_relic_baseline"))root.remove(key);
        }
        RaidSecurity.clearStaleInsurance(p);
        PacketDistributor.sendToPlayer(p,new DiscoveryState(true,java.util.List.of()));
        return "Compte EFM réinitialisé. Niveau 1, progression et crédits remis à zéro.";
    }
    public static void handle(ServerPlayer p,WipeAccount msg){
        if(p.containerMenu instanceof OperationsMenu menu&&menu.containerId==msg.menuId&&"WIPE".equals(msg.confirmation))
            menu.confirmWipe(p);
    }
}
