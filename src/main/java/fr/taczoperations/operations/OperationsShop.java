package fr.taczoperations.operations;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public enum OperationsShop {
    ARMORER("Armurier", "Armes, munitions et accessoires"),
    MEDIC("Médecin", "Soins et survie"),
    SALVAGER("Récupérateur", "Matériel de terrain"),
    BROKER("Courtier", "Renseignements et accès permanents");

    public final String title;
    public final String specialty;

    OperationsShop(String title, String specialty) {
        this.title = title;
        this.specialty = specialty;
    }

    public static int requiredPlayerLevel(int loyalty) {
        return switch (loyalty) { case 2 -> 20; case 3 -> 50; case 4 -> 100; default -> 1; };
    }

    public static int requiredReputation(int loyalty) {
        return switch (loyalty) { case 2 -> 200; case 3 -> 800; case 4 -> 2000; default -> 0; };
    }

    public static int reputationLevel(int points, int experience) {
        int level = MilitaryProgression.level(experience);
        for (int loyalty = 4; loyalty >= 2; loyalty--) {
            if (points >= requiredReputation(loyalty) && level >= requiredPlayerLevel(loyalty)) return loyalty;
        }
        return 1;
    }

    public static int nextReputation(int points, int experience) {
        return requiredReputation(Math.min(4,reputationLevel(points,experience)+1));
    }

    int reputation(ServerPlayer player) {
        var account = SupplyContract.account(player);
        long points = (long)Math.max(0,account.getInt("timed_reputation_"+ordinal()))+Math.max(0,account.getInt("merchant_order_rep_"+ordinal()));
        for (SupplyContract contract : SupplyContract.values()) {
            if (contract.merchant() != this) continue;
            String key = "completed_" + contract.name();
            int count = account.contains(key) ? account.getInt(key)
                : contract.status(player) == SupplyContract.COMPLETED ? 1 : 0;
            points += (long)Math.max(0, count) * contract.reputationReward();
        }
        return (int)Math.min(Integer.MAX_VALUE, points);
    }

    public enum Action {
        BUY("Acheter"), SELL("Vendre"), UNLOCK("Débloquer"), REPAIR("Réparer");
        public final String label;
        Action(String label) { this.label = label; }
    }

    public record Offer(String title, int price, Action action, Supplier<ItemStack> stack,
                        RaidRelic key, int rankXp, int minCondition, int maxCondition) {
        public Offer(String title,int price,Action action,Supplier<ItemStack> stack,RaidRelic key,int rankXp){
            this(title,price,action,stack,key,rankXp,-1,-1);
        }
        public boolean accessory(){return title.startsWith("Accessoire · ");}
        public int shopCategory(){
            if(action==Action.REPAIR||action==Action.UNLOCK)return 6;
            if(accessory())return 3;
            var item=stack.get().getItem();
            if(item instanceof com.tacz.guns.api.item.IGun)return 1;
            if(item instanceof com.tacz.guns.api.item.IAmmo)return 2;
            if(item instanceof net.minecraft.world.item.ArmorItem||item instanceof FieldPack)return 4;
            if(item instanceof FieldMedicine||item==Items.GOLDEN_APPLE||item==Items.POTION)return 5;
            return 6;
        }
        public int loyaltyLevel() {
            if (action != Action.BUY) return 1;
            int level = rankXp >= 180 ? 4 : rankXp >= 65 ? 3 : rankXp > 0 ? 2 : 1;
            return key == null ? level : Math.max(level, key == RaidRelic.OMEGA ? 4 : key == RaidRelic.CHIP ? 3 : 2);
        }
        public String requirement() {
            return (loyaltyLevel() > 1 ? "Fidélité " + loyaltyLevel() : "")
                + (key != null && action == Action.BUY ? (loyaltyLevel() > 1 ? " · " : "") + key.title : "");
        }
    }

    public List<Offer> offers() {
        var offers = new ArrayList<Offer>();
        switch (this) {
            case ARMORER -> {
                offers.add(buy("Grenade de raid EFM",180,()->CampaignSupplies.rewardStack(RaidGrenade.REFERENCE),null,20));
                offers.add(new Offer("Entretien · arme tenue en main",0,Action.REPAIR,()->new ItemStack(Items.IRON_INGOT),null,0));
                // Build a single authoritative catalogue from the installed TaCZ packs.
                // Each calibre inherits the lowest tier of a weapon using it, with
                // a minimum tier for the calibre itself. No duplicate cheap ammo offers.
                var ammoLevels = new java.util.TreeMap<ResourceLocation,Integer>(
                    java.util.Comparator.comparing(ResourceLocation::toString));
                for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().toString())).toList()) {
                    ResourceLocation id = entry.getKey();
                    var data = entry.getValue().getGunData();
                    if (data == null || data.getFireModeSet().isEmpty()) continue;
                    int rounds = Math.max(1, data.getAmmoAmount());
                    int tier = weaponTier(id,data.getAmmoId(),rounds);
                    int price = switch (tier) {
                        case 1 -> 380 + Math.min(400,rounds*12);
                        case 2 -> 950 + Math.min(950,rounds*20);
                        case 3 -> 1600 + Math.min(1600,rounds*30);
                        default -> 2900 + Math.min(4500,rounds*40);
                    };
                    offers.add(buy(id.getPath().replace('_',' ').toUpperCase(java.util.Locale.ROOT),price,
                        () -> weapon(id,rounds),null,tierRequirement(tier)));
                    if (data.getAmmoId()!=null) ammoLevels.merge(data.getAmmoId(),tier,Math::min);
                }
                for (var entry : ammoLevels.entrySet()) {
                    ResourceLocation ammoId=entry.getKey();
                    int tier=Math.max(calibreTier(ammoId),entry.getValue());
                    int count=tier==4?10:30;
                    int price=switch(tier){case 1->65;case 2->95;case 3->125;default->160;};
                    offers.add(buy("Munitions · "+ammoId.getPath().replace('_',' '),price,
                        () -> AmmoItemBuilder.create().setId(ammoId).setCount(count).build(),null,tierRequirement(tier)));
                }
                // Limited, stable selection of real installed TaCZ accessories.
                for(var accessory:ArmorerAccessories.catalogue()){
                    offers.add(buy("Accessoire · "+accessory.title(),accessory.price(),
                        ()->AttachmentItemBuilder.create().setId(accessory.id()).build(),
                        accessory.key(),tierRequirement(accessory.tier())));
                }
            }
            case MEDIC -> {
                offers.add(buy("Bandage",45,()->medical("bandage"),null,0));
                offers.add(buy("Attelle",95,()->medical("splint"),null,0));
                offers.add(buy("Kit hémostatique",120,()->medical("hemostat"),null,0));
                offers.add(buy("Kit de premiers secours",180,()->medical("medkit"),null,0));
                offers.add(buy("Salewa",150,()->medical("salewa"),null,0));
                offers.add(buy("Bouteille d'eau",65,()->net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.WATER),null,0));
                offers.add(buy("Rations de terrain", 80, () -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations","field_ration")),4), null, 0));
                offers.add(buy("Soupe de survie", 55, () -> new ItemStack(Items.RABBIT_STEW), null, 0));
                offers.add(buy("Pomme dorée", 190, () -> new ItemStack(Items.GOLDEN_APPLE), RaidRelic.WATCH, 0));
                offers.add(buy("Potion de soin", 140, () -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                    Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING), null, 0));
                offers.add(new Offer("Reprise · pomme dorée", 45, Action.SELL, () -> new ItemStack(Items.GOLDEN_APPLE), null, 0));
            }
            case SALVAGER -> {
                offers.add(new Offer("Plaque nominative PMC",RaidRelic.PLAQUE.price,Action.SELL,()->medical("military_tag"),null,0));
                int[] bagPrices={500,1200,3000,6000,12000},bagRanks={0,20,20,65,180};
                String[] bags={"Petit sac","Sac de patrouille","Sac tactique","Sac militaire","Sac d'expédition"};
                for(int i=0;i<bags.length;i++){
                    final int tier=i;
                    offers.add(buy(bags[i]+" · "+FieldPack.CAPACITY[i]+" cases",bagPrices[i],()->FieldPack.create(false,tier),null,bagRanks[i]));
                    offers.add(new Offer("Reprise · "+bags[i],bagPrices[i]*30/100,Action.SELL,()->FieldPack.create(false,tier),null,0));
                }
                int[] rigPrices={700,1800},rigRanks={0,20};
                for(int i=0;i<rigPrices.length;i++){
                    final int tier=i;
                    offers.add(buy("Gilet tactique · "+FieldPack.RIG_CAPACITY[i]+" cases",rigPrices[i],()->FieldPack.create(true,tier),null,rigRanks[i]));
                    offers.add(new Offer("Reprise · Gilet "+FieldPack.RIG_CAPACITY[i]+" cases",rigPrices[i]*30/100,Action.SELL,()->FieldPack.create(true,tier),null,0));
                }
                offers.add(buy("Lot de torches", 45, () -> new ItemStack(Items.TORCH, 24), RaidRelic.PLAQUE, 0));
                offers.add(buy("Lot de flèches", 65, () -> new ItemStack(Items.ARROW, 24), RaidRelic.RADIO, 0));
                offers.add(buy("Veste en cuir", 160, () -> new ItemStack(Items.LEATHER_CHESTPLATE), null, 0));
                for (var slot : BallisticVest.SLOTS) {
                    int percent = switch (slot) { case HEAD -> 80; case LEGS -> 75; case FEET -> 50; default -> 100; };
                    for (int tier=2;tier<=4;tier++) {
                        final int t = tier;
                        int base = tier==2?900:tier==3?2400:6500;
                        offers.add(buy(BallisticVest.name(slot)+" · Classe "+tier,base*percent/100,
                            () -> BallisticVest.create(slot,t,BallisticVest.classColor(t)),null,tier==2?20:tier==3?100:190));
                    }
                    offers.add(new Offer("Réparation · "+BallisticVest.name(slot)+" portée", 300*percent/100,
                        Action.REPAIR, () -> new ItemStack(BallisticVest.item(slot)),null,0));
                }
                for(var slot:BallisticVest.SLOTS){
                    int percent=switch(slot){case HEAD->80;case LEGS->75;case FEET->50;default->100;};
                    for(int tier=2;tier<=6;tier++){
                        final int t=tier;
                        int base=switch(t){case 2->900;case 3->2400;case 4->6500;case 5->12000;default->24000;};
                        addConditionSales(offers,BallisticVest.name(slot)+" · Classe "+t,Math.max(1,base*percent*30/10000),
                            ()->BallisticVest.create(slot,t,BallisticVest.classColor(t)));
                    }
                }
                offers.add(new Offer("Reprise · Sac de raid",4500,Action.SELL,()->FieldPack.create(false,5),null,0));
                offers.add(new Offer("Reprise · Gilet élite",900,Action.SELL,()->FieldPack.create(true,2),null,0));
                // Class 5 is raid loot only.
                offers.add(buy("Lunettes de vision nocturne", 15000, () -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("taczoperations:night_vision_goggles"))), null, 180));
                offers.add(buy("Lunettes thermiques", 60000, () -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("taczoperations:thermal_goggles"))), null, 180));
                offers.add(buy("Casque en cuir", 95, () -> new ItemStack(Items.LEATHER_HELMET), null, 0));
                offers.add(buy("Bottes en cuir", 85, () -> new ItemStack(Items.LEATHER_BOOTS), null, 0));
                offers.add(buy("Casque en mailles", 250, () -> new ItemStack(Items.CHAINMAIL_HELMET), null, 20));
                offers.add(buy("Bottes en mailles", 230, () -> new ItemStack(Items.CHAINMAIL_BOOTS), null, 20));
                offers.add(buy("Casque en fer", 460, () -> new ItemStack(Items.IRON_HELMET), null, 65));
                offers.add(buy("Plastron en fer", 850, () -> new ItemStack(Items.IRON_CHESTPLATE), null, 90));
                offers.add(buy("Jambières en fer", 680, () -> new ItemStack(Items.IRON_LEGGINGS), null, 75));
                offers.add(buy("Bottes en fer", 370, () -> new ItemStack(Items.IRON_BOOTS), null, 50));
                offers.add(buy("Casque en diamant", 1450, () -> new ItemStack(Items.DIAMOND_HELMET), null, 190));
                offers.add(buy("Plastron en diamant", 2600, () -> new ItemStack(Items.DIAMOND_CHESTPLATE), null, 280));
                offers.add(buy("Jambières en mailles", 350, () -> new ItemStack(Items.CHAINMAIL_LEGGINGS), RaidRelic.DRIVE, 0));
                offers.add(buy("Veste en mailles", 400, () -> new ItemStack(Items.CHAINMAIL_CHESTPLATE), RaidRelic.CASE, 0));
                for (RaidRelic relic : List.of(RaidRelic.PLAQUE, RaidRelic.RADIO, RaidRelic.WATCH, RaidRelic.CIRCUIT, RaidRelic.DRIVE)) {
                    offers.add(sale(relic));
                }
            }
            case BROKER -> {
                offers.add(buy("Ravitaillement confidentiel", 220, () -> new ItemStack(Items.GOLDEN_CARROT, 8),
                    RaidRelic.DOCUMENTS, 0));
                for (RaidRelic relic : RaidRelic.values()) {
                    offers.add(sale(relic));
                    offers.add(new Offer("Accès · " + relic.title, 0, Action.UNLOCK, relic::stack, relic, 0));
                }
            }
        }
        for(var loot:TradeLoot.values())if(loot.merchant()==this)
            offers.add(new Offer(loot.title,loot.price,Action.SELL,loot::stack,null,0));
        if (this == ARMORER) {
            offers.sort(java.util.Comparator.comparingInt(Offer::loyaltyLevel));
            for (Offer offer : List.copyOf(offers)) {
                if (offer.action != Action.BUY) continue;
                ItemStack sample = offer.stack.get();
                if(sample.getItem() instanceof com.tacz.guns.api.item.IGun)
                    addConditionSales(offers,offer.title,Math.max(1,offer.price*30/100),offer.stack);
                else if(sample.getItem() instanceof com.tacz.guns.api.item.IAmmo||sample.getItem() instanceof com.tacz.guns.api.item.IAttachment)
                    offers.add(new Offer("Reprise · "+offer.title,Math.max(1,offer.price*30/100/Math.max(1,sample.getCount())),Action.SELL,offer.stack,null,0));
            }
        }
        appendEfmResales(offers);
        return offers;
    }

    private static void addConditionSales(List<Offer> offers,String name,int price,Supplier<ItemStack> stack){
        int[] low={75,40,1,0},high={100,74,39,0},rate={100,70,35,10};
        String[] labels={"75–100 %","40–74 %","1–39 %","0 %"};
        for(int i=0;i<low.length;i++)offers.add(new Offer("Reprise · "+name+" · "+labels[i],
            Math.max(1,price*rate[i]/100),Action.SELL,stack,null,0,low[i],high[i]));
    }
    private static int ballisticTier(ItemStack stack){
        for(var slot:BallisticVest.SLOTS){int tier=BallisticVest.tier(stack,slot);if(tier>0)return tier;}
        return 0;
    }
    private static int resaleCondition(ItemStack stack){
        if(ballisticTier(stack)>0)return BallisticVest.condition(stack);
        if(stack.getItem() instanceof com.tacz.guns.api.item.IGun)return WeaponCondition.condition(stack)/100;
        return stack.isDamageableItem()?Math.clamp(100-stack.getDamageValue()*100/Math.max(1,stack.getMaxDamage()),0,100):100;
    }
    // A stable identity for resale offers, independent of display language and stack count.
    public static String saleKey(ItemStack stack){
        String item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int tier=ballisticTier(stack);
        if(tier>0)return "armor/"+item+"/"+tier;
        if(stack.getItem() instanceof com.tacz.guns.api.item.IGun gun)return "gun/"+gun.getGunId(stack);
        if(stack.getItem() instanceof com.tacz.guns.api.item.IAmmo ammo)return "ammo/"+ammo.getAmmoId(stack);
        if(stack.getItem() instanceof com.tacz.guns.api.item.IAttachment attachment)return "attachment/"+attachment.getAttachmentId(stack);
        return "item/"+item;
    }
    static String quoteKey(Offer offer,ItemStack sample){
        return saleKey(sample)+"/"+offer.minCondition()+"/"+offer.maxCondition();
    }
    private static OperationsShop resaleMerchant(ItemStack stack,String path){
        if(stack.getItem() instanceof FieldMedicine||path.equals("field_ration"))return MEDIC;
        if(path.equals("raid_grenade")||path.equals("tactical_headset"))return ARMORER;
        if(stack.getItem() instanceof FieldPack||stack.getItem() instanceof SecureCaseItem||stack.getItem() instanceof KeyRingItem
            ||path.endsWith("_goggles")||path.endsWith("_key")||path.equals("military_tag"))return SALVAGER;
        for(var item:TradeLoot.values())if(path.equals(item.id))return item.merchant();
        return BROKER;
    }
    private static String stableName(ItemStack stack,String path){
        if(path.startsWith("supply_")||path.startsWith("collector_")||stack.getItem() instanceof SecureCaseItem)
            return stack.getHoverName().getString();
        return switch(path){
            case "bandage"->"Bandage";case "splint"->"Attelle";case "hemostat"->"Kit hémostatique";
            case "medkit"->"Kit de premiers secours";case "salewa"->"Salewa";case "field_ration"->"Ration de terrain";
            case "raid_grenade"->"Grenade de raid EFM";case "tactical_headset"->"Casque audio tactique";
            case "night_vision_goggles"->"Lunettes de vision nocturne";case "thermal_goggles"->"Lunettes thermiques";
            case "key_ring"->"Trousseau";case "forest_key"->"Clé de la Forêt noire";case "industrial_key"->"Clé industrielle";
            default->path.replace('_',' ');
        };
    }
    private static int fallbackPrice(ItemStack stack,String path){
        if(stack.getItem() instanceof SecureCaseItem c)return 100*c.tier().capacity;
        if(path.startsWith("collector_"))return 500;
        if(path.startsWith("supply_"))return 100;
        return switch(path){case "tactical_headset"->250;case "key_ring"->150;case "forest_key","industrial_key"->150;default->25;};
    }
    private void appendEfmResales(List<Offer> offers){
        var registry=net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var known=new java.util.HashSet<String>();
        var buyPrices=new java.util.HashMap<String,Integer>();
        for(var offer:offers){
            var stack=offer.stack().get();if(stack.isEmpty())continue;
            String key=saleKey(stack);
            if(offer.action()==Action.SELL)known.add(key);
            else if(offer.action()==Action.BUY)buyPrices.merge(key,Math.max(1,offer.price()*30/100/Math.max(1,stack.getCount())),Math::min);
        }
        // Include all EFM registry items, including future campaign supplies, without duplicate offers.
        for(var id:registry.keySet().stream().filter(k->k.getNamespace().equals("taczoperations"))
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString)).toList()){
            var item=registry.get(id);var sample=new ItemStack(item);
            if(sample.isEmpty()||resaleMerchant(sample,id.getPath())!=this||!known.add(saleKey(sample)))continue;
            int price=buyPrices.getOrDefault(saleKey(sample),fallbackPrice(sample,id.getPath()));
            offers.add(new Offer("Reprise · "+stableName(sample,id.getPath()),price,Action.SELL,()->new ItemStack(item),null,0));
        }
    }

    private static int tierRequirement(int tier) {
        return switch(tier) { case 1 -> 0; case 2 -> 20; case 3 -> 80; default -> 180; };
    }

    private static int calibreTier(ResourceLocation id) {
        if (id==null) return 4;
        String s=id.getPath().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
        if (java.util.Set.of("9mm","9x19","9x19mm","45acp","45auto","380acp","22lr","22longrifle","9x18","9x18mm").contains(s)) return 1;
        if (java.util.Set.of("12g","12ga","12gauge","20g","20ga","357magnum","357","44magnum","44mag","57x28","57x28mm","46x30","46x30mm").contains(s)) return 2;
        if (java.util.Set.of("762x39","762x39mm","556x45","556x45mm","545x39","545x39mm","300blk","300blackout","762x35","223","223rem").contains(s)) return 3;
        return 4; // high-power and unclassified addon ammunition unlock last
    }

    static int weaponTier(ResourceLocation id, ResourceLocation ammo, int rounds) {
        String s=id.getPath().toLowerCase(java.util.Locale.ROOT);
        int base=calibreTier(ammo);
        if (s.matches(".*(sniper|awp|barrett|m82|m107|m249|m240|pkm|minigun|launcher|rpg|scar_h|sr25|m110|svd|m200|intervention|mg42|mg3).*")) return 4;
        if (s.matches(".*(glock|m1911|p226|p320|m9|beretta|makarov|usp|cz75|pistol|revolver).*")) return base;
        if (s.matches(".*(mp5|mp7|mp9|ump|uzi|vector|p90|pp19|bizon|smg|m870|shotgun|spas|aa12|ksg).*")) return Math.max(2,base);
        if (s.matches(".*(m4a1|hk416|scar_l|ak12|aug|famas|g36|ar15|m16|mk18|sopmod|as_val|vss).*")) return Math.max(3,base);
        return base==1 && rounds>18 ? 2 : base;
    }

    private static ItemStack medical(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations",id)));
    }

    private static Offer sale(RaidRelic relic) {
        return new Offer(relic.title, relic.price, Action.SELL, relic::stack, null, 0);
    }

    private static Offer buy(String title, int credits, Supplier<ItemStack> stack, RaidRelic key, int xp) {
        return new Offer(title, credits, Action.BUY, stack, key, xp);
    }

    static ItemStack weapon(String id, int rounds) {
        return weapon(tacz(id), rounds);
    }

    static ItemStack weapon(ResourceLocation id, int rounds) {
        var index = TimelessAPI.getCommonGunIndex(id);
        if (index.isEmpty() || index.get().getGunData().getFireModeSet().isEmpty())
            return ItemStack.EMPTY;
        var data = index.get().getGunData();
        return GunItemBuilder.create().setId(id).setFireMode(data.getFireModeSet().getFirst())
            .setAmmoCount(Math.min(rounds, Math.max(1, data.getAmmoAmount())))
            .setAmmoInBarrel(rounds > 0).build();
    }

    public static ItemStack ammo(String id, int count) {
        return AmmoItemBuilder.create().setId(tacz(id)).setCount(count).build();
    }

    static ItemStack attachment(String id) {
        return AttachmentItemBuilder.create().setId(tacz(id)).build();
    }

    private static ResourceLocation tacz(String id) {
        return ResourceLocation.fromNamespaceAndPath("tacz", id);
    }

    static int unlocks(ServerPlayer player) {
        int bits = 0;
        for (RaidRelic relic : RaidRelic.values()) {
            if (SupplyContract.account(player).getBoolean("unlock_" + relic.id)) bits |= 1 << relic.ordinal();
        }
        return bits;
    }

    public static boolean matchesSale(ItemStack sample,ItemStack candidate){
        if(sample.isEmpty()||candidate.isEmpty())return false;
        // Do not discard any stored items or mistake tagged EFM armor for ordinary leather.
        if(candidate.getItem() instanceof FieldPack&&FieldPack.contents(candidate).stream().anyMatch(item->!item.isEmpty()))return false;
        if(candidate.getItem() instanceof KeyRingItem&&KeyRingItem.contents(candidate).stream().anyMatch(item->!item.isEmpty()))return false;
        if(candidate.getItem() instanceof SecureCaseItem&&!RaidSecurity.emptyCase(candidate))return false;
        if(candidate.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA))return false;
        return saleKey(sample).equals(saleKey(candidate));
    }
    public static boolean matchesSale(Offer offer,ItemStack sample,ItemStack candidate){
        if(!matchesSale(sample,candidate))return false;
        if(offer.minCondition()<0)return true;
        int condition=resaleCondition(candidate);
        return condition>=offer.minCondition()&&condition<=offer.maxCondition();
    }
    static int saleCount(ServerPlayer player,Offer offer){return saleCount(CarriedStorage.of(player),offer);}
    static int saleCount(CarriedStorage storage,Offer offer){
        if(offer.action()!=Action.SELL)return 0;
        ItemStack sample=offer.stack().get();
        return storage.count(s->matchesSale(offer,sample,s));
    }
    public static int purchasePrice(Offer offer,int discount){
        if(offer.action()!=Action.BUY||offer.price()<=0)return offer.price();
        return (int)Math.max(1,((long)offer.price()*(100-Math.clamp(discount,0,10))+99)/100);
    }
    String trade(ServerPlayer player,int index,long quotedDay){
        return trade(player,index,quotedDay,1,false);
    }
    String trade(ServerPlayer player,int index,long quotedDay,int quantity,boolean bulk) {
        if(!PlayerMarket.available(player))return "Transactions disponibles uniquement hors raid, en survie.";
        var offers = offers();
        if (index < 0 || index >= offers.size()) return "Offre indisponible.";
        Offer offer = offers.get(index);
        if(bulk&&offer.action!=Action.SELL)return "Cette offre ne permet pas la vente en lot.";
        if(offer.action!=Action.SELL){
            int experience=SupplyContract.experienceEarned(player),loyalty=offer.loyaltyLevel();
            if(reputationLevel(reputation(player),experience)<loyalty)
                return "Fidélité "+loyalty+" requise.";
        }
        var account = SupplyContract.account(player);
        if (offer.action == Action.UNLOCK) {
            String key = "unlock_" + offer.key.id;
            if (account.getBoolean(key)) return "Accès déjà débloqué.";
            if (!SupplyContract.consume(player, offer.key.item(), 1)) return "Rapporte l’objet requis.";
            account.putBoolean(key, true);
            return "Accès permanent débloqué.";
        }
        ItemStack stack = offer.stack.get();
        if (stack.isEmpty()) return "Article indisponible dans cette installation TaCZ.";
        int balance = account.getInt("credits");
        if (offer.action == Action.REPAIR) {
            if (this == ARMORER) return WeaponCondition.repair(player);
            if (this != SALVAGER) return "Service indisponible.";
            for (var slot : BallisticVest.SLOTS) {
                if (!stack.is(BallisticVest.item(slot))) continue;
                ItemStack worn = player.getItemBySlot(slot);
                if (BallisticVest.tier(worn,slot) == 0) return "Équipe d’abord la protection balistique à réparer.";
                if (BallisticVest.condition(worn) == 100 && worn.getDamageValue() == 0) return "Protection déjà en parfait état.";
                if (balance < offer.price) return "Crédits insuffisants.";
                if (!BallisticVest.repair(worn,slot)) return "Réparation impossible.";
                account.putInt("credits", balance-offer.price);
                TradeJournal.record(player,"Réparation",title,offer.title(),1,-offer.price());
                player.getInventory().setChanged();
                return "Protection réparée · état 100/100.";
            }
            return "Protection indisponible.";
        }
        if (offer.action == Action.SELL) {
            long today=ResaleMarket.day();
            if(quotedDay!=today)return "Les tarifs ont changé. Vérifie le nouveau prix avant de vendre.";
            int price=ResaleMarket.price(offer,today);
            var storage=CarriedStorage.of(player);
            int available=storage.count(s->matchesSale(offer,stack,s));
            if(quantity<=0||available<quantity)return "Quantité indisponible. Vérifie ton inventaire.";
            if(bulk&&available!=quantity)return "Quantité modifiée. Vérifie le nouveau montant.";
            long total=(long)price*quantity;
            if(total>Integer.MAX_VALUE-(long)balance)return "Solde maximal atteint.";
            // Validate the entire quote before touching any stack.
            if(!storage.consume(s->matchesSale(offer,stack,s),quantity))return "Quantité indisponible.";
            account.putInt("credits",balance+(int)total);
            TradeJournal.record(player,"Vente",title,offer.title(),quantity,total);
            CampaignRuntime.recordMerchantSale(player,name().toLowerCase(java.util.Locale.ROOT),quantity,this==SALVAGER&&(stack.getItem() instanceof FieldPack||ballisticTier(stack)>0));
            PassiveSkills.trade(player,total);
            return "Vente · "+quantity+" objet(s) · +"+total+" crédits.";
        }
        int experience = SupplyContract.experienceEarned(player);
        int loyalty = offer.loyaltyLevel();
        if (MilitaryProgression.level(experience) < requiredPlayerLevel(loyalty))
            return "Niveau militaire " + requiredPlayerLevel(loyalty) + " requis.";
        if (reputationLevel(reputation(player),experience) < loyalty)
            return "Fidélité " + loyalty + " requise · " + requiredReputation(loyalty) + " rép.";
        if (offer.key != null && !account.getBoolean("unlock_" + offer.key.id))
            return "Accès permanent requis.";
        if(quantity<1||quantity>64)return "Quantité invalide.";
        long total=(long)purchasePrice(offer,PassiveSkills.bonus(player,PassiveSkills.Skill.TRADE))*quantity;
        if(total>balance||total<0)return "Crédits insuffisants.";
        var inventory = player.getInventory();
        int capacity = inventory.items.stream().mapToInt(slot -> slot.isEmpty() ? Math.min(inventory.getMaxStackSize(),stack.getMaxStackSize())
            : ItemStack.isSameItemSameComponents(slot, stack) ? Math.max(0,Math.min(inventory.getMaxStackSize(),slot.getMaxStackSize()) - slot.getCount()) : 0).sum();
        int purchased=stack.getCount()*quantity;
        if(capacity<purchased)return "Inventaire plein : libère des cases pour le lot complet.";
        for(int i=0;i<quantity;i++)inventory.add(stack.copy());
        account.putInt("credits",balance-(int)total);
        TradeJournal.record(player,"Achat",title,offer.title(),purchased,-total);
        PassiveSkills.trade(player,-total);
        return "Achat · "+purchased+" objet(s) · -"+total+" crédits.";
    }
}
