package fr.taczoperations.operations;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;
import java.util.Random;

public enum TradeLoot {
    SCREWDRIVER("screwdriver","Tournevis",90,0,1,24),
    WRENCH("wrench","Clé à molette",140,0,1,22),
    FLAT_PLIERS("flat_pliers","Pince plate",160,0,1,20),
    MEASURING_TAPE("measuring_tape","Mètre ruban",110,0,1,22),
    HAND_DRILL("hand_drill","Perceuse manuelle",420,1,1,9),
    TOOL_SET("tool_set","Trousse à outils",650,2,1,5),
    FUEL_CAN("fuel_can","Bidon de carburant",320,1,1,12),
    AIR_FILTER("air_filter","Filtre à air",380,1,1,11),
    WATER_FILTER("water_filter","Filtre à eau",480,2,1,6),
    ARAMID_FABRIC("aramid_fabric","Tissu aramide",240,1,1,14),
    RIPSTOP_FABRIC("ripstop_fabric","Tissu ripstop",130,0,1,22),
    PARACORD("paracord","Paracorde",280,1,1,12),
    SALINE("saline","Sérum physiologique",140,0,0,22),
    MEDICAL_TOOLS("medical_tools","Instruments médicaux",350,1,0,12),
    SURGICAL_KIT("surgical_kit","Trousse chirurgicale",750,2,0,5),
    OPHTHALMOSCOPE("ophthalmoscope","Ophtalmoscope",900,2,0,4),
    BLOOD_SET("blood_set","Kit de transfusion",450,1,0,9),
    DEFIBRILLATOR("defibrillator","Défibrillateur",2600,3,0,2),
    LEDX("ledx","Scanner médical LEDX",5500,4,0,1),
    TEA_BOX("tea_box","Boîte de thé",100,0,3,24),
    BEAR_PLUSH("bear_plush","Ourson en peluche",500,2,3,7),
    MASTER_TOOLS("master_tools","Kit d'outillage professionnel",1100,2,1,4),
    METAL_SCISSORS("metal_scissors","Ciseaux de précision",150,0,0,22),
    FILTER_CARTRIDGE("filter_cartridge","Cartouche filtrante FP-100",1250,2,1,4);

    public final String id,title;
    public final int price,tier,category,weight;
    private Supplier<? extends Item> item;
    TradeLoot(String id,String title,int price,int tier,int category,int weight){
        this.id=id;this.title=title;this.price=price;this.tier=tier;this.category=category;this.weight=weight;
    }
    public static void register(DeferredRegister.Items registry){
        for(var loot:values())loot.item=registry.registerItem(loot.id,p->new LootItem(p,loot),
            new Item.Properties().stacksTo(loot.tier>=3?1:loot.tier==2?4:16).rarity(switch(loot.tier){
                case 0->net.minecraft.world.item.Rarity.COMMON;
                case 1->net.minecraft.world.item.Rarity.UNCOMMON;
                case 2->net.minecraft.world.item.Rarity.RARE;
                default->net.minecraft.world.item.Rarity.EPIC;
            }));
    }
    private static final class LootItem extends Item{
        private final TradeLoot loot;
        LootItem(Properties properties,TradeLoot loot){super(properties);this.loot=loot;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> text,net.minecraft.world.item.TooltipFlag flag){
            text.add(net.minecraft.network.chat.Component.literal(new String[]{"Commun","Peu commun","Rare","Épique","Légendaire"}[loot.tier]+" · "+loot.price+" crédits (référence)"));
            text.add(net.minecraft.network.chat.Component.literal("Objet de revente · "+loot.merchant().title));
        }
    }
    public ItemStack stack(){return new ItemStack(item.get());}
    public OperationsShop merchant(){return category==0?OperationsShop.MEDIC:category==1?OperationsShop.SALVAGER:OperationsShop.BROKER;}
    static TradeLoot roll(Random rng,int category,int minimumTier){
        var candidates=java.util.Arrays.stream(values()).filter(l->l.category==category&&l.tier>=minimumTier).toList();
        if(candidates.isEmpty())candidates=java.util.Arrays.stream(values()).filter(l->l.category==category).toList();
        if(candidates.isEmpty())return null;
        int draw=rng.nextInt(candidates.stream().mapToInt(l->l.weight).sum());
        for(var loot:candidates){draw-=loot.weight;if(draw<0)return loot;}
        throw new IllegalStateException("Invalid trade loot pool");
    }
}
