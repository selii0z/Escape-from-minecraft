package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class OperationsMenu extends AbstractContainerMenu {
    public static final int CONTRACTS = 0;
    public static final int GROUP = 1;
    public static final int PREVIOUS = 2;
    public static final int NEXT = 3;
    public static final int ACCEPT = 4;
    public static final int DECLINE = 5;
    public static final int LEAVE = 6;
    public static final int REFRESH = 7;
    public static final int FOREST = 8;
    public static final int DEPART = 9;
    public static final int DEPART_NIGHT = 14;
    public static final int FIRST_ROW = 10;
    public static final int SHOP = 20;
    public static final int PROGRESSION = 21;
    public static final int FILES = 22;
    public static final int SETTINGS = 23;
    public static final int TOGGLE_EXTRACTION = 24;
    public static final int SECURE_POUCH = 140, INSURE_GEAR = 141, CLAIM_INSURANCE = 142, TIMED_CONTRACTS = 143, BLACK_MARKET = 144, MARKET_FIRST = 150;
    public static final int DEPART_DUO = 25;
    public static final int DEPART_DUO_NIGHT = 26;
    private static final int PAGE_SIZE = 4;
    public static final int LIST_ROWS = 6;
    public static final int CONTRACT_ROWS = 5;
    public static final int VENDOR_FIRST = 35;
    public static final int VENDOR_PAGES = 128;
    public static final int SHOP_ROWS = 24;
    public static final int TRADE_FIRST = 180, SHOP_CATEGORY_FIRST=228;
    public static final int SELL_ALL_FIRST=204, SHOP_OWNED_FILTER=166, TRADE_JOURNAL=167, MERCHANT_ORDERS=168, ORDER_FIRST=170, SHOP_ACCESSORIES=174;
    public static final int CONTRACT_FIRST = 55;
    public static final int INDUSTRIAL_FIRST = 65;

    public static final int FILTER_CATEGORY = 100, FILTER_DIFFICULTY = 110, FILTER_STATUS = 120, FILTER_MERCHANT = 130;

    public record View(int tab, int page, int pages, int credits, int experience, boolean showExtraction, int unlocks, List<Integer> contracts, List<Integer> progress,
                       String partner, String inviter, List<String> candidates, String notice, String raidReport,
                       List<String> raidHistory, int category, int difficulty, int statusFilter, int merchantFilter, List<Integer> filtered, int cycles, List<Integer> reputation, List<Long> raidTotals, List<Integer> timedContracts, List<String> market,List<Integer> saleCounts,List<Integer> shopRows,boolean shopOwned,int shopPages,long shopValue,List<String> journal,List<Long> journalTotals,List<String> orders,boolean shopAccessories) {
        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(tab);
            buffer.writeVarInt(page);
            buffer.writeVarInt(pages);
            buffer.writeVarInt(credits);
            buffer.writeVarInt(experience);
            buffer.writeBoolean(showExtraction);
            buffer.writeVarInt(unlocks);
            buffer.writeCollection(contracts, (buf, status) -> buf.writeVarInt(status));
            buffer.writeCollection(progress, (buf, amount) -> buf.writeVarInt(amount));
            buffer.writeUtf(partner);
            buffer.writeUtf(inviter);
            buffer.writeCollection(candidates, (buf, name) -> buf.writeUtf(name));
            buffer.writeUtf(notice);
            buffer.writeUtf(raidReport);
            buffer.writeCollection(raidHistory, (buf, line) -> buf.writeUtf(line));
            buffer.writeVarInt(category); buffer.writeVarInt(difficulty); buffer.writeVarInt(statusFilter); buffer.writeVarInt(merchantFilter);
            buffer.writeCollection(filtered, (buf, index) -> buf.writeVarInt(index));
            buffer.writeVarInt(cycles);
            buffer.writeCollection(reputation, (buf, points) -> buf.writeVarInt(points));
            buffer.writeCollection(raidTotals, (buf, amount) -> buf.writeVarLong(amount));
            buffer.writeCollection(timedContracts,(buf,amount)->buf.writeVarInt(amount));
            buffer.writeCollection(market,(buf,line)->buf.writeUtf(line));
            buffer.writeCollection(saleCounts,(buf,count)->buf.writeVarInt(count));
            buffer.writeCollection(shopRows,(buf,index)->buf.writeVarInt(index));
            buffer.writeBoolean(shopOwned);
            buffer.writeVarInt(shopPages);
            buffer.writeVarLong(shopValue);
            buffer.writeCollection(journal,(buf,line)->buf.writeUtf(line));
            buffer.writeCollection(journalTotals,(buf,value)->buf.writeVarLong(value));
            buffer.writeCollection(orders,(buf,line)->buf.writeUtf(line));
            buffer.writeBoolean(shopAccessories);
        }

        private static View read(RegistryFriendlyByteBuf buffer) {
            return new View(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readList(buf -> buf.readVarInt()),
                buffer.readList(buf -> buf.readVarInt()), buffer.readUtf(),
                buffer.readUtf(), buffer.readList(buf -> buf.readUtf()), buffer.readUtf(), buffer.readUtf(),
                buffer.readList(buf -> buf.readUtf()), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readList(buf -> buf.readVarInt()), buffer.readVarInt(), buffer.readList(buf -> buf.readVarInt()), buffer.readList(buf -> buf.readVarLong()),buffer.readList(buf -> buf.readVarInt()),buffer.readList(buf -> buf.readUtf()),buffer.readList(buf->buf.readVarInt()),buffer.readList(buf->buf.readVarInt()),buffer.readBoolean(),buffer.readVarInt(),buffer.readVarLong(),buffer.readList(buf->buf.readUtf()),buffer.readList(buf->buf.readVarLong()),buffer.readList(buf->buf.readUtf()),buffer.readBoolean());
        }
    }

    private final View view;
    private final BlockPos position;
    private final ServerLevel level;
    private final List<UUID> candidates;
    private final UUID inviter;

    public OperationsMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, View.read(buffer), null, null, List.of(), null);
    }

    private OperationsMenu(int id, View view, BlockPos position, ServerLevel level,
                           List<UUID> candidates, UUID inviter) {
        super(OperationsMod.MENU.get(), id);
        this.view = view;
        this.position = position;
        this.level = level;
        this.candidates = candidates;
        this.inviter = inviter;
    }

    private static String lastRaidSummary(ServerPlayer player) {
        var report = SupplyContract.account(player).getCompound("last_raid_report");
        if (!report.contains("final_xp")) return "";
        long seconds = report.getLong("duration_seconds");
        return (report.getBoolean("success") ? "EXTRACTION RÉUSSIE" : "RAID ÉCHOUÉ")
            + " | " + (seconds / 60) + "m " + (seconds % 60) + "s"
            + " | " + report.getInt("kills") + " kills"
            + " | " + report.getInt("crates") + " coffres"
            + " | " + report.getInt("final_xp") + " XP";
    }

    private static List<String> raidHistory(ServerPlayer player) {
        var reports = SupplyContract.account(player).getList("raid_history", 10);
        List<String> history = new java.util.ArrayList<>();
        for (int i = 0; i < reports.size() && i < 10; i++) {
            var report = reports.getCompound(i);
            long seconds = report.getLong("duration_seconds");
            history.add((report.getBoolean("success") ? "OK" : "KO")
                + " " + (seconds / 60) + ":" + String.format("%02d", seconds % 60)
                + " | " + report.getInt("kills") + "K"
                + " | " + report.getInt("crates") + "C"
                + " | " + report.getInt("final_xp") + "XP"
                + (report.getBoolean("success")&&report.contains("loot_value")&&report.getLong("loot_value")>=0
                    ?" | "+report.getLong("loot_value")+" cr.":""));
        }
        return history;
    }

    static void open(ServerPlayer player, BlockPos position, int tab, int requestedPage, String notice) {
        OperationsGroups groups = OperationsGroups.of(player);
        UUID partner = groups.partner(player);
        UUID inviter = groups.inviter(player);
        List<ServerPlayer> online = tab == GROUP && partner == null && inviter == null
            ? player.server.getPlayerList().getPlayers().stream()
                .filter(target -> target != player && groups.partner(target) == null)
                .sorted(Comparator.comparing(target -> target.getGameProfile().getName()))
                .toList()
            : List.of();
        SupplyContract.renewCycle(player);
        SupplyContract.rewardLevels(player);
        var account = SupplyContract.account(player);
        int category = account.getInt("filter_category_mask") & 31;
        int difficulty = account.getInt("filter_difficulty_mask") & 31;
        int statusFilter = account.getInt("filter_status_mask") & 7;
        int merchantFilter = account.getInt("filter_merchant_mask") & 15;
        var filtered = java.util.Arrays.stream(SupplyContract.values())
            .filter(c -> selected(category, c.category()))
            .filter(c -> selected(difficulty, c.difficulty()))
            .filter(c -> statusFilter == 0 ? c.status(player) != SupplyContract.COMPLETED : selected(statusFilter, c.status(player) + 1))
            .filter(c -> selected(merchantFilter, c.merchant().ordinal() + 1))
            .map(Enum::ordinal).toList();
        int pages = switch (tab) {
            case CONTRACTS -> Math.max(1, (filtered.size() + CONTRACT_ROWS - 1) / CONTRACT_ROWS);
            case SHOP -> OperationsShop.values().length * VENDOR_PAGES;
            case TRADE_JOURNAL -> Math.max(1,(TradeJournal.size(player)+LIST_ROWS-1)/LIST_ROWS);
            default -> Math.max(1, (online.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        };
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int start = tab == GROUP ? page * PAGE_SIZE : 0;
        List<ServerPlayer> shown = online.subList(start, Math.min(online.size(), start + PAGE_SIZE));
        List<UUID> candidateIds = shown.stream().map(ServerPlayer::getUUID).toList();
        List<Integer> saleCounts=new java.util.ArrayList<>();
        List<Integer> shopRows=new java.util.ArrayList<>();
        boolean shopOwned=account.getBoolean("shop_owned_only");
        int shopCategory=Math.clamp(account.getInt("shop_category"),0,6);
        boolean shopAccessories=tab==SHOP&&page/VENDOR_PAGES==OperationsShop.ARMORER.ordinal()&&!shopOwned&&account.getBoolean("shop_accessories_only");
        int shopPages=1;
        long shopValue=0;
        if(tab==SHOP){
            int vendorIndex=page/VENDOR_PAGES;
            var merchant=OperationsShop.values()[vendorIndex];
            var offers=merchant.offers();
            var indices=new java.util.ArrayList<Integer>();
            var counts=new java.util.ArrayList<Integer>();
            long day=ResaleMarket.day();
            var valued=new java.util.HashMap<String,Long>();
            for(int i=0;i<offers.size();i++){
                var offer=offers.get(i);
                int count=OperationsShop.saleCount(player,offer);
                if((shopOwned?offer.action()==OperationsShop.Action.SELL&&count>0:offer.action()!=OperationsShop.Action.SELL)&&(!shopAccessories||offer.accessory())&&(shopOwned||shopAccessories||shopCategory==0||offer.shopCategory()==shopCategory)){
                    indices.add(i);counts.add(count);
                }
                if(count>0){
                    var stack=offer.stack().get();
                    String key=stack.getItem() instanceof com.tacz.guns.api.item.IGun gun
                        ?"gun/"+gun.getGunId(stack):"item/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                    valued.merge(key,(long)count*ResaleMarket.price(offer,day),Math::max);
                }
            }
            for(long value:valued.values())shopValue+=value;
            shopPages=Math.max(1,Math.min(VENDOR_PAGES,(indices.size()+SHOP_ROWS-1)/SHOP_ROWS));
            int local=Math.clamp(page%VENDOR_PAGES,0,shopPages-1);
            page=vendorIndex*VENDOR_PAGES+local;
            int first=local*SHOP_ROWS;
            for(int i=first;i<Math.min(first+SHOP_ROWS,indices.size());i++){
                shopRows.add(indices.get(i));saleCounts.add(counts.get(i));
            }
        }
        View view = new View(tab, page, pages, SupplyContract.balance(player),
            SupplyContract.experienceEarned(player), SupplyContract.showExtraction(player), OperationsShop.unlocks(player),
            java.util.Arrays.stream(SupplyContract.values()).map(contract -> contract.status(player)).toList(),
            java.util.Arrays.stream(SupplyContract.values()).map(contract -> contract.progress(player)).toList(),
            name(player, partner), name(player, inviter),
            shown.stream().map(target -> target.getGameProfile().getName()).toList(), notice,
            lastRaidSummary(player), raidHistory(player), tab==SHOP?shopCategory:category, difficulty, statusFilter, merchantFilter, filtered, SupplyContract.cyclesCompleted(player),
            java.util.Arrays.stream(OperationsShop.values()).map(m -> m.reputation(player)).toList(),
            java.util.stream.Stream.of("raids","wins","losses","kills","crates","contracts","seconds","xp","best_kills",
                "best_loot","loot_total","current_streak","best_streak","best_xp","best_crates","valued_raids","loot_count_total")
                .map(key -> SupplyContract.raidTotals(player).getLong(key)).toList(),TimedContracts.view(player),BlackMarket.view(player),List.copyOf(saleCounts),List.copyOf(shopRows),shopOwned,shopPages,shopValue,tab==TRADE_JOURNAL?TradeJournal.page(player,page):List.of(),TradeJournal.totals(player),tab==MERCHANT_ORDERS?MerchantOrders.view(player):List.of(),shopAccessories);
        ServerLevel currentLevel = player.serverLevel();
        boolean refreshing = player.containerMenu instanceof OperationsMenu current
            && current.level == currentLevel && position.equals(current.position);
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.taczoperations.operations_board");
            }

            @Override
            public OperationsMenu createMenu(int id, Inventory inventory, Player viewer) {
                return new OperationsMenu(id, view, position.immutable(), currentLevel, candidateIds, inviter);
            }

            @Override
            public boolean shouldTriggerClientSideContainerClosingOnOpen() {
                // Keep the screen open during refresh so Minecraft does not grab the mouse again.
                return !refreshing;
            }
        }, view::write);
    }

    public static boolean selected(int mask, int option) {
        return option == 0 ? mask == 0 : mask == 0 || (mask & (1 << (option - 1))) != 0;
    }

    private static void toggleFilter(ServerPlayer player, String key, int option) {
        var account = SupplyContract.account(player);
        int mask = account.getInt(key);
        account.putInt(key, option == 0 ? 0 : mask ^ (1 << (option - 1)));
    }

    private static String name(ServerPlayer viewer, UUID id) {
        if (id == null) {
            return "";
        }
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(id);
        return online == null ? "Équipier hors ligne" : online.getGameProfile().getName();
    }

    public View view() {
        return view;
    }

    @Override
    public boolean stillValid(Player player) {
        return level == null || (player.level() == level && level.isLoaded(position)
            && level.getBlockState(position).is(OperationsMod.BOARD.get())
            && position.distToCenterSqr(player.position()) <= 64);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || !stillValid(player)) {
            return false;
        }
        int tab = view.tab();
        int page = view.page();
        String notice = "";
        if (button == CONTRACTS || button == GROUP || button == FOREST
            || button == SHOP || button == PROGRESSION || button == FILES || button == SETTINGS || button == TIMED_CONTRACTS || button == BLACK_MARKET || button==TRADE_JOURNAL || button==MERCHANT_ORDERS) {
            tab = button;
            page = 0;
        } else if (tab == FOREST && (button == DEPART || button == DEPART_NIGHT || button == DEPART_DUO
            || button == DEPART_DUO_NIGHT || (button >= INDUSTRIAL_FIRST && button < INDUSTRIAL_FIRST + 4))) {
            boolean industrial = button >= INDUSTRIAL_FIRST;
            int mode = industrial ? button - INDUSTRIAL_FIRST : -1;
            notice = ForestExpedition.depart(serverPlayer, position,
                industrial ? (mode & 1) != 0 : button == DEPART_NIGHT || button == DEPART_DUO_NIGHT,
                industrial ? (mode & 2) != 0 : button == DEPART_DUO || button == DEPART_DUO_NIGHT, industrial);
            if (notice.isEmpty()) {
                return true;
            }
        } else if(tab==MERCHANT_ORDERS&&button>=ORDER_FIRST&&button<ORDER_FIRST+MerchantOrders.SIZE){
            notice=MerchantOrders.deliver(serverPlayer,button-ORDER_FIRST,Long.parseLong(view.orders().getFirst()));
        } else if (tab==BLACK_MARKET && button>=MARKET_FIRST && button<MARKET_FIRST+BlackMarket.SIZE) {
            notice=BlackMarket.buy(serverPlayer,button-MARKET_FIRST,Long.parseLong(view.market().getFirst()));
        } else if (tab == SETTINGS && button == SECURE_POUCH) {
            notice = "Clic droit avec le coffre sécurisé en main pour ouvrir ses 5 places.";
        } else if (tab == SETTINGS && button == INSURE_GEAR) {
            notice = RaidSecurity.insure(serverPlayer);
        } else if (tab == SETTINGS && button == CLAIM_INSURANCE) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(RaidSecurity.claim(serverPlayer)));
            return true;
        } else if (tab == SETTINGS && button == TOGGLE_EXTRACTION) {
            SupplyContract.toggleExtraction(serverPlayer);
            notice = "Préférence enregistrée.";
        } else if (tab == SHOP && button >= VENDOR_FIRST && button < VENDOR_FIRST + OperationsShop.values().length) {
            page = (button - VENDOR_FIRST) * VENDOR_PAGES;
        } else if(tab==SHOP&&button>=SHOP_CATEGORY_FIRST&&button<SHOP_CATEGORY_FIRST+7){
            var account=SupplyContract.account(serverPlayer);
            account.putInt("shop_category",button-SHOP_CATEGORY_FIRST);
            account.putBoolean("shop_owned_only",false);
            account.putBoolean("shop_accessories_only",false);
            page=page/VENDOR_PAGES*VENDOR_PAGES;
        } else if(tab==SHOP&&button==SHOP_OWNED_FILTER){
            SupplyContract.account(serverPlayer).putBoolean("shop_owned_only",!view.shopOwned());
            SupplyContract.account(serverPlayer).putBoolean("shop_accessories_only",false);
            page=page/VENDOR_PAGES*VENDOR_PAGES;
        } else if(tab==SHOP&&button==SHOP_ACCESSORIES&&page/VENDOR_PAGES==OperationsShop.ARMORER.ordinal()){
            var account=SupplyContract.account(serverPlayer);
            account.putBoolean("shop_accessories_only",!view.shopAccessories());
            account.putBoolean("shop_owned_only",false);
            page=page/VENDOR_PAGES*VENDOR_PAGES;
        } else if(tab==SHOP&&button>=SELL_ALL_FIRST&&button<SELL_ALL_FIRST+SHOP_ROWS){
            if(serverPlayer.level().dimension().equals(ForestExpedition.FOREST))return false;
            int row=button-SELL_ALL_FIRST;
            if(row>=view.shopRows().size())return false;
            var vendor=OperationsShop.values()[page/VENDOR_PAGES];
            notice=vendor.trade(serverPlayer,view.shopRows().get(row),
                Long.parseLong(view.market().getFirst()),view.saleCounts().get(row),true);
        } else if (tab == SHOP && button >= TRADE_FIRST && button < TRADE_FIRST + SHOP_ROWS) {
            var vendor = OperationsShop.values()[page / VENDOR_PAGES];
            if (serverPlayer.level().dimension().equals(ForestExpedition.FOREST)) return false;
            int row=button-TRADE_FIRST;
            if(row>=view.shopRows().size())return false;
            notice = vendor.trade(serverPlayer,view.shopRows().get(row),Long.parseLong(view.market().getFirst()));
        } else if (tab == SHOP && (button == PREVIOUS || button == NEXT)) {
            int vendor = page / VENDOR_PAGES;
            int max = view.shopPages()-1;
            page = vendor * VENDOR_PAGES + Math.clamp(page % VENDOR_PAGES + (button == NEXT ? 1 : -1), 0, max);
        } else if (tab == CONTRACTS && button >= FILTER_CATEGORY && button <= FILTER_CATEGORY + 5) {
            toggleFilter(serverPlayer, "filter_category_mask", button - FILTER_CATEGORY); page = 0;
        } else if (tab == CONTRACTS && button >= FILTER_DIFFICULTY && button <= FILTER_DIFFICULTY + 5) {
            toggleFilter(serverPlayer, "filter_difficulty_mask", button - FILTER_DIFFICULTY); page = 0;
        } else if (tab == CONTRACTS && button >= FILTER_STATUS && button <= FILTER_STATUS + 3) {
            toggleFilter(serverPlayer, "filter_status_mask", button - FILTER_STATUS); page = 0;
        } else if (tab == CONTRACTS && button >= FILTER_MERCHANT && button <= FILTER_MERCHANT + 4) {
            toggleFilter(serverPlayer, "filter_merchant_mask", button - FILTER_MERCHANT); page = 0;
        } else if ((tab == CONTRACTS||tab==TRADE_JOURNAL) && (button == PREVIOUS || button == NEXT)) {
            page += button == NEXT ? 1 : -1;
        } else if (button != REFRESH) {
            int row = button - FIRST_ROW;
            OperationsGroups groups = OperationsGroups.of(serverPlayer);
            int contractIndex = page * CONTRACT_ROWS + button - CONTRACT_FIRST;
            if (tab == CONTRACTS && button >= CONTRACT_FIRST && button < CONTRACT_FIRST + CONTRACT_ROWS
                && contractIndex >= 0 && contractIndex < view.filtered().size()) {
                contractIndex = view.filtered().get(contractIndex);
                SupplyContract contract = SupplyContract.values()[contractIndex];
                if (contract.status(serverPlayer) != view.contracts().get(contractIndex)) {
                    notice = "Le contrat a été mis à jour.";
                } else {
                    notice = contract.act(serverPlayer);
                }
            } else if (tab == GROUP) {
                switch (button) {
                    case PREVIOUS -> page--;
                    case NEXT -> page++;
                    case ACCEPT, DECLINE -> notice = groups.answer(serverPlayer, inviter, button == ACCEPT);
                    case LEAVE -> {
                        if (!view.partner().isEmpty()) {
                            notice = groups.leave(serverPlayer);
                        }
                    }
                    default -> {
                        if (row < 0 || row >= candidates.size()) {
                            return false;
                        }
                        notice = groups.invite(serverPlayer, candidates.get(row));
                    }
                }
            } else {
                return false;
            }
        }
        open(serverPlayer, position, tab, page, notice);
        return true;
    }
}
