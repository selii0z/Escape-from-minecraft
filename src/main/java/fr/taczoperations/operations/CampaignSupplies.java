package fr.taczoperations.operations;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Trade supplies referenced by reviewed quest data. These are inert loot, not functional chemicals or explosives. */
public final class CampaignSupplies {
    private record Entry(String id,String name){}
    private static final List<Entry> ENTRIES=read();
    private static final Map<String,Supplier<? extends Item>> ITEMS=new LinkedHashMap<>();
    private static List<Entry> read(){
        try(var in=CampaignSupplies.class.getResourceAsStream("/data/taczoperations/campaign/supplies.txt")){
            if(in==null)throw new IllegalStateException("Missing campaign supplies");
            var list=new ArrayList<Entry>();var ids=new HashSet<String>();
            for(var line:new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)).lines().toList()){
                if(line.isBlank())continue;
                var p=line.split("[|]",-1);
                if(p.length!=3||!p[0].matches("[0-9a-f]{24}")||!ids.add(p[0])||p[1].isBlank())
                    throw new IllegalStateException("Invalid supply entry");
                list.add(new Entry(p[0],p[1]));
            }
            return List.copyOf(list);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    public static void register(DeferredRegister.Items registry){
        for(var e:ENTRIES)ITEMS.put(e.id(),registry.registerItem("supply_"+e.id(),p->new Item(p){
            @Override public Component getName(ItemStack stack){return Component.literal(e.name());}
        },new Item.Properties().stacksTo(16)));
    }
    private static final String KAPPA_REFERENCE="5c093ca986f7740a1867ab12";
    static boolean rewardKnown(String id){return KAPPA_REFERENCE.equals(id)||ENTRIES.stream().anyMatch(e->e.id().equals(id))||CollectorItems.rewardKnown(id);}
    static ItemStack rewardStack(String id){
        if(KAPPA_REFERENCE.equals(id)){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","secure_kappa"));
            return item instanceof SecureCaseItem s&&s.tier()==SecureCaseItem.Tier.KAPPA?new ItemStack(item):ItemStack.EMPTY;
        }
        var item=ITEMS.get(id);return item==null?CollectorItems.rewardStack(id):new ItemStack(item.get());
    }
    static Optional<String> reference(ItemStack stack){
        var collector=CollectorItems.reference(stack);
        if(collector.isPresent())return collector;
        for(var row:ITEMS.entrySet())if(stack.is(row.getValue().get()))return Optional.of(row.getKey());
        return Optional.empty();
    }
    static boolean matches(ItemStack stack,String reference){return reference(stack).filter(reference::equals).isPresent();}
    private static final Set<String> BOSS_TROPHIES=Set.of("5c0e874186f7745dc7616606","6759af0f9c8a538dd70bfae6",
        "60a7acf20c5cb24b01346648","675aae1c26dc64e17800fee6","6540d2162ae6d96b540afcaf");
    static ItemStack bossTrophy(GuardRole role,net.minecraft.util.RandomSource rng){
        String id=switch(role){
            case KILLA->rng.nextInt(20)==0?"6759af0f9c8a538dd70bfae6":"5c0e874186f7745dc7616606";
            case FOREMAN->rng.nextInt(20)==0?"675aae1c26dc64e17800fee6":"60a7acf20c5cb24b01346648";
            case KOLLONTAY->"6540d2162ae6d96b540afcaf";
            default->null;
        };
        if(id==null)return ItemStack.EMPTY;
        var item=ITEMS.get(id);
        return item==null?ItemStack.EMPTY:new ItemStack(item.get());
    }
    static void addLoot(Container c,Random rng,int chance){
        if(rng.nextInt(100)>=chance)return;
        var free=new ArrayList<Integer>();
        for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty())free.add(i);
        var ordinary=ENTRIES.stream().filter(e->!BOSS_TROPHIES.contains(e.id())).toList();
        if(free.isEmpty()||ordinary.isEmpty())return;
        var e=ordinary.get(rng.nextInt(ordinary.size()));var item=ITEMS.get(e.id());
        if(item!=null)c.setItem(free.get(rng.nextInt(free.size())),new ItemStack(item.get(),1+rng.nextInt(2)));
    }
    private CampaignSupplies(){}
}
