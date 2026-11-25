package fr.taczoperations.operations;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared escrow. All mutations run on the server thread, never from client state. */
public final class PlayerMarket extends SavedData {
    public static final int PAGE_SIZE=12, MAX_PRICE=100_000_000;
    private static final int MAX_LISTINGS=256, PER_PLAYER=10;
    private static final Factory<PlayerMarket> FACTORY=new Factory<>(PlayerMarket::new,PlayerMarket::read);
    private final Map<UUID,Listing> listings=new LinkedHashMap<>();
    private final Map<UUID,Long> proceeds=new LinkedHashMap<>();
    private record Listing(UUID id,UUID owner,String seller,int price,ItemStack item){}
    public record Offer(UUID id,String seller,int price,boolean own,ItemStack item){
        void write(RegistryFriendlyByteBuf b){
            b.writeUUID(id);b.writeUtf(seller);b.writeVarInt(price);b.writeBoolean(own);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b,item);
        }
        static Offer read(RegistryFriendlyByteBuf b){
            return new Offer(b.readUUID(),b.readUtf(),b.readVarInt(),b.readBoolean(),ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
        }
    }
    public record Page(List<Offer> offers,long pending,int ownCount,boolean ownOnly,ItemStack held,int category){
        public static Page empty(){return new Page(List.of(),0,0,false,ItemStack.EMPTY,0);}
        public void write(RegistryFriendlyByteBuf b){
            b.writeCollection(offers,(buf,offer)->offer.write(buf));b.writeVarLong(pending);
            b.writeVarInt(ownCount);b.writeBoolean(ownOnly);ItemStack.OPTIONAL_STREAM_CODEC.encode(b,held);b.writeVarInt(category);
        }
        public static Page read(RegistryFriendlyByteBuf b){
            return new Page(b.readList(Offer::read),b.readVarLong(),b.readVarInt(),b.readBoolean(),ItemStack.OPTIONAL_STREAM_CODEC.decode(b),b.readVarInt());
        }
    }
    static PlayerMarket of(ServerPlayer p){
        return p.server.overworld().getDataStorage().computeIfAbsent(FACTORY,"efm_player_market_v1");
    }
    static boolean available(ServerPlayer p){
        return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.level().dimension().equals(ForestExpedition.FOREST);
    }
    public static boolean eligible(ItemStack s){
        if(s.isEmpty()||s.getCount()>s.getMaxStackSize()||s.getItem() instanceof SecureCaseItem)return false;
        if(s.getItem() instanceof FieldPack)return FieldPack.contents(s).stream().allMatch(ItemStack::isEmpty);
        return !s.has(DataComponents.CONTAINER)&&!s.has(DataComponents.BUNDLE_CONTENTS)&&s.getItem().canFitInsideContainerItems();
    }
    private static boolean bounded(ServerPlayer p,ItemStack s){
        return eligible(s)&&s.saveOptional(p.registryAccess()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=1536;
    }
    private static int category(ItemStack s){
        var item=s.getItem();
        if(item instanceof com.tacz.guns.api.item.IGun)return 1;
        if(item instanceof com.tacz.guns.api.item.IAmmo)return 2;
        if(item instanceof com.tacz.guns.api.item.IAttachment)return 3;
        if(item instanceof net.minecraft.world.item.ArmorItem||item instanceof FieldPack)return 4;
        if(item instanceof FieldMedicine||s.has(DataComponents.FOOD)||s.has(DataComponents.POTION_CONTENTS))return 5;
        return 6;
    }
    private List<Listing> filtered(ServerPlayer p,boolean own,int category){
        return listings.values().stream().filter(l->(!own||l.owner.equals(p.getUUID()))&&(category==0||category(l.item)==category))
            .sorted(java.util.Comparator.comparingInt(Listing::price).thenComparing(Listing::id)).toList();
    }
    int pages(ServerPlayer p,boolean own,int category){
        return Math.max(1,(filtered(p,own,category).size()+PAGE_SIZE-1)/PAGE_SIZE);
    }
    private int ownCount(UUID owner){return (int)listings.values().stream().filter(l->l.owner.equals(owner)).count();}
    Page view(ServerPlayer p,int page,boolean own,int category){
        var all=filtered(p,own,category);
        int from=Math.min(all.size(),Math.max(0,page)*PAGE_SIZE);
        var rows=all.subList(from,Math.min(all.size(),from+PAGE_SIZE)).stream()
            .map(l->new Offer(l.id,l.seller,l.price,l.owner.equals(p.getUUID()),l.item.copy())).toList();
        return new Page(rows,Math.max(0,proceeds.getOrDefault(p.getUUID(),0L)),ownCount(p.getUUID()),own,bounded(p,p.getMainHandItem())?p.getMainHandItem().copy():ItemStack.EMPTY,category);
    }
    String list(ServerPlayer p,ItemStack quote,int price){
        if(!available(p))return "Marché disponible hors raid, en survie.";
        if(price<1||price>MAX_PRICE)return "Prix invalide (1 à "+MAX_PRICE+" crédits).";
        if(listings.size()>=MAX_LISTINGS||ownCount(p.getUUID())>=PER_PLAYER)return "Limite d'annonces atteinte.";
        var held=p.getMainHandItem();
        if(!bounded(p,held)||!ItemStack.matches(held,quote))return "Objet modifié ou non vendable. Actualise la page.";
        // Keep all item components (TaCZ IDs, durability, origin) in escrow.
        var item=held.copy();
        UUID id=UUID.randomUUID();
        p.getInventory().setItem(p.getInventory().selected,ItemStack.EMPTY);
        listings.put(id,new Listing(id,p.getUUID(),p.getGameProfile().getName(),price,item));
        p.getInventory().setChanged();setDirty();
        return "Annonce publiée · "+price+" crédits le lot.";
    }
    private static boolean hasSpace(ServerPlayer p,ItemStack item){
        long capacity=p.getInventory().items.stream().mapToLong(s->s.isEmpty()?item.getMaxStackSize():
            ItemStack.isSameItemSameComponents(s,item)?Math.max(0,s.getMaxStackSize()-s.getCount()):0).sum();
        return capacity>=item.getCount();
    }
    String buyOrCancel(ServerPlayer p,Offer quote){
        if(!available(p))return "Marché disponible hors raid, en survie.";
        var l=listings.get(quote.id);
        if(l==null||l.price!=quote.price)return "Annonce indisponible. Catalogue actualisé.";
        boolean own=l.owner.equals(p.getUUID());
        var delivered=l.item.copy();
        // Cancelling preserves the original insurance; a change of owner does not.
        if(!own)RaidSecurity.removeInsuranceForTrade(delivered);
        if(!hasSpace(p,delivered))return "Inventaire Minecraft plein.";
        var account=SupplyContract.account(p);
        int balance=SupplyContract.balance(p);
        if(!own&&balance<l.price)return "Crédits insuffisants.";
        long net=l.price-l.price/20;
        long pending=proceeds.getOrDefault(l.owner,0L);
        if(!own&&pending>Long.MAX_VALUE-net)return "Le vendeur doit retirer ses crédits.";
        // No other request can interleave on the server thread.
        listings.remove(l.id);
        if(!own){
            account.putInt("credits",balance-l.price);
            proceeds.put(l.owner,pending+net);
        }
        p.getInventory().add(delivered);p.getInventory().setChanged();setDirty();
        if(own)return "Annonce annulée · objet récupéré.";
        TradeJournal.record(p,"Achat","Marché joueurs",l.item.getHoverName().getString(),l.item.getCount(),-l.price);
        var seller=p.server.getPlayerList().getPlayer(l.owner);
        if(seller!=null)seller.sendSystemMessage(Component.literal("Marché joueurs · vente réalisée. "+net+" crédits à retirer."));
        return "Achat effectué · -"+l.price+" crédits.";
    }
    String claim(ServerPlayer p){
        if(!available(p))return "Retrait disponible hors raid.";
        long pending=proceeds.getOrDefault(p.getUUID(),0L);
        int balance=SupplyContract.balance(p);
        int amount=(int)Math.min(pending,Math.max(0L,Integer.MAX_VALUE-(long)balance));
        if(amount<=0)return pending>0?"Solde maximal atteint.":"Aucun crédit à retirer.";
        if(pending==amount)proceeds.remove(p.getUUID());else proceeds.put(p.getUUID(),pending-amount);
        SupplyContract.account(p).putInt("credits",balance+amount);setDirty();
        TradeJournal.record(p,"Vente","Marché joueurs","Recettes de ventes",1,amount);
        return "Recettes récupérées · +"+amount+" crédits.";
    }
    private static PlayerMarket read(CompoundTag tag,HolderLookup.Provider registries){
        var market=new PlayerMarket();
        for(var raw:tag.getList("listings",10)){
            if(!(raw instanceof CompoundTag e)||!e.hasUUID("id")||!e.hasUUID("owner"))continue;
            var stack=ItemStack.parseOptional(registries,e.getCompound("item"));
            int price=e.getInt("price");
            if(stack.isEmpty()||price<1||price>MAX_PRICE)continue;
            var id=e.getUUID("id");
            market.listings.putIfAbsent(id,new Listing(id,e.getUUID("owner"),e.getString("seller"),price,stack));
        }
        for(var raw:tag.getList("proceeds",10)){
            if(raw instanceof CompoundTag e&&e.hasUUID("owner")&&e.getLong("amount")>0)
                market.proceeds.put(e.getUUID("owner"),e.getLong("amount"));
        }
        return market;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        var offers=new ListTag();
        for(var l:listings.values()){
            var e=new CompoundTag();e.putUUID("id",l.id);e.putUUID("owner",l.owner);e.putString("seller",l.seller);
            e.putInt("price",l.price);e.put("item",l.item.saveOptional(registries));offers.add(e);
        }
        var payments=new ListTag();
        proceeds.forEach((id,amount)->{var e=new CompoundTag();e.putUUID("owner",id);e.putLong("amount",amount);payments.add(e);});
        tag.put("listings",offers);tag.put("proceeds",payments);return tag;
    }
}
