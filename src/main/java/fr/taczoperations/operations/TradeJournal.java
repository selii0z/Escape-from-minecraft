package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

/** Successful merchant transactions only; no effect on account balance. */
final class TradeJournal {
    static final int LIMIT=50;
    private static final String KEY="trade_journal_v1";
    private static final String TOTALS="trade_journal_totals_v1";
    static void record(ServerPlayer player,String kind,String merchant,String title,int quantity,long delta){
        var account=SupplyContract.account(player);
        var previous=account.getList(KEY,10);
        var entry=new CompoundTag();
        entry.putLong("time",System.currentTimeMillis());
        entry.putString("kind",kind);
        entry.putString("merchant",merchant);
        entry.putString("title",title);
        entry.putInt("quantity",quantity);
        entry.putLong("delta",delta);
        entry.putInt("balance",SupplyContract.balance(player));
        var next=new ListTag();
        next.add(entry);
        for(int i=0;i<previous.size()&&i<LIMIT-1;i++)next.add(previous.getCompound(i).copy());
        account.put(KEY,next);
        var totals=account.getCompound(TOTALS);
        String key=delta>0?"sales":kind.equals("Réparation")?"repairs":"purchases";
        long old=Math.max(0,totals.getLong(key));
        totals.putLong(key,old+Math.min(Long.MAX_VALUE-old,Math.abs(delta)));
        account.put(TOTALS,totals);
    }
    static int size(ServerPlayer p){return Math.min(LIMIT,SupplyContract.account(p).getList(KEY,10).size());}
    static List<Long> totals(ServerPlayer p){
        var t=SupplyContract.account(p).getCompound(TOTALS);
        return List.of(t.getLong("sales"),t.getLong("purchases"),t.getLong("repairs"));
    }
    static List<String> page(ServerPlayer p,int page){
        var list=SupplyContract.account(p).getList(KEY,10);
        var result=new ArrayList<String>();
        var format=java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(java.time.ZoneOffset.UTC);
        for(int i=page*6;i<Math.min(size(p),page*6+6);i++){
            var e=list.getCompound(i);
            result.add(format.format(java.time.Instant.ofEpochMilli(e.getLong("time")))+"|"+clean(e.getString("kind"))+"|"
                +clean(e.getString("merchant"))+"|"+clean(e.getString("title"))+"|"+e.getInt("quantity")+"|"+e.getLong("delta")+"|"+e.getInt("balance"));
        }
        return List.copyOf(result);
    }
    private static String clean(String s){s=s.replace('|','/').replace('\n',' ');return s.substring(0,Math.min(150,s.length()));}
    private TradeJournal(){}
}
