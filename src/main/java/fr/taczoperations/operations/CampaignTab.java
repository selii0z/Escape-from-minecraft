package fr.taczoperations.operations;

import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.campaign.CampaignGraph.State;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** Board-scoped, server-authoritative quest actions. */
final class CampaignTab {
    static CampaignTabData snapshot(ServerPlayer p,int menuId,CampaignTrader trader,String notice){
        var live=CampaignRuntime.views(p,trader.id);
        var all=CampaignPreview.merge(live,trader.id);
        var liveIds=new HashSet<String>();for(var q:live)liveIds.add(q.id());
        String tracked=SupplyContract.account(p).getString(CampaignMenu.TRACKED);
        var result=new ArrayList<CampaignTabData.Quest>();
        for(var q:all){
            boolean playable=liveIds.contains(q.id());
            String status=!playable?"Non jouable":q.claimable()?"À valider":CampaignRuntime.stateLabel(q.state());
            var lines=new ArrayList<>(q.lines());
            if(playable)lines.addAll(CampaignRuntime.requirementLines(q.id()));
            result.add(new CampaignTabData.Quest(q.id(),CampaignFrench.title(q.name()),status,playable,tracked.equals(q.id()),
                playable?CampaignRuntime.questArt(q.id()):"military",playable?CampaignRuntime.objectiveCount(q.id()):0,
                lines,playable?CampaignRuntime.rewardIcons(q.id()):List.of()));
        }
        var loyalty=new ArrayList<Integer>();for(var t:CampaignTrader.values())loyalty.add(CampaignTraderAccount.unlocked(p,t)?CampaignTraderAccount.loyalty(p,t):0);
        return new CampaignTabData(menuId,trader.ordinal(),SupplyContract.balance(p),SupplyContract.experienceEarned(p),CampaignRewardInbox.pending(p),loyalty,result,notice);
    }
    static String act(ServerPlayer p,CampaignTrader trader,int action,String id){
        if(action==0)return "";
        if(action==4){int count=CampaignRewardInbox.collect(p);return count+" objet(s) récupéré(s)";}
        if(!id.matches("[0-9a-f]{24}")||!CampaignRuntime.belongsToTrader(id,trader.id))return "Quête indisponible.";
        if(action==1)return CampaignRuntime.acceptNamed(p,id)>0?"Quête acceptée.":"Impossible d’accepter cette quête.";
        if(action==2)return CampaignRuntime.claimNamed(p,id)>0?"Quête terminée.":"Objectifs ou récompenses à vérifier.";
        if(action==3){
            var state=CampaignRuntime.views(p,trader.id).stream().filter(q->q.id().equals(id)).findFirst().orElse(null);
            if(state==null||(state.state()!=State.ACTIVE&&state.state()!=State.READY))return "Accepte la quête avant de la suivre.";
            var a=SupplyContract.account(p);
            if(id.equals(a.getString(CampaignMenu.TRACKED))){a.remove(CampaignMenu.TRACKED);return "Suivi désactivé.";}
            a.putString(CampaignMenu.TRACKED,id);return "Quête suivie en raid.";
        }
        return "Action indisponible.";
    }
    private CampaignTab(){}
}
