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
    private static final Map<String,String> EQUIPMENT=Map.of("59e763f286f7742ee57895da","backpack_expedition","59e7643b86f7742cbf2c109a","rig_tactical","5648a69d4bdc2ded0b8b457b","rig_elite");
    // Real EFM medical items, each mapped to exactly one canonical reference ID.
    private static final Map<String,String> MEDICINE=Map.of(
        "544fb45d4bdc2dee738b4568","salewa","5755356824597772cb798962","medkit",
        "544fb25a4bdc2dfb738b4567","bandage","544fb3364bdc2d34748b456a","splint",
        "60098af40accd37ef2175f27","hemostat");
    private static final Map<String,Integer> ARMOR=Map.of("59e7635f86f7742cbf2c1095",2,"5c0e655586f774045612eeb2",4,"5ab8e79e86f7742d8b372e78",5,"545cdb794bdc2d3a198b456a",6);
    private static final String SHOTGUN="5a38e6bac4a2826c6e06d79b";
    private static final Map<String,String> BASE_GUNS=Map.of("5644bd2b4bdc2d3b4c8b4572","ak47","5447a9cd4bdc2dbd208b4567","m4a1","5448bd6b4bdc2dfc2f8b4569","glock_17");
    private static final String THERMAL="5a1eaa87fcdbcb001865f75e",HELMET5="5ca20ee186f774799474abc2",AMMO556="60194943740c5d77f6705eea";
    private static final String EPSILON="59db794186f77448bc595262", HELMET3="5645bc214bdc2d363b8b4571";
    static boolean rewardKnown(String id){return RaidGrenade.REFERENCE.equals(id)|| EPSILON.equals(id)||HELMET3.equals(id)|| THERMAL.equals(id)||HELMET5.equals(id)||AMMO556.equals(id)|| "5645bcc04bdc2d363b8b4572".equals(id)||"5aa7d03ae5b5b00016327db5".equals(id)||BASE_GUNS.containsKey(id)||ARMOR.containsKey(id)||SHOTGUN.equals(id)||MEDICINE.containsKey(id)||id.equals("5b8683a486f77467f2423114")||EQUIPMENT.containsKey(id)||KAPPA_REFERENCE.equals(id)||ENTRIES.stream().anyMatch(e->e.id().equals(id))||CollectorItems.rewardKnown(id);}
    static ItemStack rewardStack(String id){
        if(RaidGrenade.REFERENCE.equals(id))return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","raid_grenade")));
        if(EPSILON.equals(id)){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","secure_epsilon"));
            return item instanceof SecureCaseItem s&&s.tier()==SecureCaseItem.Tier.EPSILON?new ItemStack(item):ItemStack.EMPTY;
        }
        if(HELMET3.equals(id))return BallisticVest.create(net.minecraft.world.entity.EquipmentSlot.HEAD,3,BallisticVest.classColor(3));
        if(THERMAL.equals(id))return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","thermal_goggles")));
        if(HELMET5.equals(id))return BallisticVest.create(net.minecraft.world.entity.EquipmentSlot.HEAD,5,BallisticVest.classColor(5));
        if(AMMO556.equals(id))return OperationsShop.ammo("556x45",1);
        if("5645bcc04bdc2d363b8b4572".equals(id))return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","tactical_headset")));
        if("5aa7d03ae5b5b00016327db5".equals(id))return BallisticVest.create(net.minecraft.world.entity.EquipmentSlot.HEAD,2,BallisticVest.classColor(2));
        if(BASE_GUNS.containsKey(id))return OperationsShop.weapon(BASE_GUNS.get(id),0);
        if(ARMOR.containsKey(id)){
            int tier=ARMOR.get(id);
            return BallisticVest.create(tier,BallisticVest.classColor(tier));
        }
        if(SHOTGUN.equals(id))return OperationsShop.weapon("m870",5);
        if(MEDICINE.containsKey(id)){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",MEDICINE.get(id)));
            return item instanceof FieldMedicine?new ItemStack(item):ItemStack.EMPTY;
        }
        if(id.equals("5b8683a486f77467f2423114"))return OperationsShop.weapon("ai_awp",5);
        if(KAPPA_REFERENCE.equals(id)){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","secure_kappa"));
            return item instanceof SecureCaseItem s&&s.tier()==SecureCaseItem.Tier.KAPPA?new ItemStack(item):ItemStack.EMPTY;
        }
        if(EQUIPMENT.containsKey(id)){
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",EQUIPMENT.get(id)));
            return item instanceof FieldPack?new ItemStack(item):ItemStack.EMPTY;
        }
        var item=ITEMS.get(id);return item==null?CollectorItems.rewardStack(id):new ItemStack(item.get());
    }
    private static final String BEAR_TAG="59f32bb586f774757e1e8442", USEC_TAG="59f32c3b86f77472a31742f0";
    static Optional<String> reference(ItemStack stack){
        if(stack.getItem() instanceof RaidGrenade)return Optional.of(RaidGrenade.REFERENCE);
        if(stack.getItem() instanceof SecureCaseItem c)
            return c.tier()==SecureCaseItem.Tier.EPSILON&&RaidSecurity.emptyCase(stack)?Optional.of(EPSILON):Optional.empty();
        if(BallisticVest.tier(stack,net.minecraft.world.entity.EquipmentSlot.HEAD)==3)return Optional.of(HELMET3);
        if(MilitaryTag.isTag(stack)){
            var custom=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if(custom==null)return Optional.empty();
            var data=custom.copyTag();
            if(!data.hasUUID("operator")||data.getInt("rank")<1)return Optional.empty();
            String faction=data.getString("faction");
            return switch(faction){case "BEAR"->Optional.of(BEAR_TAG);case "USEC"->Optional.of(USEC_TAG);default->Optional.empty();};
        }
        if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("taczoperations:thermal_goggles"))return Optional.of(THERMAL);
        if(BallisticVest.tier(stack,net.minecraft.world.entity.EquipmentSlot.HEAD)==5)return Optional.of(HELMET5);
        if(stack.getItem() instanceof com.tacz.guns.api.item.IAmmo ammo&&net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz","556x45").equals(ammo.getAmmoId(stack)))return Optional.of(AMMO556);
        if(BallisticVest.tier(stack,net.minecraft.world.entity.EquipmentSlot.HEAD)==2)return Optional.of("5aa7d03ae5b5b00016327db5");
        if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("taczoperations:tactical_headset"))return Optional.of("5645bcc04bdc2d363b8b4572");
        int armorClass=BallisticVest.tier(stack,net.minecraft.world.entity.EquipmentSlot.CHEST);
        if(armorClass>0){
            for(var e:ARMOR.entrySet())if(e.getValue()==armorClass)return Optional.of(e.getKey());
        }
        if(stack.getItem() instanceof com.tacz.guns.api.item.IGun gun){
            var id=gun.getGunId(stack);
            if(id!=null&&id.getNamespace().equals("tacz")){
                for(var e:BASE_GUNS.entrySet())if(id.getPath().equals(e.getValue()))return Optional.of(e.getKey());
            }
            if(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tacz","m870").equals(id))return Optional.of(SHOTGUN);
        }
        if(stack.getItem() instanceof FieldMedicine){
            String key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for(var e:MEDICINE.entrySet())if(key.equals("taczoperations:"+e.getValue()))return Optional.of(e.getKey());
        }
        if(stack.getItem() instanceof FieldPack&&FieldPack.contents(stack).stream().allMatch(ItemStack::isEmpty)){
            String key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            for(var e:EQUIPMENT.entrySet())if(key.equals("taczoperations:"+e.getValue()))return Optional.of(e.getKey());
        }
        var collector=CollectorItems.reference(stack);
        if(collector.isPresent())return collector;
        for(var row:ITEMS.entrySet())if(stack.is(row.getValue().get())&&!row.getKey().equals(BEAR_TAG)&&!row.getKey().equals(USEC_TAG))return Optional.of(row.getKey());
        return Optional.empty();
    }
    static boolean matches(ItemStack stack,String reference){return reference(stack).filter(reference::equals).isPresent();}
    private static final Set<String> BOSS_TROPHIES=Set.of("5c0e874186f7745dc7616606","6759af0f9c8a538dd70bfae6",
        "60a7acf20c5cb24b01346648","675aae1c26dc64e17800fee6","6540d2162ae6d96b540afcaf","5b3b713c5acfc4330140bd8d","5e997f0b86f7741ac73993e2","609e860ebd219504d8507525","628baf0b967de16aab5a4f36","628bc7fb408e2b2e9c0801b1","628b9c7d45122232a872358f","5fc64ea372b0dd78d51159dc");
    static ItemStack bossTrophy(GuardRole role,net.minecraft.util.RandomSource rng){
        String id=switch(role){
            case KILLA->rng.nextInt(20)==0?"6759af0f9c8a538dd70bfae6":"5c0e874186f7745dc7616606";
            case FOREMAN->rng.nextInt(20)==0?"675aae1c26dc64e17800fee6":"60a7acf20c5cb24b01346648";
            case KOLLONTAY->"6540d2162ae6d96b540afcaf";
            case RESHALA->"5b3b713c5acfc4330140bd8d";
            default->null;
        };
        if(id==null)return ItemStack.EMPTY;
        var item=ITEMS.get(id);
        return item==null?ItemStack.EMPTY:new ItemStack(item.get());
    }
    static ItemStack cultistTrophy(GuardRole role,String escortOf){
        return role==GuardRole.CULTIST_PRIEST||"CULTIST_PRIEST".equals(escortOf)?rewardStack("5fc64ea372b0dd78d51159dc"):ItemStack.EMPTY;
    }
    static ItemStack bossEquipmentTrophy(GuardRole role){
        String ref=switch(role){
            case SANITAR->"5e997f0b86f7741ac73993e2";
            case FOREMAN->"609e860ebd219504d8507525";
            case KNIGHT->"628baf0b967de16aab5a4f36";
            case BIRDEYE->"628bc7fb408e2b2e9c0801b1";
            case BIG_PIPE->"628b9c7d45122232a872358f";
            default->null;
        };
        return ref==null?ItemStack.EMPTY:rewardStack(ref);
    }
    static void addLoot(Container c,Random rng,int chance){
        if(rng.nextInt(100)>=chance)return;
        var free=new ArrayList<Integer>();
        for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty())free.add(i);
        var ordinary=ENTRIES.stream().filter(e->!BOSS_TROPHIES.contains(e.id())).toList();
        if(free.isEmpty())return;
        if(rng.nextInt(8)==0){
            var stack=rewardStack(RaidGrenade.REFERENCE);
            if(!stack.isEmpty()){stack.setCount(1+rng.nextInt(2));c.setItem(free.get(rng.nextInt(free.size())),stack);}
            return;
        }
        if(ordinary.isEmpty())return;
        var e=ordinary.get(rng.nextInt(ordinary.size()));var item=ITEMS.get(e.id());
        if(item!=null)c.setItem(free.get(rng.nextInt(free.size())),new ItemStack(item.get(),1+rng.nextInt(2)));
    }
    private CampaignSupplies(){}
}
