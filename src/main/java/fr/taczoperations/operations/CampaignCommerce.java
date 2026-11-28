package fr.taczoperations.operations;

import fr.taczoperations.campaign.CampaignTrader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import java.util.*;

/** Provisional EFM catalogue split using installed content, not a copy of EFT assort data. */
final class CampaignCommerce {
    record Entry(OperationsShop source,int index,OperationsShop.Offer offer){}
    static List<Entry> catalogue(CampaignTrader trader){
        var result=new ArrayList<Entry>();
        for(var source:OperationsShop.values()){
            var offers=source.offers();
            for(int i=0;i<offers.size();i++){
                var offer=offers.get(i);
                if(owner(source,offer)==trader)result.add(new Entry(source,i,offer));
            }
        }
        result.sort(Comparator.comparingInt((Entry e)->e.offer().loyaltyLevel()).thenComparing(e->e.offer().title()));
        return List.copyOf(result);
    }
    static CampaignTrader owner(OperationsShop source,OperationsShop.Offer offer){
        ItemStack sample=offer.stack().get();
        if(sample.isEmpty())return null;
        return switch(source){
            case ARMORER->{
                if(offer.action()==OperationsShop.Action.REPAIR)yield CampaignTrader.MECHANIC;
                if(sample.getItem() instanceof IAttachment||offer.accessory())
                    yield offer.loyaltyLevel()<=2?CampaignTrader.SKIER:CampaignTrader.MECHANIC;
                String id=sample.getItem() instanceof IGun gun?gun.getGunId(sample).getPath():
                    sample.getItem() instanceof IAmmo ammo?ammo.getAmmoId(sample).getPath():"";
                id=id.toLowerCase(Locale.ROOT);
                if(id.matches(".*(m870|shotgun|mp133|mp153|mp155|toz|mosin|12g|12ga|20g|20ga).*"))yield CampaignTrader.JAEGER;
                if(id.matches(".*(m4a1|m16|ar15|hk416|scar|g36|aug|mp5|mp7|ump|glock|m1911|p226|p320|556|45acp|300blk|762x51|9mm|9x19).*"))yield CampaignTrader.PEACEKEEPER;
                yield CampaignTrader.PRAPOR;
            }
            case MEDIC->{
                if(sample.getItem() instanceof FieldMedicine||offer.title().toLowerCase(Locale.ROOT).contains("soin"))yield CampaignTrader.THERAPIST;
                if(offer.action()==OperationsShop.Action.BUY)yield CampaignTrader.JAEGER;
                yield CampaignTrader.THERAPIST;
            }
            case SALVAGER->{
                if(offer.action()==OperationsShop.Action.SELL)yield CampaignTrader.FENCE;
                yield CampaignTrader.RAGMAN;
            }
            case BROKER->CampaignTrader.FENCE;
        };
    }
    static int list(ServerPlayer p){
        if(!PlayerMarket.available(p))return 0;
        p.sendSystemMessage(Component.literal("Marchands de campagne · aperçu de développement"));
        for(var t:CampaignTrader.values()){
            int loyalty=CampaignTraderAccount.loyalty(p,t);
            p.sendSystemMessage(Component.literal(t.title+" · "+t.questCount+" quêtes de référence · "+(loyalty==0?"Verrouillé":"Fidélité "+loyalty)));
        }
        return 1;
    }
    static int preview(ServerPlayer p,CampaignTrader trader,int page){
        if(!PlayerMarket.available(p))return 0;
        int loyalty=CampaignTraderAccount.loyalty(p,trader);
        var entries=catalogue(trader);
        int pageSize=12,pages=Math.max(1,(entries.size()+pageSize-1)/pageSize);
        page=Math.clamp(page,1,pages);
        p.sendSystemMessage(Component.literal(trader.title+" · "+trader.specialty+" · "+trader.questCount+" quêtes · page "+page+"/"+pages));
        p.sendSystemMessage(Component.literal("Aperçu uniquement · "+(loyalty==0?"Marchand verrouillé":"Fidélité "+loyalty)+" · réputation "+String.format(Locale.FRANCE,"%.3f",CampaignTraderAccount.reputation(p,trader)/1000d)));
        for(var req:trader.loyalty)p.sendSystemMessage(Component.literal("Fidélité "+req.tier()+" : niveau "+req.minimumLevel()+" · rép. "+String.format(Locale.FRANCE,"%.3f",req.reputationMilli()/1000d)));
        if(entries.isEmpty())p.sendSystemMessage(Component.literal("Aucun catalogue adapté pour le moment."));
        for(var e:entries.subList((page-1)*pageSize,Math.min(entries.size(),page*pageSize))){
            var offer=e.offer();
            p.sendSystemMessage(Component.literal(offer.action().label+" · "+offer.title()+" · "+offer.price()+" crédits · F"+offer.loyaltyLevel()+(loyalty<offer.loyaltyLevel()?" · Verrouillé":"")));
        }
        return 1;
    }
    private CampaignCommerce(){}
}
