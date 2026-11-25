package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class TimedContracts {
    public static final int COUNT=6;
    private static final String ROOT="timed_contracts_v1";
    private static final String[] TITLES={"Éliminations","Fouilles","Extractions"};
    public static String title(int index){return TITLES[index%3];}
    private static long day(){return LocalDate.now(ZoneOffset.UTC).toEpochDay();}
    private static long period(int index,long day){return index<3?day:Math.floorDiv(day+3,7);}
    private static int target(int index,long period) {
        int variation=Math.floorMod(period+index,3);
        return switch(index){
            case 0 -> 8+4*variation;
            case 1 -> 5+3*variation;
            case 2 -> 1+variation;
            case 3 -> 55+15*variation;
            case 4 -> 40+10*variation;
            default -> 7+3*variation;
        };
    }
    public static int credits(int index){return new int[]{650,450,700,4500,3200,5000}[index];}
    public static int xp(int index){return new int[]{120,100,150,800,650,1000}[index];}
    private static CompoundTag state(ServerPlayer player,long day){
        var account=SupplyContract.account(player);
        CompoundTag state=account.getCompound(ROOT);
        for(int i=0;i<COUNT;i++){
            long period=period(i,day);
            if(!state.contains("period"+i)||state.getLong("period"+i)<period){
                state.putLong("period"+i,period);
                state.putInt("progress"+i,0);
                state.putBoolean("done"+i,false);
            }
        }
        account.put(ROOT,state);return state;
    }
    // All period decisions and reward grants are made on the server.
    // A completed raid contributes once; defeats count kills/fouilles but not extraction.
    static int[] record(ServerPlayer player,boolean success,int kills,int crates) {
        var state=state(player,day());var account=SupplyContract.account(player);
        int gained=0,completed=0;
        for(int i=0;i<COUNT;i++){
            if(state.getBoolean("done"+i))continue;
            int add=switch(i%3){case 0->kills;case 1->crates;default->success?1:0;};
            int goal=target(i,state.getLong("period"+i));
            int next=(int)Math.min(goal,(long)Math.max(0,state.getInt("progress"+i))+Math.max(0,add));
            state.putInt("progress"+i,next);
            if(next<goal)continue;
            state.putBoolean("done"+i,true);completed++;gained+=xp(i);
            account.putInt("credits",(int)Math.min(Integer.MAX_VALUE,(long)SupplyContract.balance(player)+credits(i)));
            int merchant=switch(i%3){case 0->OperationsShop.ARMORER.ordinal();case 1->OperationsShop.SALVAGER.ordinal();default->OperationsShop.MEDIC.ordinal();};
            String key="timed_reputation_"+merchant;
            account.putInt(key,(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,account.getInt(key))+(i<3?4:12)));
            player.sendSystemMessage(Component.literal((i<3?"Quotidien":"Hebdomadaire")+" terminé : "+title(i)+" · +"+credits(i)+" crédits / +"+xp(i)+" XP"));
        }
        account.putInt("timed_contract_xp",(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,account.getInt("timed_contract_xp"))+gained));
        return new int[]{completed,gained};
    }
    static List<Integer> view(ServerPlayer p){
        var state=state(p,day());var result=new ArrayList<Integer>(COUNT*3);
        for(int i=0;i<COUNT;i++){
            result.add(state.getInt("progress"+i));result.add(target(i,state.getLong("period"+i)));result.add(state.getBoolean("done"+i)?1:0);
        }
        return List.copyOf(result);
    }
    private TimedContracts(){}
}
