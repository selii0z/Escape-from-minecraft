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
    public static final int DEPART_DUO = 25;
    public static final int DEPART_DUO_NIGHT = 26;
    private static final int PAGE_SIZE = 4;
    public static final int LIST_ROWS = 6;
    public static final int VENDOR_FIRST = 35;
    public static final int VENDOR_PAGES = 4;
    public static final int TRADE_FIRST = 45;
    public static final int CONTRACT_FIRST = 55;
    public static final int INDUSTRIAL_FIRST = 65;

    public record View(int tab, int page, int pages, int credits, int experience, boolean showExtraction, int unlocks, List<Integer> contracts, List<Integer> progress,
                       String partner, String inviter, List<String> candidates, String notice) {
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
        }

        private static View read(RegistryFriendlyByteBuf buffer) {
            return new View(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readList(buf -> buf.readVarInt()),
                buffer.readList(buf -> buf.readVarInt()), buffer.readUtf(),
                buffer.readUtf(), buffer.readList(buf -> buf.readUtf()), buffer.readUtf());
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
        int pages = switch (tab) {
            case CONTRACTS -> (SupplyContract.values().length + LIST_ROWS - 1) / LIST_ROWS;
            case SHOP -> OperationsShop.values().length * VENDOR_PAGES;
            default -> Math.max(1, (online.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        };
        int page = Math.clamp(requestedPage, 0, pages - 1);
        int start = tab == GROUP ? page * PAGE_SIZE : 0;
        List<ServerPlayer> shown = online.subList(start, Math.min(online.size(), start + PAGE_SIZE));
        List<UUID> candidateIds = shown.stream().map(ServerPlayer::getUUID).toList();
        View view = new View(tab, page, pages, SupplyContract.balance(player),
            SupplyContract.experienceEarned(player), SupplyContract.showExtraction(player), OperationsShop.unlocks(player),
            java.util.Arrays.stream(SupplyContract.values()).map(contract -> contract.status(player)).toList(),
            java.util.Arrays.stream(SupplyContract.values()).map(contract -> contract.progress(player)).toList(),
            name(player, partner), name(player, inviter),
            shown.stream().map(target -> target.getGameProfile().getName()).toList(), notice);
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
            || button == SHOP || button == PROGRESSION || button == FILES || button == SETTINGS) {
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
        } else if (tab == SETTINGS && button == TOGGLE_EXTRACTION) {
            SupplyContract.toggleExtraction(serverPlayer);
            notice = "Préférence enregistrée.";
        } else if (tab == SHOP && button >= VENDOR_FIRST && button < VENDOR_FIRST + OperationsShop.values().length) {
            page = (button - VENDOR_FIRST) * VENDOR_PAGES;
        } else if (tab == SHOP && button >= TRADE_FIRST && button < TRADE_FIRST + LIST_ROWS) {
            var vendor = OperationsShop.values()[page / VENDOR_PAGES];
            if (serverPlayer.level().dimension().equals(ForestExpedition.FOREST)) return false;
            notice = vendor.trade(serverPlayer, page % VENDOR_PAGES * LIST_ROWS + button - TRADE_FIRST);
        } else if (tab == SHOP && (button == PREVIOUS || button == NEXT)) {
            int vendor = page / VENDOR_PAGES;
            int offers = OperationsShop.values()[vendor].offers().size();
            int max = Math.max(0, (offers - 1) / LIST_ROWS);
            page = vendor * VENDOR_PAGES + Math.clamp(page % VENDOR_PAGES + (button == NEXT ? 1 : -1), 0, max);
        } else if (tab == CONTRACTS && (button == PREVIOUS || button == NEXT)) {
            page += button == NEXT ? 1 : -1;
        } else if (button != REFRESH) {
            int row = button - FIRST_ROW;
            OperationsGroups groups = OperationsGroups.of(serverPlayer);
            int contractIndex = page * LIST_ROWS + button - CONTRACT_FIRST;
            if (tab == CONTRACTS && button >= CONTRACT_FIRST && button < CONTRACT_FIRST + LIST_ROWS
                && contractIndex < view.contracts().size()) {
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
