package fr.taczoperations.operations;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.Container;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Individually registered Collector objects, with stable reference IDs. */
public final class CollectorItems {
    private static final String RESOURCE="/data/taczoperations/campaign/collector-items.txt";
    private record Entry(String id,String title,String icon){}
    private static final List<Entry> ENTRIES=read();
    private static final Map<String,Supplier<? extends Item>> ITEMS=new LinkedHashMap<>();
    private static List<Entry> read(){
        try(var in=CollectorItems.class.getResourceAsStream(RESOURCE)){
            if(in==null)throw new IllegalStateException("Missing Collector item catalog");
            var lines=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)).lines().toList();
            var rows=new ArrayList<Entry>();var ids=new HashSet<String>();
            for(var line:lines){
                if(line.isBlank())continue;
                var p=line.split("[|]",-1);
                if(p.length!=3||!p[0].matches("[0-9a-f]{24}")||p[1].isBlank()||!p[2].matches("[a-z0-9_]+")||!ids.add(p[0]))
                    throw new IllegalStateException("Invalid Collector item row");
                rows.add(new Entry(p[0],p[1],p[2]));
            }
            if(rows.size()!=44)throw new IllegalStateException("Incomplete Collector reference");
            return List.copyOf(rows);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static void register(DeferredRegister.Items registry){
        for(var entry:ENTRIES){
            ITEMS.put(entry.id(),registry.registerItem("collector_"+entry.id(),
                props->new Item(props){
                    @Override public Component getName(ItemStack stack){return Component.literal(entry.title());}
                    @Override public void appendHoverText(ItemStack stack,TooltipContext context,
                        List<Component> text,net.minecraft.world.item.TooltipFlag flag){
                        text.add(Component.literal("Collectionneur · Objet de collection").withStyle(net.minecraft.ChatFormatting.GOLD));
                    }
                },new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
        }
    }
    static Optional<String> reference(ItemStack stack){
        for(var row:ITEMS.entrySet())if(stack.is(row.getValue().get()))return Optional.of(row.getKey());
        return Optional.empty();
    }
    static void addLoot(Container inventory,Random rng,int chance){
        if(rng.nextInt(100)>=chance)return;
        var free=new ArrayList<Integer>();
        for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).isEmpty())free.add(i);
        if(free.isEmpty())return;
        var entry=ENTRIES.get(rng.nextInt(ENTRIES.size()));
        var item=ITEMS.get(entry.id());
        if(item!=null)inventory.setItem(free.get(rng.nextInt(free.size())),new ItemStack(item.get()));
    }
    private CollectorItems(){}
}
