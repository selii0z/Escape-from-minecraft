package fr.taczoperations.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.OperationsMenu;
import fr.taczoperations.operations.OperationsShop;
import fr.taczoperations.operations.SupplyContract;
import fr.taczoperations.operations.MilitaryProgression;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.function.BooleanSupplier;

@EventBusSubscriber(modid = OperationsMod.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OperationsScreen extends AbstractContainerScreen<OperationsMenu> {
    private static final int WIDTH = 836;
    private static final int HEIGHT = 470;
    private static final int WHITE = 0xfff5f3e8;
    private static final int SOFT = 0xffcaccc0;
    private static final int GOLD = 0xffe5ce88;
    private static final int LIME = 0xffe9f9a1;
    private static final int OLIVE = 0xff70993d;
    private static final int FRAME = 0xff716751;
    private static final int DARK = 0xda111611;
    private static final ResourceLocation BODY = asset("interface");
    private static final ResourceLocation BOLD = asset("heading");
    private static final ResourceLocation DESK = texture("dashboard");
    private static final ResourceLocation ICONS = texture("dashboard_icons");
    private static final int FOREST = 0;
    private static final int MAX_CONTRACT_XP = java.util.Arrays.stream(SupplyContract.values())
        .mapToInt(contract -> contract.experience).sum();

    private record Area(String title, String image, int difficulty, String environment) {
        ResourceLocation texture() { return OperationsScreen.texture(image); }
    }

    private static final Area[] AREAS = {
        new Area("Forêt Noire", "forest", 2, "Forêt, collines et installations"),
        new Area("Zone industrielle", "industrial", 3, "Usines et entrepôts"),
        new Area("Village abandonné", "village", 3, "Maisons et ruelles"),
        new Area("Base militaire", "military", 4, "Caserne et postes de garde"),
        new Area("Ville en quarantaine", "quarantine", 4, "Quartiers urbains"),
        new Area("Complexe souterrain", "underground", 5, "Galeries et salles techniques")
    };

    private record Section(int tab, String title, String subtitle) {}

    private static final Section[] SECTIONS = {
        new Section(OperationsMenu.FOREST, "Opérations", "Lancer un raid"),
        new Section(OperationsMenu.CONTRACTS, "Contrats", "Objectifs et récompenses"),
        new Section(OperationsMenu.SHOP, "Marchands", "Matériel et ravitaillement"),
        new Section(OperationsMenu.PROGRESSION, "Progression", "Rang, XP et crédits"),
        new Section(OperationsMenu.FILES, "Dossiers", "Ennemis et renseignements"),
        new Section(OperationsMenu.SETTINGS, "Paramètres", "Options du mod")
    };

    private int offsetX;
    private int offsetY;
    private float scale;
    private int selectedArea;
    private boolean night;
    private boolean duo;
    private ItemStack[] rewards;
    private int historyScroll;
    private static final int HISTORY_ROWS = 4;

    public OperationsScreen(OperationsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        duo = !menu.view().partner().isEmpty();
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(OperationsMod.MENU.get(), OperationsScreen::new);
    }

    @Override
    protected void init() {
        scale = Math.min(width / (float) WIDTH, height / (float) HEIGHT);
        imageWidth = Math.round(WIDTH * scale);
        imageHeight = Math.round(HEIGHT * scale);
        offsetX = (width - imageWidth) / 2;
        offsetY = (height - imageHeight) / 2;
        super.init();
        var view = menu.view();
        for (int row = 0; row < SECTIONS.length; row++) {
            Section section = SECTIONS[row];
            int iconIndex = row;
            int y = 102 + row * 43;
            widget(8, y, 139, 36, section.title, () -> send(section.tab),
                () -> true, (g, hovered) -> {
                    key(g, 8, y, 139, 36, active(section.tab), hovered, true);
                    icon(g, iconIndex, 17, y + 5, 26);
                    text(g, upper(section.title), 52, y + 6, 1.15F, WHITE, true);
                    text(g, fit(section.subtitle, 105), 52, y + 21, .83F, SOFT, false);
                });
        }
        switch (view.tab()) {
            case OperationsMenu.FOREST -> operationWidgets();
            case OperationsMenu.CONTRACTS -> {
                int start = view.page() * OperationsMenu.CONTRACT_ROWS;
                for (int i = 0; i < OperationsMenu.CONTRACT_ROWS && start + i < view.filtered().size(); i++) {
                    int status = view.contracts().get(view.filtered().get(start + i));
                    String label = switch (status) {
                        case SupplyContract.ACTIVE -> "Abandonner";
                        case SupplyContract.COMPLETED -> "Terminé";
                        default -> "Accepter";
                    };
                    final int action = OperationsMenu.CONTRACT_FIRST + i;
                    simple(700, 215 + i * 31, 110, 24, label, () -> send(action),
                        () -> false, () -> status == SupplyContract.ACTIVE || (status != SupplyContract.COMPLETED && view.contracts().stream().filter(s -> s == SupplyContract.ACTIVE).count() < 3));
                }
                topFilters(OperationsMenu.FILTER_CATEGORY, view.category(), 119, new String[]{"Tous", "Combat", "Exploration", "Extraction", "Récupération", "Boss"});
                topFilters(OperationsMenu.FILTER_DIFFICULTY, view.difficulty(), 141, new String[]{"Toutes", "Facile", "Moyen", "Difficile", "Épique", "Mythique"});
                topFilters(OperationsMenu.FILTER_STATUS, view.statusFilter(), 163, new String[]{"À faire", "Disponibles", "Actifs", "Terminés"});
                topFilters(OperationsMenu.FILTER_MERCHANT, view.merchantFilter(), 185, new String[]{"Tous", "Armurier", "Médecin", "Récupérateur", "Courtier"});
                simple(173, 376, 65, 22, "‹", () -> send(OperationsMenu.PREVIOUS), () -> false, () -> view.page() > 0);
                simple(750, 376, 65, 22, "›", () -> send(OperationsMenu.NEXT), () -> false, () -> view.page() + 1 < view.pages());
                widget(430, 376, 110, 22, "", () -> {}, () -> false,
                    (g, hover) -> centered(g, (view.page() + 1) + " / " + view.pages(), 485, 382, 1, SOFT, false));
            }
            case OperationsMenu.SHOP -> {
                var vendor = vendor();
                var offers = vendor.offers();
                int page = view.page() % OperationsMenu.VENDOR_PAGES;
                int start = page * OperationsMenu.LIST_ROWS;
                for (int i = 0; i < OperationsMenu.LIST_ROWS && start + i < offers.size(); i++) {
                    var offer = offers.get(start + i);
                    final int action = OperationsMenu.TRADE_FIRST + i;
                    simple(446, 136 + i * 41, 94, 24, offer.action().label, () -> send(action), () -> false,
                        () -> offer.action() == OperationsShop.Action.UNLOCK ? !unlocked(offer.key())
                            : offer.action() != OperationsShop.Action.BUY || (OperationsShop.reputationLevel(view.reputation().get(vendor.ordinal()), view.experience()) >= offer.loyaltyLevel()
                            && (offer.key() == null || unlocked(offer.key()))));
                }
                for (var merchant : OperationsShop.values()) {
                    simple(584, 218 + merchant.ordinal() * 35, 225, 28, merchant.title,
                        () -> send(OperationsMenu.VENDOR_FIRST + merchant.ordinal()), () -> merchant == vendor, () -> true);
                }
                pager(page, (offers.size() + OperationsMenu.LIST_ROWS - 1) / OperationsMenu.LIST_ROWS);
            }
            case OperationsMenu.GROUP -> groupWidgets();
            case OperationsMenu.SETTINGS -> simple(420, 145, 120, 29,
                view.showExtraction() ? "Masquer" : "Afficher", () -> send(OperationsMenu.TOGGLE_EXTRACTION));
            default -> { }
        }
        if (view.tab() != OperationsMenu.FOREST && view.tab() != OperationsMenu.GROUP && view.tab() != OperationsMenu.SHOP && view.tab() != OperationsMenu.CONTRACTS) {
            simple(581, 243, 232, 28, "Ouvrir le groupe", () -> send(OperationsMenu.GROUP));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.view().tab() == OperationsMenu.PROGRESSION && scale > 0) {
            double x = (mouseX - offsetX) / scale;
            double y = (mouseY - offsetY) / scale;
            if (x >= 169 && x <= 548 && y >= 301 && y <= 404 && scrollY != 0) {
                int max = Math.max(0, menu.view().raidHistory().size() - HISTORY_ROWS);
                historyScroll = Math.clamp(historyScroll - (scrollY > 0 ? 1 : -1), 0, max);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static final int[] DIFFICULTY_COLORS = {WHITE, 0xff9bce88, 0xff89bce5, GOLD, 0xffc39ae8, 0xffee7777};

    private void topFilters(int base, int mask, int y, String[] labels) {
        int w = 93;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            int x = 253 + i * (w + 2);
            BooleanSupplier selected = () -> index == 0 ? mask == 0 : (mask & (1 << (index - 1))) != 0;
            if (base == OperationsMenu.FILTER_DIFFICULTY && index > 0) {
                int accent = DIFFICULTY_COLORS[index];
                widget(x, y, w, 19, labels[i], () -> send(base + index), () -> true, (g, hover) -> {
                    boolean active = selected.getAsBoolean();
                    int rgb = accent & 0xffffff;
                    g.fillGradient(x, y, x+w, y+19, 0xff30342d, 0xff181c17);
                    g.fill(x+1,y+1,x+w-1,y+18,(active ? 0x60300000 : hover ? 0x30300000 : 0x10300000) & 0xff000000 | rgb);
                    g.renderOutline(x,y,w,19,active || hover ? accent : FRAME);
                    g.fill(x+2,y+16,x+w-2,y+18,active ? accent : (0x90000000 | rgb));
                    centered(g,labels[index],x+w/2,y+4,1,active ? WHITE : accent,true);
                });
            } else {
                simple(x, y, w, 19, labels[i], () -> send(base + index), selected, () -> true);
            }
        }
    }

    private void filterButtons(int base, int selected, int y, String[] labels) {
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            simple(581 + i % 3 * 78, y + i / 3 * 23, 75, 21, labels[i],
                () -> send(base + index), () -> selected == index, () -> true);
        }
    }

    private void operationWidgets() {
        if (rewards == null) {
            ItemStack ammunition = AmmoItemBuilder.create()
                .setId(ResourceLocation.fromNamespaceAndPath("tacz", "9mm")).build();
            rewards = new ItemStack[]{new ItemStack(Items.IRON_INGOT), ammunition,
                new ItemStack(Items.BREAD), new ItemStack(Items.COOKED_BEEF), new ItemStack(Items.GOLDEN_CARROT)};
        }
        for (int i = 0; i < AREAS.length; i++) {
            final int index = i;
            int x = 166 + i % 2 * 196;
            int y = 133 + i / 2 * 92;
            widget(x, y, 190, 87, AREAS[i].title, () -> selectedArea = index,
                () -> true, (g, hovered) -> mapCard(g, index, x, y, hovered));
        }
        simple(582, 321, 54, 27, "☀ Jour", () -> night = false, () -> !night, () -> selectedArea <= 1);
        simple(643, 321, 53, 27, "☾ Nuit", () -> night = true, () -> night, () -> selectedArea <= 1);
        simple(711, 321, 40, 27, "Solo", () -> duo = false, () -> !duo, () -> selectedArea <= 1);
        simple(756, 321, 63, 27, "Duo", () -> {
            if (menu.view().partner().isEmpty()) {
                send(OperationsMenu.GROUP);
            } else {
                duo = true;
            }
        }, () -> duo, () -> selectedArea <= 1);
        widget(580, 369, 240, 32, "Lancer l’opération", () -> {
            if (duo && menu.view().partner().isEmpty()) {
                send(OperationsMenu.GROUP);
                return;
            }
            if (selectedArea == 1) {
                send(OperationsMenu.INDUSTRIAL_FIRST + (night ? 1 : 0) + (duo ? 2 : 0));
            } else {
                send(duo ? (night ? OperationsMenu.DEPART_DUO_NIGHT : OperationsMenu.DEPART_DUO)
                    : (night ? OperationsMenu.DEPART_NIGHT : OperationsMenu.DEPART));
            }
        }, () -> selectedArea <= 1, (g, hovered) -> {
            boolean enabled = selectedArea <= 1;
            key(g, 580, 369, 240, 32, enabled, hovered, enabled);
            centered(g, enabled ? "⊕   LANCER L’OPÉRATION" : "ZONE INDISPONIBLE",
                700, 378, 1.43F, enabled ? WHITE : SOFT, true);
        });
    }

    private void groupWidgets() {
        var view = menu.view();
        if (!view.partner().isEmpty()) {
            simple(429, 145, 111, 28, "Quitter le duo", () -> send(OperationsMenu.LEAVE));
        } else if (!view.inviter().isEmpty()) {
            simple(350, 154, 92, 27, "Accepter", () -> send(OperationsMenu.ACCEPT));
            simple(448, 154, 92, 27, "Refuser", () -> send(OperationsMenu.DECLINE));
        } else {
            for (int i = 0; i < view.candidates().size(); i++) {
                final int action = OperationsMenu.FIRST_ROW + i;
                simple(455, 139 + i * 41, 85, 25, "Inviter", () -> send(action));
            }
            if (view.pages() > 1) {
                simple(385, 318, 42, 24, "‹", () -> send(OperationsMenu.PREVIOUS), () -> false, () -> view.page() > 0);
                simple(498, 318, 42, 24, "›", () -> send(OperationsMenu.NEXT), () -> false,
                    () -> view.page() + 1 < view.pages());
            }
        }
        simple(581, 243, 232, 28, "Actualiser les invitations", () -> send(OperationsMenu.REFRESH));
        simple(581, 282, 232, 28, "Revenir aux opérations", () -> send(OperationsMenu.FOREST));
    }

    private void send(int action) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private boolean active(int tab) {
        return tab == menu.view().tab() || (tab == OperationsMenu.FOREST && menu.view().tab() == OperationsMenu.GROUP);
    }

    @Override
    protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        canvas(g);
        image(g, DESK, 0, 0, WIDTH, HEIGHT);
        panel(g, 3, 74, 149, 337, 0xc0111713);
        if (menu.view().tab() == OperationsMenu.CONTRACTS) {
            panel(g, 157, 74, 674, 339, 0xf0111612);
        } else {
            panel(g, 157, 74, 401, 339, 0xd8111612);
            panel(g, 562, 74, 269, 339, 0xdc171e13);
        }
        header(g);
        if (menu.view().tab() == OperationsMenu.FOREST) {
            pageTitle(g, "⊕", "OPÉRATIONS", "Sélectionnez une zone de déploiement");
            operationDetails(g);
        } else {
            secondaryPage(g);
        }
        if (!menu.view().notice().isEmpty()) {
            panel(g, 157, 417, 674, 20, 0xf31d2417);
            text(g, fit(menu.view().notice(), 645), 170, 422, 1, GOLD, true);
        }
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The dashboard draws its labels on the same scaled canvas as the artwork.
    }

    private void header(GuiGraphics g) {
        g.fill(0, 0, 249, 66, 0x65050707);
        text(g, "ESCAPE FROM", 50, 4, 1.76F, WHITE, true);
        text(g, "MINECRAFT", 54, 23, 2.40F, WHITE, true);
        g.fillGradient(50, 48, 220, 64, 0xffecc33c, 0xffb18321);
        text(g, "▰  TaCZ OPERATIONS", 58, 50, 1.14F, 0xff12130e, true);

        panel(g, 528, 7, 195, 49, 0xec0f1311);
        if (minecraft.player != null) {
            PlayerFaceRenderer.draw(g, minecraft.player.getSkin(), 539, 16, 30);
            text(g, fit(minecraft.player.getGameProfile().getName(), 124), 580, 12, 1.25F, WHITE, true);
        }
        int xp = menu.view().experience();
        text(g, "Rang · " + rank(xp), 601, 27, .91F, WHITE, false);
        icon(g, 3, 575, 29, 20);
        progress(g, 601, 39, 108, 8, xp);
        text(g, "XP · " + xp + " / " + MilitaryProgression.threshold(MilitaryProgression.level(xp)+1), 605, 48, .74F, SOFT, false);

        panel(g, 730, 7, 98, 49, 0xec0f1311);
        item(g, new ItemStack(Items.GOLD_INGOT), 740, 19, 1.25F);
        text(g, "Crédits", 766, 16, 1.04F, SOFT, false);
        text(g, Integer.toString(menu.view().credits()), 766, 29, 1.40F, WHITE, true);
        g.fillGradient(3, 66, 833, 73, 0xff6e6454, 0xff1c201a);
    }

    private void operationDetails(GuiGraphics g) {
        Area area = AREAS[selectedArea];
        boolean open = selectedArea <= 1;
        panel(g, 569, 85, 258, 83, 0xc5272e20);
        g.blit(area.texture(), 574, 91, 87, 69, 83, 0, 214, 170, 380, 170);
        g.renderOutline(572, 89, 91, 73, 0xffc4b68c);
        icon(g, selectedArea + 6, 668, 100, 16);
        text(g, upper(area.title), 685, 102, area.title.length() > 19 ? 1.00F : 1.25F, WHITE, true);
        String[] lines = selectedArea == FOREST
            ? new String[]{"Zone forestière dense, scierie", "abandonnée et camp militaire.", "Deux voies d’extraction."}
            : selectedArea == 1 ? new String[]{"Complexe de fret et ateliers", "Première version jouable.", "Une sortie sur le bloc d’or."}
            : new String[]{area.environment, "Zone de déploiement à venir."};
        for (int i = 0; i < lines.length; i++) text(g, lines[i], 673, 124 + i * 11, .94F, SOFT, false);

        panel(g, 575, 173, 249, 63, DARK);
        detail(g, 179, "✦", "Difficulté", stars(area.difficulty));
        detail(g, 193, "◷", "Durée", open ? "Exploration libre" : "À venir");
        detail(g, 207, "♠", "Environnement", area.environment);
        detail(g, 221, "⊕", "Menace", open ? "Gardes armés" : "À venir");

        panel(g, 575, 241, 249, 54, DARK);
        text(g, "Récompenses possibles", 585, 246, 1, WHITE, true);
        if (open) {
            for (int i = 0; i < rewards.length; i++) {
                int x = 586 + i * 39;
                panel(g, x, 258, 34, 31, 0xff1b2118);
                item(g, rewards[i], x + 7, 263, 1.24F);
            }
        }
        panel(g, 575, 300, 127, 62, DARK);
        panel(g, 705, 300, 119, 62, DARK);
        text(g, "Moment de l’opération", 586, 305, .91F, WHITE, true);
        text(g, "Déploiement", 712, 305, .91F, WHITE, true);
        if (open) {
            text(g, duo ? fit(menu.view().partner(), 104) : "Groupe sur invitation", 711, 351, .75F, SOFT, false);
        }
    }

    private void mapCard(GuiGraphics g, int index, int x, int y, boolean hovered) {
        Area area = AREAS[index];
        boolean selected = index == selectedArea;
        image(g, area.texture(), x + 1, y + 1, 188, 85);
        g.fillGradient(x + 1, y + 42, x + 189, y + 86, 0x1010140d, 0xff111511);
        g.fill(x + 1, y + 68, x + 189, y + 86, 0xb30b0f0a);
        panel(g, x + 6, y + 57, 23, 24, index == FOREST ? 0xdd424c1e : 0xe3292b24);
        icon(g, index + 6, x + 8, y + 59, 19);
        text(g, upper(area.title), x + 35, y + 58, 1.16F, WHITE, true);
        text(g, "Difficulté", x + 35, y + 73, .84F, SOFT, false);
        text(g, stars(area.difficulty), x + 76, y + 72, .82F, GOLD, false);
        text(g, index <= 1 ? "◷ Libre" : "À venir", x + 145, y + 73, .78F, WHITE, true);
        frame(g, x, y, 190, 87, selected, hovered);
    }

    private void secondaryPage(GuiGraphics g) {
        var view = menu.view();
        switch (view.tab()) {
            case OperationsMenu.CONTRACTS -> {
                long active = view.contracts().stream().filter(status -> status == SupplyContract.ACTIVE).count();
                text(g, "CONTRATS", 173, 82, 1.55F, WHITE, true);
                text(g, "Cycle " + ((long)view.cycles()+1) + " · " + active + "/3 actifs · "
                    + view.filtered().size() + " résultats", 430, 87, .94F, GOLD, false);
                text(g, "CATÉGORIE", 174, 124, .88F, GOLD, true);
                text(g, "DIFFICULTÉ", 174, 146, .88F, GOLD, true);
                text(g, "STATUT", 174, 168, .88F, GOLD, true);
                text(g, "MARCHAND", 174, 190, .88F, GOLD, true);
                if (view.filtered().isEmpty()) text(g, "Aucun contrat correspondant", 180, 225, 1, SOFT, false);
                int start = view.page() * OperationsMenu.CONTRACT_ROWS;
                for (int i = 0; i < OperationsMenu.CONTRACT_ROWS && start+i < view.filtered().size(); i++) {
                    int index = view.filtered().get(start+i);
                    var contract = SupplyContract.values()[index];
                    int y = 212 + 31*i;
                    panel(g, 169, y, 650, 29, DARK);
                    g.fill(170, y+1, 174, y+28, DIFFICULTY_COLORS[contract.difficulty()]);
                    item(g, new ItemStack(contract.icon()), 178, y+5, 1.1F);
                    text(g, fit(contract.title + " · " + new String[]{"","Facile","Moyen","Difficile","Épique","Mythique"}[contract.difficulty()], 280),
                        205,y+3,1.0F,DIFFICULTY_COLORS[contract.difficulty()],true);
                    text(g, fit(contract.description(), 305),205,y+16,.85F,SOFT,false);
                    text(g, contract.merchant().title + " · +" + contract.reputationReward() + " rép.", 505,y+3,.85F,GOLD,true);
                    text(g, "+"+contract.credits+" cr. · "+contract.experience+" XP · "+view.progress().get(index)+"/"+contract.amount,
                        505,y+16,.80F,SOFT,false);
                }
            }
            case OperationsMenu.SHOP -> {
                var vendor = vendor();
                pageTitle(g, "▱", upper(vendor.title), vendor.specialty);
                int start = view.page() % OperationsMenu.VENDOR_PAGES * OperationsMenu.LIST_ROWS;
                var offers = vendor.offers();
                for (int i = 0; i < OperationsMenu.LIST_ROWS && start + i < offers.size(); i++) {
                    var offer = offers.get(start + i);
                    int y = 128 + i * 41;
                    panel(g, 169, y, 379, 37, DARK);
                    boolean locked = offer.action() == OperationsShop.Action.BUY &&
                        (OperationsShop.reputationLevel(view.reputation().get(vendor.ordinal()), view.experience()) < offer.loyaltyLevel()
                         || offer.key() != null && !unlocked(offer.key()));
                    text(g, fit((locked ? "[Verrouillé] " : "") + offer.title(), 253), 181, y + 6, 1.04F, locked ? SOFT : WHITE, true);
                    String cost = switch (offer.action()) {
                        case BUY, REPAIR -> offer.price() + " crédits";
                        case SELL -> "+" + offer.price() + " crédits";
                        case UNLOCK -> unlocked(offer.key()) ? "Débloqué" : "Remettre 1 objet";
                    };
                    String requirement = offer.requirement();
                    if (offer.action() == OperationsShop.Action.BUY && offer.loyaltyLevel() > 1)
                        requirement += " · Niv. " + OperationsShop.requiredPlayerLevel(offer.loyaltyLevel());
                    text(g, fit(cost + (requirement.isEmpty() ? "" : " · " + requirement), 250),
                        181, y + 23, .95F, GOLD, false);
                }
            }
            case OperationsMenu.PROGRESSION -> {
                pageTitle(g, "»", "PROGRESSION", "Expérience des raids et des contrats");
                panel(g, 169, 130, 379, 91, DARK);
                text(g, rank(view.experience()), 188, 139, 1.30F, WHITE, true);
                progress(g, 188, 160, 310, 10, view.experience());
                text(g, view.experience() + " XP · Prochain niveau : " + MilitaryProgression.threshold(MilitaryProgression.level(view.experience())+1), 188, 175, .95F, SOFT, false);
                long completed = view.contracts().stream().filter(status -> status == SupplyContract.COMPLETED).count();
                text(g, completed + "/" + view.contracts().size() + " contrats · " + view.cycles() + " cycles accomplis", 188, 190, .9F, GOLD, false);
                text(g, "Prochain palier : +" + MilitaryProgression.reward(MilitaryProgression.level(view.experience())+1) + " crédits", 188, 204, .82F, GOLD, false);
                panel(g, 169, 228, 379, 66, DARK);
                text(g, "DERNIER RAID", 187, 236, 1.10F, GOLD, true);
                if (view.raidReport().isEmpty()) {
                    text(g, "Aucun raid terminé pour le moment", 187, 257, .94F, SOFT, false);
                } else {
                    String[] fields = view.raidReport().split(" \\| ");
                    text(g, fields[0], 187, 254, 1.02F, WHITE, true);
                    String details = fields.length > 1
                        ? String.join(" · ", java.util.Arrays.copyOfRange(fields, 1, fields.length))
                        : "";
                    text(g, fit(details, 345), 187, 274, .93F, SOFT, false);
                }
                panel(g, 169, 301, 379, 103, DARK);
                text(g, "HISTORIQUE DES RAIDS", 181, 308, 1.0F, GOLD, true);
                text(g, "K = éliminations · C = coffres", 181, 321, .76F, SOFT, false);
                int count = view.raidHistory().size();
                historyScroll = Math.clamp(historyScroll, 0, Math.max(0, count - HISTORY_ROWS));
                if (count == 0) {
                    text(g, "Les dix derniers raids apparaîtront ici.", 181, 346, .85F, SOFT, false);
                } else {
                    for (int row = 0; row < HISTORY_ROWS && historyScroll + row < count; row++) {
                        int index = historyScroll + row;
                        String line = (index + 1) + ". " + view.raidHistory().get(index);
                        int y = 335 + row * 14;
                        if (row % 2 == 0) g.fill(179, y - 1, 528, y + 12, 0x30373e31);
                        text(g, fit(line, 335), 183, y, .85F,
                            view.raidHistory().get(index).startsWith("KO") ? 0xffdf9c94 : 0xffb9dcb6, false);
                    }
                    if (count > HISTORY_ROWS) {
                        int track = 56;
                        int thumb = Math.max(10, track * HISTORY_ROWS / count);
                        int top = 334 + (track - thumb) * historyScroll / (count - HISTORY_ROWS);
                        g.fill(534, 334, 540, 390, 0xff262c23);
                        g.fill(534, top, 540, top + thumb, GOLD);
                    }
                    text(g, (historyScroll + 1) + "–" + Math.min(count, historyScroll + HISTORY_ROWS)
                        + " / " + count + (count > HISTORY_ROWS ? " · Molette pour défiler" : ""),
                        181, 393, .70F, SOFT, false);
                }
            }
            case OperationsMenu.FILES -> {
                pageTitle(g, "▰", "DOSSIERS", "Renseignements sur la Forêt Noire");
                notes(g, 128, "Gardes armés", "Les gardes défendent les secteurs de ravitaillement.", "Les effets laissés au sol peuvent être fouillés.");
                notes(g, 207, "Voies de sortie", "Bloc d’or à la sortie principale.", "Activez le relais en cuivre pour ouvrir la seconde sortie.");
            }
            case OperationsMenu.SETTINGS -> {
                pageTitle(g, "⚙", "PARAMÈTRES", "Options du Tableau des opérations");
                panel(g, 169, 132, 379, 54, DARK);
                text(g, "Indication d’extraction", 185, 144, 1.13F, WHITE, true);
                text(g, view.showExtraction() ? "Coordonnées affichées en raid" : "Coordonnées masquées en raid",
                    185, 163, .95F, SOFT, false);
            }
            case OperationsMenu.GROUP -> {
                pageTitle(g, "♟", "GROUPE", "Préparez un déploiement à deux");
                if (!view.partner().isEmpty()) {
                    notes(g, 129, "Duo formé", view.partner(), "Restez ensemble près du tableau pour partir.");
                } else if (!view.inviter().isEmpty()) {
                    notes(g, 129, "Invitation reçue", view.inviter(), "");
                } else if (view.candidates().isEmpty()) {
                    notes(g, 129, "Aucun joueur disponible", "Les joueurs libres apparaissent ici.", "");
                } else {
                    for (int i = 0; i < view.candidates().size(); i++) {
                        int y = 132 + i * 41;
                        panel(g, 169, y, 379, 37, DARK);
                        text(g, view.candidates().get(i), 185, y + 12, 1.12F, WHITE, true);
                    }
                    if (view.pages() > 1) centered(g, (view.page() + 1) + " / " + view.pages(), 463, 325, 1, SOFT, false);
                }
            }
            default -> { }
        }
        if (view.tab() == OperationsMenu.CONTRACTS) return;
        if (view.tab() == OperationsMenu.SHOP) {
            var vendor = vendor();
            panel(g, 578, 88, 239, 115, DARK);
            image(g, texture("merchant_" + vendor.name().toLowerCase(java.util.Locale.ROOT)), 593, 102, 62, 82);
            text(g, vendor.title, 669, 109, 1.2F, GOLD, true);
            int reputation = view.reputation().get(vendor.ordinal());
            int loyalty = OperationsShop.reputationLevel(reputation,view.experience());
            text(g, "Fidélité " + loyalty + " / 4", 669, 130, 1.0F, GOLD, true);
            text(g, loyalty == 4 ? "Fidélité maximale" : reputation + " / " + OperationsShop.nextReputation(reputation,view.experience()) + " rép.", 669, 148, .85F, SOFT, false);
            if (loyalty < 4) {
                int required = OperationsShop.requiredPlayerLevel(loyalty+1);
                boolean reached = MilitaryProgression.level(view.experience()) >= required;
                text(g, "Niv. " + required + " requis", 669, 164, .85F, reached ? LIME : 0xffee9990, true);
            }
            return;
        }
        image(g, AREAS[FOREST].texture(), 578, 88, 239, 108);
        g.renderOutline(578, 88, 239, 108, FRAME);
        text(g, "FORÊT NOIRE", 585, 205, 1.24F, WHITE, true);
        text(g, view.partner().isEmpty() ? "Solo · invitations disponibles" : "Équipier : " + view.partner(),
            585, 224, .92F, SOFT, false);
    }

    private OperationsShop vendor() {
        return OperationsShop.values()[menu.view().page() / OperationsMenu.VENDOR_PAGES];
    }

    private boolean unlocked(fr.taczoperations.operations.RaidRelic relic) {
        return (menu.view().unlocks() & (1 << relic.ordinal())) != 0;
    }

    private void pager(int page, int total) {
        simple(173, 376, 65, 22, "‹", () -> send(OperationsMenu.PREVIOUS), () -> false, () -> page > 0);
        simple(478, 376, 65, 22, "›", () -> send(OperationsMenu.NEXT), () -> false, () -> page + 1 < total);
        widget(304, 376, 110, 22, "", () -> {}, () -> false,
            (g, hover) -> centered(g, (page + 1) + " / " + total, 359, 382, 1, SOFT, false));
    }

    private void notes(GuiGraphics g, int y, String title, String a, String b) {
        panel(g, 169, y, 379, 62, DARK);
        text(g, title, 185, y + 9, 1.17F, GOLD, true);
        text(g, a, 185, y + 25, 1.06F, WHITE, false);
        if (!b.isEmpty()) text(g, b, 185, y + 42, .93F, SOFT, false);
    }

    private void detail(GuiGraphics g, int y, String symbol, String label, String value) {
        text(g, symbol, 586, y, .92F, GOLD, false);
        text(g, label, 607, y, .91F, SOFT, false);
        text(g, value, 692, y, .87F, WHITE, true);
        g.fill(585, y + 11, 815, y + 12, 0x385b624a);
    }

    private void pageTitle(GuiGraphics g, String icon, String title, String subtitle) {
        text(g, icon, 172, 86, 2.25F, GOLD, false);
        text(g, title, 207, 86, 1.81F, WHITE, true);
        text(g, subtitle, 208, 108, .96F, SOFT, false);
        g.fill(167, 125, 549, 126, 0x705d5b3e);
    }

    private void simple(int x, int y, int w, int h, String label, Runnable action) {
        simple(x, y, w, h, label, action, () -> false, () -> true);
    }

    private void simple(int x, int y, int w, int h, String label, Runnable action,
                        BooleanSupplier selected, BooleanSupplier enabled) {
        widget(x, y, w, h, label, action, enabled, (g, hovered) -> {
            key(g, x, y, w, h, selected.getAsBoolean(), hovered, enabled.getAsBoolean());
            centered(g, label, x + w / 2, y + h / 2 - 5, 1,
                enabled.getAsBoolean() ? WHITE : 0xffa2a397, true);
        });
    }

    @FunctionalInterface
    private interface Paint { void draw(GuiGraphics graphics, boolean hover); }

    private void widget(int x, int y, int w, int h, String title, Runnable action,
                        BooleanSupplier enabled, Paint paint) {
        addRenderableWidget(new Button(
            offsetX + Math.round(x * scale), offsetY + Math.round(y * scale),
            Math.round((x + w) * scale) - Math.round(x * scale),
            Math.round((y + h) * scale) - Math.round(y * scale),
            Component.literal(title), ignored -> {
                if (enabled.getAsBoolean()) action.run();
            }, java.util.function.Supplier::get) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mx, int my, float tick) {
                active = enabled.getAsBoolean();
                canvas(graphics);
                paint.draw(graphics, active && isHoveredOrFocused());
                graphics.pose().popPose();
            }

            @Override
            public boolean mouseClicked(double mx, double my, int mouseButton) {
                active = enabled.getAsBoolean();
                return super.mouseClicked(mx, my, mouseButton);
            }
        });
    }

    private void canvas(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(offsetX, offsetY, 0);
        g.pose().scale(scale, scale, 1);
    }

    private void text(GuiGraphics g, String words, int x, int y, float size, int color, boolean bold) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size, size, 1);
        g.drawString(font, styled(words, bold), 0, 0, color, true);
        g.pose().popPose();
    }

    private void centered(GuiGraphics g, String words, int x, int y, float size, int color, boolean bold) {
        int w = font.width(styled(words, bold));
        text(g, words, Math.round(x - w * size / 2), y, size, color, bold);
    }

    private static Component styled(String words, boolean bold) {
        return Component.literal(words).withStyle(Style.EMPTY.withFont(bold ? BOLD : BODY));
    }

    private String fit(String text, int width) {
        if (font.width(styled(text, false)) <= width) return text;
        int end = text.length();
        while (end > 0 && font.width(styled(text.substring(0, end) + "…", false)) > width) {
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end) + "…";
    }

    private void image(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h) {
        var textures = minecraft.getTextureManager();
        textures.bindForSetup(id);
        textures.getTexture(id).setFilter(true, false);
        // Use normalized UVs by giving the UV region and texture the same dimensions.
        g.blit(id, x, y, 0, 0, w, h, w, h);
    }

    private void icon(GuiGraphics g, int index, int x, int y, int size) {
        var textures = minecraft.getTextureManager();
        textures.bindForSetup(ICONS);
        textures.getTexture(ICONS).setFilter(true, false);
        RenderSystem.enableBlend();
        g.blit(ICONS, x, y, size, size, index * 48, 0, 48, 48, 576, 48);
        RenderSystem.disableBlend();
    }

    private static void item(GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        g.renderOutline(x, y, w, h, FRAME);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, 0x6469634b);
    }

    private static void key(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover, boolean enabled) {
        g.fillGradient(x, y, x + w, y + h, !enabled ? 0xff292d27 : selected ? 0xff515d28 : hover ? 0xff404a30 : 0xff30342d,
            !enabled ? 0xff191c17 : selected ? 0xff242d14 : hover ? 0xff1f2a18 : 0xff181c17);
        frame(g, x, y, w, h, selected, hover);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover) {
        int border = selected ? LIME : hover ? GOLD : FRAME;
        if (selected || hover) {
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, selected ? 0x9fb3ca5f : 0x707e6b3a);
        }
        g.renderOutline(x, y, w, h, border);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, selected ? 0x8ae4f6a0 : 0x527c7257);
    }

    private static void progress(GuiGraphics g, int x, int y, int width, int height, int xp) {
        g.fill(x, y, x + width, y + height, 0xff111710);
        g.fillGradient(x + 1, y + 1, x + 1 + (width - 2) * (xp - MilitaryProgression.threshold(MilitaryProgression.level(xp))) / Math.max(1, MilitaryProgression.threshold(MilitaryProgression.level(xp)+1)-MilitaryProgression.threshold(MilitaryProgression.level(xp))), y + height - 1,
            0xffa0cd63, OLIVE);
        g.renderOutline(x, y, width, height, FRAME);
    }

    private static String rank(int xp) {
        return MilitaryProgression.label(xp) + " · " + MilitaryProgression.grade(xp);
    }

    private static String upper(String text) { return text.toUpperCase(java.util.Locale.ROOT); }
    private static String stars(int n) { return "★".repeat(n) + "☆".repeat(5 - n); }
    private static ResourceLocation asset(String name) { return ResourceLocation.fromNamespaceAndPath(OperationsMod.ID, name); }
    private static ResourceLocation texture(String name) { return asset("textures/gui/" + name + ".png"); }
}
