package fr.taczoperations.operations;

import fr.taczoperations.campaign.CampaignTrader;
import net.minecraft.server.level.ServerPlayer;
import java.util.Objects;

/** Server-owned trader account, reset automatically by the existing EFM account wipe. */
final class CampaignTraderAccount {
    private static final String REP="campaign_trader_rep_";
    private static final String UNLOCK="campaign_trader_unlock_";
    static boolean unlocked(ServerPlayer p,CampaignTrader trader){
        return switch(trader){
            case REF->SupplyContract.account(p).getBoolean(UNLOCK+trader.id)
                ||CampaignStorage.retired("6834145ebc1f443d7603c8a7")&&MilitaryProgression.level(SupplyContract.experienceEarned(p))>=10;
            case JAEGER,LIGHTKEEPER,BTR_DRIVER->SupplyContract.account(p).getBoolean(UNLOCK+trader.id);
            default->true;
        };
    }
    static int reputation(ServerPlayer p,CampaignTrader trader){
        return SupplyContract.account(p).getInt(REP+trader.id);
    }
    static int loyalty(ServerPlayer p,CampaignTrader trader){
        return trader.loyaltyLevel(MilitaryProgression.level(SupplyContract.experienceEarned(p)),reputation(p,trader),unlocked(p,trader));
    }
    // Called only by future validated quest reward transactions.
    static void applyReputation(ServerPlayer p,CampaignTrader trader,int deltaMilli){
        var a=SupplyContract.account(p);
        a.putInt(REP+trader.id,(int)Math.clamp((long)reputation(p,trader)+deltaMilli,-100_000L,100_000L));
    }
    static void unlockFromCompletedQuest(ServerPlayer p,CampaignTrader trader,String questId){
        Objects.requireNonNull(questId);
        // Verified direct unlocks in the frozen PvE reference.
        boolean permitted=switch(trader){
            case JAEGER->questId.equals("5d2495a886f77425cd51e403");
            case REF->questId.equals("6834145ebc1f443d7603c8a7");
            default->false;
        };
        if(!permitted)throw new IllegalArgumentException("Unreviewed trader unlock");
        SupplyContract.account(p).putBoolean(UNLOCK+trader.id,true);
    }
    private CampaignTraderAccount(){}
}
