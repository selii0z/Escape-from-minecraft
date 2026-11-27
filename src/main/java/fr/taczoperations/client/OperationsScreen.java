package fr.taczoperations.client;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.taczoperations.operations.RaidLoadout;
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
        ResourceLocation texture() { return OperationsScreen.texture("hd/"+image); }
    }

    private static final Area[] AREAS = {
        new Area("Forêt Noire", "forest", 2, "Forêt, collines et installations"),
        new Area("Zone industrielle", "industrial", 3, "Usines et entrepôts"),
        new Area("Village express", "village", 3, "Maisons et ruelles"),
        new Area("Entrepôt express", "industrial", 3, "Hangars et stockage"),
        new Area("Zombiepolis", "quarantine", 4, "Ville et îles"),
        new Area("Bunker express", "underground", 4, "Galeries et salles techniques")
    };

    private record Section(int tab, String title, String subtitle) {}

    private static final Section[] SECTIONS = {
        new Section(OperationsMenu.FOREST, "Opérations", "Lancer un raid"),
        new Section(OperationsMenu.CONTRACTS, "Contrats", "Objectifs et récompenses"),
        new Section(OperationsMenu.QUESTS, "Quêtes", "Campagne marchands"),
        new Section(OperationsMenu.SHOP, "Marchands", "Matériel et ravitaillement"),
        new Section(OperationsMenu.PROGRESSION, "Progression", "Rang, XP et crédits"),
        new Section(OperationsMenu.FILES, "Dossiers", "Ennemis et renseignements"),
        new Section(OperationsMenu.SETTINGS, "Paramètres", "Options du mod")
    };

    private int offsetX;
    private int offsetY;
    private float scale;
    private int selectedArea;
    private final CampaignDashboard campaign;
    private boolean questMode;
    private boolean enterCampaignOnTick;
    private boolean night;
    private boolean duo;
    private RaidLoadout loadout;
    private int lastLoadoutTick=Integer.MIN_VALUE;
    private int historyScroll;
    private int shopSelection=0;
    private int shopLots=1;
    private static ShopBookmark pendingShop;
    private record ShopBookmark(Object connection,int containerId,int page,int category,boolean owned,boolean accessories,int offer,long time){}
    private void rememberShop(){
        var v=menu.view();
        if(v.tab()==OperationsMenu.SHOP&&shopSelection>=0&&shopSelection<v.shopRows().size())pendingShop=new ShopBookmark(minecraft.getConnection(),menu.containerId,v.page(),v.category(),v.shopOwned(),v.shopAccessories(),v.shopRows().get(shopSelection),System.nanoTime());
    }
    private int marketSelection=0;
    private net.minecraft.client.gui.components.EditBox marketPrice;
    private java.util.List<OperationsShop.Offer> shopCatalogue=java.util.List.of();
    private static final int HISTORY_ROWS = 4;
    private static final int SHOP_X=169,SHOP_Y=169,SHOP_DX=37,SHOP_DY=36,SHOP_CELL=35;
    private int shopRowAt(double x,double y){
        if(x<SHOP_X||y<SHOP_Y||x>=SHOP_X+OperationsMenu.SHOP_COLUMNS*SHOP_DX||y>=SHOP_Y+OperationsMenu.SHOP_VISIBLE_ROWS*SHOP_DY)return -1;
        if((x-SHOP_X)%SHOP_DX>=SHOP_CELL||(y-SHOP_Y)%SHOP_DY>=SHOP_CELL)return -1;
        return (int)((y-SHOP_Y)/SHOP_DY)*OperationsMenu.SHOP_COLUMNS+(int)((x-SHOP_X)/SHOP_DX);
    }

    public OperationsScreen(OperationsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        campaign=new CampaignDashboard(menu.containerId);
        questMode=menu.view().tab()==OperationsMenu.QUESTS;
        enterCampaignOnTick=questMode;
        duo = !menu.view().partner().isEmpty();
        var v=menu.view();var bookmark=pendingShop;pendingShop=null;
        if(bookmark!=null&&bookmark.connection()==net.minecraft.client.Minecraft.getInstance().getConnection()&&System.nanoTime()-bookmark.time()<15_000_000_000L&&v.tab()==OperationsMenu.SHOP&&v.page()/OperationsMenu.VENDOR_PAGES==bookmark.page()/OperationsMenu.VENDOR_PAGES&&v.category()==bookmark.category()&&v.shopOwned()==bookmark.owned()&&v.shopAccessories()==bookmark.accessories()){
            int row=v.shopRows().indexOf(bookmark.offer());if(row>=0)shopSelection=row;
        }
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(OperationsMod.MENU.get(), OperationsScreen::new);
    }

    @Override
    protected void init() {
        scale = Math.min(width / (float) WIDTH, height / (float) HEIGHT)*.95f;
        imageWidth = Math.round(WIDTH * scale);
        imageHeight = Math.round(HEIGHT * scale);
        offsetX = (width - imageWidth) / 2;
        offsetY = (height - imageHeight) / 2;
        super.init();
        var view = menu.view();
        for (int row = 0; row < SECTIONS.length; row++) {
            Section section = SECTIONS[row];
            int iconIndex = row>2?row-1:row;
            int y = 81 + row * 39;
            widget(12, y, 136, 34, section.title, () -> send(section.tab),
                () -> true, (g, hovered) -> {
                    TacticalSkin.tile(g, 12, y, 136, 34, active(section.tab), hovered);
                    if(section.tab==OperationsMenu.QUESTS)item(g,new ItemStack(Items.WRITTEN_BOOK),20,y+6,1.35F);else icon(g, iconIndex, 17, y + 4, 25);
                    text(g, section.title, 52, y + 11, 1.16F, WHITE, false);
                });
        }
        if(questMode)return;
        switch (view.tab()) {
            case OperationsMenu.FOREST -> operationWidgets();
            case OperationsMenu.PROGRESSION -> simple(690,45,125,23,"Statistiques",()->send(OperationsMenu.DETAILED_STATS));
            case OperationsMenu.DETAILED_STATS -> simple(690,45,125,23,"Progression",()->send(OperationsMenu.PROGRESSION));
            case OperationsMenu.TIMED_CONTRACTS -> {
                simple(690,79,125,29,"Classiques",()->send(OperationsMenu.CONTRACTS));
                simple(710,376,105,24,"Actualiser",()->send(OperationsMenu.REFRESH));
            }
            case OperationsMenu.CONTRACTS -> {
                simple(690,79,125,29,"Défis périodiques",()->send(OperationsMenu.TIMED_CONTRACTS));
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
            case OperationsMenu.SHOP -> shopWidgets();
            case OperationsMenu.PLAYER_MARKET -> playerMarketWidgets();
            case OperationsMenu.MERCHANT_ORDERS -> {
                simple(690,79,125,29,"Marchands",()->send(OperationsMenu.SHOP));
                for(int i=0;i<4;i++){
                    var fields=view.orders().get(i+1).split("\\|",-1);
                    int action=OperationsMenu.ORDER_FIRST+i;
                    boolean done=fields[8].equals("1");
                    simple(700,151+i*56,110,28,done?"Livrée":"Livrer",()->send(action),()->false,
                        ()->!done&&Integer.parseInt(fields[4])>=Integer.parseInt(fields[3]));
                }
                simple(700,376,110,24,"Actualiser",()->send(OperationsMenu.REFRESH));
            }
            case OperationsMenu.TRADE_JOURNAL -> {
                simple(690,79,125,29,"Marchands",()->send(OperationsMenu.SHOP));
                simple(173,376,65,22,"‹",()->send(OperationsMenu.PREVIOUS),()->false,()->view.page()>0);
                simple(750,376,65,22,"›",()->send(OperationsMenu.NEXT),()->false,()->view.page()+1<view.pages());
            }
            case OperationsMenu.BLACK_MARKET -> blackMarketWidgets();
            case OperationsMenu.GROUP -> groupWidgets();
            case OperationsMenu.SETTINGS -> {
                simple(420,145,120,29,view.showExtraction()?"Masquer":"Afficher",()->send(OperationsMenu.TOGGLE_EXTRACTION));
                simple(420,207,120,29,"Utilisation",()->send(OperationsMenu.SECURE_POUCH));
                simple(420,269,120,29,"Assurer",()->send(OperationsMenu.INSURE_GEAR));
                simple(420,331,120,29,"Ouvrir",()->send(OperationsMenu.CLAIM_INSURANCE));
                simple(581,128,232,29,"Configuration des HUD",()->minecraft.setScreen(new RaidHudSettings.Screen(this)));
                simple(581,205,232,29,"Ouvrir le groupe",()->send(OperationsMenu.GROUP));
                simple(581,328,232,29,"Réinitialiser mon compte",()->{send(OperationsMenu.ARM_WIPE);minecraft.setScreen(new AccountWipeScreen(this,menu.containerId));});
            }
            default -> { }
        }
        if (view.tab() != OperationsMenu.SETTINGS && view.tab() != OperationsMenu.FOREST && view.tab() != OperationsMenu.GROUP && view.tab() != OperationsMenu.SHOP && view.tab() != OperationsMenu.CONTRACTS && view.tab()!=OperationsMenu.TIMED_CONTRACTS && view.tab()!=OperationsMenu.BLACK_MARKET && view.tab()!=OperationsMenu.PROGRESSION && view.tab()!=OperationsMenu.TRADE_JOURNAL && view.tab()!=OperationsMenu.MERCHANT_ORDERS && view.tab()!=OperationsMenu.PLAYER_MARKET && view.tab()!=OperationsMenu.DETAILED_STATS) {
            simple(581, 243, 232, 28, "Ouvrir le groupe", () -> send(OperationsMenu.GROUP));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(questMode)return campaign.scroll((mouseX-offsetX)/scale,(mouseY-offsetY)/scale,scrollY);
        if(menu.view().tab()==OperationsMenu.SHOP&&scale>0&&scrollY!=0){
            double x=(mouseX-offsetX)/scale,y=(mouseY-offsetY)/scale;
            if(x>=SHOP_X&&x<=548&&y>=SHOP_Y&&y<313){
                var v=menu.view();int row=v.page()%OperationsMenu.VENDOR_PAGES;
                if(scrollY>0&&row>0)send(OperationsMenu.PREVIOUS);
                else if(scrollY<0&&row+1<v.shopPages())send(OperationsMenu.NEXT);
                return true;
            }
        }
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
        for (int i = 0; i < AREAS.length; i++) {
            final int index = i;
            int x = 166 + i % 2 * 196;
            int y = 133 + i / 2 * 92;
            widget(x, y, 190, 87, AREAS[i].title, () -> selectedArea = index,
                () -> true, (g, hovered) -> mapCard(g, index, x, y, hovered));
        }
        simple(582, 321, 54, 27, "☀ Jour", () -> night = false, () -> !night, () -> true);
        simple(643, 321, 53, 27, "☾ Nuit", () -> night = true, () -> night, () -> true);
        simple(711, 321, 40, 27, "Solo", () -> duo = false, () -> !duo, () -> true);
        simple(756, 321, 63, 27, "Duo", () -> {
            if (menu.view().partner().isEmpty()) {
                send(OperationsMenu.GROUP);
            } else {
                duo = true;
            }
        }, () -> duo, () -> true);
        simple(580,407,117,25,"Jouer Scav",()->{
            int area=selectedArea<2?selectedArea:(selectedArea==4?4:selectedArea==5?1:selectedArea==3?2:3)+1;
            send(OperationsMenu.SCAV_FIRST+area*2+(night?1:0));
        },()->false,()->true);
        simple(703,407,117,25,"Butin Scav",()->send(OperationsMenu.SCAV_COLLECT),()->false,()->true);
        widget(580, 369, 240, 32, "Lancer l’opération", () -> {
            if (duo && menu.view().partner().isEmpty()) {
                send(OperationsMenu.GROUP);
                return;
            }
            if(selectedArea>=2){
                int map=selectedArea==4?4:selectedArea==5?1:selectedArea==3?2:3;
                send(OperationsMenu.EXPRESS_FIRST+(map-1)*4+(night?1:0)+(duo?2:0));
            }else if (selectedArea == 1) {
                send(OperationsMenu.INDUSTRIAL_FIRST + (night ? 1 : 0) + (duo ? 2 : 0));
            } else {
                send(duo ? (night ? OperationsMenu.DEPART_DUO_NIGHT : OperationsMenu.DEPART_DUO)
                    : (night ? OperationsMenu.DEPART_NIGHT : OperationsMenu.DEPART));
            }
        }, () -> true, (g, hovered) -> {
            boolean enabled = true;
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

    void receiveCampaign(fr.taczoperations.operations.CampaignTabData data){campaign.receive(data);}
    @Override protected void containerTick(){
        super.containerTick();
        if(enterCampaignOnTick){enterCampaignOnTick=false;campaign.enter();}
    }
    private void send(int action) {
        if(action==OperationsMenu.QUESTS){
            if(!questMode){questMode=true;rebuildWidgets();campaign.enter();}
            return;
        }
        if(questMode){
            if(action==menu.view().tab()&&!campaign.changed()){questMode=false;rebuildWidgets();return;}
            questMode=false;rebuildWidgets();
        }
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private boolean active(int tab) {
        if(questMode)return tab==OperationsMenu.QUESTS;
        return tab != OperationsMenu.QUESTS && (tab == menu.view().tab() || (tab == OperationsMenu.FOREST && menu.view().tab() == OperationsMenu.GROUP)
            ||(tab==OperationsMenu.SHOP&&menu.view().tab()==OperationsMenu.PLAYER_MARKET));
    }

    @Override
    public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);
        if(questMode||scale<=0||minecraft==null||minecraft.player==null)return;
        double x=(mouseX-offsetX)/scale,y=(mouseY-offsetY)/scale;
        ItemStack hovered=ItemStack.EMPTY;
        int tab=menu.view().tab();
        if(tab==OperationsMenu.SHOP){
            int shopRow=shopRowAt(x,y);
            if(shopRow>=0){
                var offer=shopOffer(shopRow);
                if(offer!=null)hovered=offer.stack().get();
            }else if(x>=581&&x<815&&y>=265&&y<369){
                int k=((int)(y-265)/26)*9+(int)(x-581)/26;
                if(k>=0&&k<36)hovered=minecraft.player.getInventory().getItem(k<27?k+9:k-27);
            }
        }else if(tab==OperationsMenu.PLAYER_MARKET&&x>=169&&x<545&&y>=171&&y<330){
            int index=((int)(y-171)/53)*4+(int)(x-169)/94;
            var rows=menu.view().playerMarket().offers();
            if(index>=0&&index<rows.size()&&(x-169)%94<90&&(y-171)%53<50)hovered=rows.get(index).item();
        }else if(tab==OperationsMenu.BLACK_MARKET&&x>=173&&x<617&&y>=142&&y<212){
            int index=(int)(x-173)/74;
            if((x-173)%74<70)hovered=fr.taczoperations.operations.BlackMarket.preview(Long.parseLong(menu.view().market().getFirst()),index);
        }
        if(!hovered.isEmpty()){
            g.renderTooltip(font,hovered,mouseX,mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
        canvas(g);
        TacticalSkin.shell(g);
        if(questMode){
            TacticalSkin.logo(g);
            text(g,"CAMPAGNE  /",251,24,1.75F,WHITE,true);
            int merchantX=251+Math.round(font.width(styled("CAMPAGNE  /",true))*1.75f)+12;
            text(g,campaign.merchant(),merchantX,25,1.55F,TacticalSkin.ORANGE,true);
            campaign.draw(g,(mouseX-offsetX)/scale,(mouseY-offsetY)/scale);
            g.pose().popPose();return;
        }
        if (menu.view().tab() == OperationsMenu.CONTRACTS || menu.view().tab()==OperationsMenu.TIMED_CONTRACTS || menu.view().tab()==OperationsMenu.BLACK_MARKET || menu.view().tab()==OperationsMenu.TRADE_JOURNAL || menu.view().tab()==OperationsMenu.MERCHANT_ORDERS) {
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
        TacticalSkin.logo(g);

        panel(g, 528, 7, 195, 49, 0xec0f1311);
        if (minecraft.player != null) {
            PlayerFaceRenderer.draw(g, minecraft.player.getSkin(), 539, 16, 30);
            text(g, fit(minecraft.player.getGameProfile().getName(), 124), 580, 12, 1.25F, WHITE, true);
        }
        int xp = questMode&&campaign.latest()!=null?campaign.latest().experience():menu.view().experience();
        text(g, "Rang · " + rank(xp), 601, 27, .91F, WHITE, false);
        icon(g, 3, 575, 29, 20);
        progress(g, 601, 39, 108, 8, xp);
        text(g, "XP · " + xp + " / " + MilitaryProgression.threshold(MilitaryProgression.level(xp)+1), 605, 48, .74F, SOFT, false);

        panel(g, 730, 7, 98, 49, 0xec0f1311);
        item(g, new ItemStack(Items.GOLD_INGOT), 740, 19, 1.25F);
        text(g, "Crédits", 766, 16, 1.04F, SOFT, false);
        text(g, Integer.toString(questMode&&campaign.latest()!=null?campaign.latest().credits():menu.view().credits()), 766, 29, 1.40F, WHITE, true);
        g.fillGradient(9, 65, 824, 68, 0xff71675b, 0xff292929);
    }

    private void operationDetails(GuiGraphics g) {
        Area area = AREAS[selectedArea];
        boolean open = true;
        panel(g, 569, 85, 258, 83, 0xc5272e20);
        TacticalSkin.picture(g,area.texture(),574,91,87,69);
        TacticalSkin.outline(g,572,89,91,73,0xffc4b68c);
        icon(g, selectedArea + 6, 668, 100, 16);
        text(g, upper(area.title), 685, 102, area.title.length() > 19 ? 1.00F : 1.25F, WHITE, true);
        String[] lines = selectedArea == 0
            ? new String[]{"Zone forestière dense, scierie", "abandonnée et camp militaire.", "Deux voies d’extraction."}
            : selectedArea == 1 ? new String[]{"Complexe de fret et ateliers", "Trois voies d’extraction.", "Checkpoint, clé ou crédits."}
            : open?new String[]{area.environment,selectedArea==4?"Raid classique · 45 minutes":"Raid express · 15 minutes","Deux extractions gratuites."}:new String[]{area.environment, "Zone de déploiement à venir."};
        for (int i = 0; i < lines.length; i++) text(g, lines[i], 673, 124 + i * 11, .94F, SOFT, false);

        panel(g, 575, 173, 249, 63, DARK);
        detail(g, 179, "✦", "Difficulté", stars(area.difficulty));
        detail(g, 193, "◷", "Durée", open ? selectedArea==0||selectedArea==4?"45 minutes":selectedArea==1?"30 minutes":"15 minutes" : "À venir");
        detail(g, 207, "♠", "Environnement", area.environment);
        detail(g, 221, "⊕", "Menace", open ? "Gardes armés" : "À venir");

        panel(g, 575, 241, 249, 54, DARK);
        text(g, "PRÉPARATION · ÉQUIPEMENT PERSONNEL", 585, 245, .84F, GOLD, true);
        if(open&&minecraft.player!=null){
            int tick=minecraft.player.tickCount;
            if(loadout==null||tick-lastLoadoutTick>=10||tick<lastLoadoutTick){
                loadout=RaidLoadout.inspect(minecraft.player);
                lastLoadoutTick=tick;
            }
            renderLoadout(g,loadout);
        }
        panel(g, 575, 300, 127, 62, DARK);
        panel(g, 705, 300, 119, 62, DARK);
        text(g, "Moment de l’opération", 586, 305, .91F, WHITE, true);
        text(g, "Déploiement", 712, 305, .91F, WHITE, true);
        if (open) {
            text(g, duo ? fit(menu.view().partner(), 104) : "Groupe sur invitation", 711, 351, .75F, SOFT, false);
        }
    }

    private void renderLoadout(GuiGraphics g,RaidLoadout kit){
        boolean usable=kit.guns()>0&&kit.condition()>0&&!kit.jammed();
        String condition=kit.guns()==0?"":kit.jammed()?" · enrayée":" · "+Math.round(kit.condition()/100F)+"%";
        text(g,fit(kit.weapon()+condition,235),585,256,.83F,usable&&kit.condition()>=7000?LIME:GOLD,false);
        text(g,fit("Munitions : "+kit.loaded()+" + "+kit.reserve(),145),585,267,.79F,kit.reserve()<30?GOLD:LIME,false);
        text(g,fit("Soins : "+kit.medical()+" (saign. "+kit.bleeding()+")",143),709,267,.75F,kit.bleeding()==0?GOLD:LIME,false);
        text(g,"Protection : "+kit.armor()+"/4",585,278,.79F,kit.armor()<4||kit.armorCondition()<=20?GOLD:LIME,false);
        text(g,"Nourriture : "+kit.food(),709,278,.79F,kit.food()<3?GOLD:LIME,false);
        text(g,"Places libres : "+kit.free()+"/36",585,289,.72F,kit.free()<6?GOLD:SOFT,false);
        if(night)text(g,kit.nightOptics()?"Vision nuit : OK":"Vision nuit absente",709,289,.70F,kit.nightOptics()?LIME:GOLD,false);
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
        text(g, index<=1||index==4?"Classique":"15 min", x + 145, y + 73, .78F, WHITE, true);
        frame(g, x, y, 190, 87, selected, hovered);
    }

    private void secondaryPage(GuiGraphics g) {
        var view = menu.view();
        switch (view.tab()) {
            case OperationsMenu.TIMED_CONTRACTS -> {
                text(g,"DÉFIS PÉRIODIQUES",173,82,1.5F,WHITE,true);
                text(g,"Progression automatique à chaque bilan de raid",173,108,.90F,SOFT,false);
                var data=view.timedContracts();
                for(int i=0;i<6;i++){
                    int x=i<3?170:500, row=i%3, y=153+row*66;
                    if(row==0)text(g,i<3?"QUOTIDIENS":"HEBDOMADAIRES",x,134,1.15F,GOLD,true);
                    int progress=data.get(i*3),target=data.get(i*3+1);
                    boolean done=data.get(i*3+2)!=0;
                    panel(g,x,y,315,61,DARK);
                    text(g,fr.taczoperations.operations.TimedContracts.title(i),x+10,y+8,1.1F,WHITE,true);
                    text(g,done?"Terminé":progress+" / "+target,x+218,y+8,.95F,done?LIME:SOFT,true);
                    g.fill(x+10,y+27,x+305,y+34,0xff30382b);
                    g.fill(x+10,y+27,x+10+(int)(295L*Math.min(progress,target)/Math.max(1,target)),y+34,0xff8fdc57);
                    text(g,"+"+fr.taczoperations.operations.TimedContracts.credits(i)+" crédits · +"+fr.taczoperations.operations.TimedContracts.xp(i)+" XP",x+10,y+43,.93F,GOLD,false);
                }
                text(g,"Renouvellement : minuit UTC / lundi minuit UTC",173,364,.85F,SOFT,false);
            }
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
            case OperationsMenu.SHOP -> drawShop(g);
            case OperationsMenu.PLAYER_MARKET -> drawPlayerMarket(g);
            case OperationsMenu.MERCHANT_ORDERS -> {
                text(g,"COMMANDES SPÉCIALES",173,82,1.50F,GOLD,true);
                text(g,"Quatre livraisons quotidiennes · renouvellement à minuit UTC",173,108,.9F,SOFT,false);
                for(int i=0;i<4;i++){
                    var f=view.orders().get(i+1).split("\\|",-1);
                    int y=132+i*56;
                    boolean done=f[8].equals("1");
                    panel(g,169,y,650,52,DARK);
                    text(g,f[0]+" · "+f[1],181,y+5,1.04F,WHITE,true);
                    text(g,fit(f[2]+" ×"+f[3]+" · Possédés : "+f[4],455),181,y+20,.88F,done?SOFT:Integer.parseInt(f[4])>=Integer.parseInt(f[3])?LIME:GOLD,false);
                    text(g,"+"+f[5]+" cr.  ·  +"+f[6]+" XP  ·  +"+f[7]+" rép.",181,y+35,.91F,done?SOFT:LIME,true);
                }
                text(g,"Les objets remis sont consommés. Aucune obligation d'accepter.",173,371,.83F,SOFT,false);
            }
            case OperationsMenu.TRADE_JOURNAL -> {
                text(g,"JOURNAL DES TRANSACTIONS",173,82,1.45F,GOLD,true);
                text(g,"50 dernières opérations · heure UTC",173,106,.9F,SOFT,false);
                var sums=view.journalTotals();
                String[] labels={"VENTES","ACHATS","RÉPARATIONS"};
                for(int i=0;i<3;i++){
                    int x=169+i*220;
                    panel(g,x,123,210,38,DARK);
                    text(g,labels[i],x+9,128,.84F,SOFT,true);
                    text(g,(i==0?"+":"-")+numberLong(sums.get(i))+" cr.",x+9,142,1.05F,i==0?LIME:0xffdf9c94,true);
                }
                if(view.journal().isEmpty())text(g,"Aucune transaction enregistrée.",181,193,1,SOFT,false);
                for(int i=0;i<view.journal().size();i++){
                    var fields=view.journal().get(i).split("\\|",-1);
                    if(fields.length!=7)continue;
                    int y=166+i*34;
                    long delta=Long.parseLong(fields[5]);
                    panel(g,169,y,650,31,DARK);
                    text(g,fields[0]+" · "+fields[1]+" · "+fields[2],181,y+4,.82F,SOFT,false);
                    text(g,fit(fields[3]+" ×"+fields[4],370),181,y+16,.91F,WHITE,true);
                    text(g,(delta>=0?"+":"")+numberLong(delta)+" cr.",595,y+4,.98F,delta>=0?LIME:0xffdf9c94,true);
                    text(g,"Solde : "+numberLong(Long.parseLong(fields[6])),595,y+17,.82F,SOFT,false);
                }
                centered(g,(view.page()+1)+" / "+view.pages(),495,382,1,SOFT,false);
            }
            case OperationsMenu.BLACK_MARKET -> drawBlackMarket(g);
            case OperationsMenu.DETAILED_STATS -> {
                pageTitle(g,"»","STATISTIQUES DÉTAILLÉES","Nouvelles données enregistrées depuis cette version");
                var t=view.raidTotals();
                String[] labels={"Tirs enregistrés","Impacts sur ennemis","Impacts à la tête","PMC éliminés","Scavs éliminés","Boss éliminés","Distance parcourue"};
                for(int i=0;i<labels.length;i++){
                    int y=126+i*37;panel(g,169,y,379,31,DARK);
                    text(g,labels[i],185,y+10,.96F,WHITE,false);
                    text(g,i==6?String.format(java.util.Locale.FRANCE,"%.2f km",t.get(17+i)/100000.0):numberLong(t.get(17+i)),413,y+10,.95F,LIME,true);
                }
                panel(g,575,126,249,95,DARK);
                text(g,"PRÉCISION",585,137,1.10F,GOLD,true);
                text(g,"Les impacts peuvent être multiples",585,157,.85F,SOFT,false);
                text(g,"pour un même tir de fusil à pompe.",585,173,.85F,SOFT,false);
                text(g,"Têtes / impacts : "+(t.get(18)==0?"—":String.format(java.util.Locale.FRANCE,"%.1f %%",100.0*t.get(19)/t.get(18))),585,194,.9F,LIME,true);
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
                text(g, "FOR = forêt · IND = industrie · K/C = kills/coffres", 181, 321, .76F, SOFT, false);
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
                notes(g, 207, "Voies de sortie", "Restez 5 secondes dans le checkpoint balisé.", "Activez le relais pour ouvrir la seconde sortie.");
            }
            case OperationsMenu.SETTINGS -> {
                pageTitle(g, "⚙", "PARAMÈTRES", "Options du Tableau des opérations");
                panel(g, 169, 132, 379, 54, DARK);
                text(g, "Indication d’extraction", 185, 144, 1.13F, WHITE, true);
                text(g, view.showExtraction() ? "Coordonnées affichées en raid" : "Coordonnées masquées en raid",
                    185, 163, .95F, SOFT, false);
                panel(g,169,194,379,54,DARK);
                text(g,"Coffre sécurisé",185,206,1.13F,WHITE,true);
                text(g,"5 places · clic droit en main",185,225,.95F,SOFT,false);
                panel(g,169,256,379,54,DARK);
                text(g,"Assurance du matériel",185,268,1.13F,WHITE,true);
                text(g,"100 crédits / équipement",185,287,.95F,SOFT,false);
                panel(g,169,318,379,54,DARK);
                text(g,"Espace de récupération",185,330,1.13F,WHITE,true);
                text(g,"Après 30 min · chance 95 %",185,349,.95F,SOFT,false);
                panel(g,575,88,249,80,DARK);
                text(g,"AFFICHAGE EN RAID",585,98,1.1F,GOLD,true);
                text(g,"Position, taille et opacité des HUD.",585,114,.91F,SOFT,false);
                panel(g,575,175,249,69,DARK);
                text(g,"GROUPE",585,185,1.1F,GOLD,true);
                text(g,view.partner().isEmpty()?"Solo · invitations disponibles":"Équipier : "+view.partner(),585,197,.8F,SOFT,false);
                panel(g,575,260,249,122,DARK);
                text(g,"ZONE DE RÉINITIALISATION",585,273,1.0F,0xffe18979,true);
                text(g,"Efface uniquement ta progression EFM.",585,294,.9F,SOFT,false);
                text(g,"Action définitive.",585,309,.9F,SOFT,false);
                return;
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
        if (view.tab() == OperationsMenu.CONTRACTS || view.tab()==OperationsMenu.TIMED_CONTRACTS || view.tab()==OperationsMenu.BLACK_MARKET || view.tab()==OperationsMenu.TRADE_JOURNAL || view.tab()==OperationsMenu.MERCHANT_ORDERS) return;
        if (view.tab() == OperationsMenu.SHOP || view.tab()==OperationsMenu.PLAYER_MARKET || view.tab()==OperationsMenu.DETAILED_STATS) return;
        if(view.tab()==OperationsMenu.PROGRESSION) {
            var totals=view.raidTotals();
            panel(g,575,88,249,145,DARK);
            text(g,"STATISTIQUES DE RAID",585,97,1.17F,GOLD,true);
            long raids=totals.get(0),wins=totals.get(1),losses=totals.get(2),kills=totals.get(3);
            text(g,"Raids terminés : "+raids,585,117,.93F,WHITE,false);
            text(g,"Extractions : "+wins+" · Morts : "+losses,585,132,.93F,SOFT,false);
            text(g,"Survie : "+(raids==0?"—":String.format(java.util.Locale.FRANCE,"%.1f %%",100.0*wins/raids)),585,147,.93F,LIME,true);
            text(g,"Éliminations : "+kills+" · K/D : "+(losses==0?"—":String.format(java.util.Locale.FRANCE,"%.2f",(double)kills/losses)),585,162,.9F,WHITE,false);
            text(g,"Record : "+totals.get(8)+" éliminations",585,177,.91F,SOFT,false);
            text(g,"Coffres : "+totals.get(4)+" · Contrats : "+totals.get(5),585,192,.89F,SOFT,false);
            long seconds=totals.get(6);
            text(g,"Temps : "+seconds/3600+" h "+seconds%3600/60+" min",585,207,.89F,SOFT,false);
            panel(g,575,240,249,164,DARK);
            text(g,"RECORDS PERSONNELS",585,249,1.09F,GOLD,true);
            String bestLoot=totals.get(15)==0?"—":numberLong(totals.get(9))+" cr.";
            text(g,"Meilleur butin : "+bestLoot,585,269,.94F,LIME,true);
            text(g,"Butin cumulé : "+(totals.get(15)==0?"—":numberLong(totals.get(10))+" cr."),585,285,.92F,SOFT,false);
            text(g,"Série actuelle : "+totals.get(11)+" extraction(s)",585,301,.93F,WHITE,false);
            text(g,"Meilleure série : "+totals.get(12),585,317,.93F,LIME,false);
            text(g,"Record XP : "+numberLong(totals.get(13))+" · Fouilles : "+totals.get(14),585,333,.87F,SOFT,false);
            text(g,"XP totale : "+numberLong(totals.get(7)),585,349,.93F,WHITE,false);
            text(g,"Butin évalué : "+totals.get(15)+" raid(s)",585,365,.81F,SOFT,false);
            text(g,"Objets revendables : "+numberLong(totals.get(16)),585,381,.81F,SOFT,false);
            return;
        }
        image(g, AREAS[FOREST].texture(), 578, 88, 239, 108);
        g.renderOutline(578, 88, 239, 108, FRAME);
        text(g, "FORÊT NOIRE", 585, 205, 1.24F, WHITE, true);
        text(g, view.partner().isEmpty() ? "Solo · invitations disponibles" : "Équipier : " + view.partner(),
            585, 224, .92F, SOFT, false);
    }

    private static String numberLong(long value){
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(value);
    }

    private OperationsShop vendor() {
        return OperationsShop.values()[menu.view().page() / OperationsMenu.VENDOR_PAGES];
    }

    private boolean unlocked(fr.taczoperations.operations.RaidRelic relic) {
        return (menu.view().unlocks() & (1 << relic.ordinal())) != 0;
    }



    private void inspectSelected(int row){DiscoveryClient.request(menu.containerId,1,row);}
    private void discoveryItem(GuiGraphics g,ItemStack stack,int x,int y,float size){
        item(g,stack,x,y,size);
    }
    private String discoveredName(ItemStack stack,String name){return name;}
    private String[] blackOffer(int index){
        return menu.view().market().get(index+1).split("\\|",-1);
    }
    private boolean blackCanBuy(){
        var o=blackOffer(shopSelection);
        return !o[2].equals("0")&&o[3].equals("1")&&menu.view().credits()>=Integer.parseInt(o[1]);
    }
    private void blackMarketWidgets(){
        simple(555,79,125,29,"Transactions",()->send(OperationsMenu.TRADE_JOURNAL));
        simple(690,79,125,29,"Marchands",()->send(OperationsMenu.SHOP));
        long day=Long.parseLong(menu.view().market().getFirst());
        for(int i=0;i<6;i++){
            int row=i,x=173+i*74,y=142;
            String[] o=blackOffer(row);
            var stack=fr.taczoperations.operations.BlackMarket.preview(day,row);
            widget(x,y,70,70,discoveredName(stack,o[0]),()->shopSelection=row,()->true,(g,hover)->{
                g.fillGradient(x,y,x+70,y+70,0xff3a4332,0xff1d231c);
                frame(g,x,y,70,70,shopSelection==row,hover);
                discoveryItem(g,stack,x+11,y+13,3F);
                text(g,numberLong(Long.parseLong(o[1])),x+4,y+4,.9F,GOLD,true);
                text(g,"Stock : "+o[2],x+4,y+58,.82F,o[2].equals("0")?SOFT:LIME,true);
            });
        }
        widget(640,252,172,28,"Acheter",()->send(OperationsMenu.MARKET_FIRST+shopSelection),()->blackCanBuy(),(g,hover)->{
            boolean ok=blackCanBuy();key(g,640,252,172,28,ok,hover,ok);
            centered(g,blackOffer(shopSelection)[2].equals("0")?"Épuisé":"Acheter · "+blackOffer(shopSelection)[1]+" cr.",726,261,1,ok?WHITE:SOFT,true);
        });
        simple(640,318,172,24,"Actualiser",()->send(OperationsMenu.REFRESH));
    }
    private void drawBlackMarket(GuiGraphics g){
        text(g,"MARCHÉ NOIR",173,82,1.55F,GOLD,true);
        text(g,"6 offres limitées · renouvellement à minuit UTC",173,109,.92F,SOFT,false);
        var o=blackOffer(shopSelection);
        panel(g,173,231,454,92,DARK);
        var stack=fr.taczoperations.operations.BlackMarket.preview(Long.parseLong(menu.view().market().getFirst()),shopSelection);
        discoveryItem(g,stack,185,242,4F);
        text(g,fit(discoveredName(stack,o[0]),350),266,241,1.18F,WHITE,true);
        text(g,fit(o[4],350),266,264,.9F,o[3].equals("1")?SOFT:0xffdf9c94,false);
        text(g,numberLong(Long.parseLong(o[1]))+" cr. · stock "+o[2],266,288,1.1F,GOLD,true);
        text(g,"Stocks personnels · accès des marchands conservés",173,391,.85F,SOFT,false);
    }

    private fr.taczoperations.operations.PlayerMarket.Offer marketOffer(){
        var rows=menu.view().playerMarket().offers();
        return marketSelection>=0&&marketSelection<rows.size()?rows.get(marketSelection):null;
    }
    private int listingPrice(){
        if(marketPrice==null)return 0;
        try{int price=Integer.parseInt(marketPrice.getValue());return price>=1&&price<=fr.taczoperations.operations.PlayerMarket.MAX_PRICE?price:0;}
        catch(NumberFormatException ignored){return 0;}
    }
    private void playerMarketWidgets(){
        var v=menu.view();var page=v.playerMarket();
        simple(169,126,118,20,page.ownOnly()?"Toutes les offres":"Mes annonces",()->send(OperationsMenu.PLAYER_OWN),()->page.ownOnly(),()->true);
        simple(291,126,118,20,"Actualiser",()->send(OperationsMenu.REFRESH));
        simple(413,126,132,20,"Marchands",()->send(OperationsMenu.SHOP));
        for(int i=0;i<SHOP_FILTERS.length;i++){
            final int cat=i;
            simple(169+i*54,149,52,17,SHOP_FILTERS[i],()->send(OperationsMenu.PLAYER_FILTER_FIRST+cat),()->page.category()==cat,()->true);
        }
        for(int i=0;i<page.offers().size();i++){
            final int row=i;var o=page.offers().get(i);int x=169+(i%4)*94,y=171+(i/4)*53;
            widget(x,y,90,50,discoveredName(o.item(),o.item().getHoverName().getString()),()->marketSelection=row,()->true,(g,hover)->{
                g.fillGradient(x,y,x+90,y+50,o.own()?0xff435037:0xff343e31,0xff1d231d);
                frame(g,x,y,90,50,marketSelection==row,hover);
                discoveryItem(g,o.item(),x+30,y+7,2.1F);
                text(g,numberLong(o.price())+" cr.",x+4,y+3,.82F,GOLD,true);
                text(g,"x"+o.item().getCount(),x+4,y+38,.82F,WHITE,true);
                if(o.own())text(g,"MOI",x+66,y+38,.76F,LIME,true);
                else if(fr.taczoperations.operations.RaidOrigin.found(o.item()))text(g,"RAID",x+63,y+38,.76F,LIME,true);
            });
        }
        simple(169,337,60,22,"‹",()->send(OperationsMenu.PREVIOUS),()->false,()->v.page()>0);
        simple(485,337,60,22,"›",()->send(OperationsMenu.NEXT),()->false,()->v.page()+1<v.pages());
        widget(581,185,234,25,"Confirmer l'offre",()->{send(OperationsMenu.PLAYER_OFFER_FIRST+marketSelection);},
            ()->marketOffer()!=null&&(marketOffer().own()||v.credits()>=marketOffer().price()),(g,hover)->{
                var o=marketOffer();boolean ok=o!=null&&(o.own()||v.credits()>=o.price());
                key(g,581,185,234,25,ok,hover,ok);
                centered(g,o==null?"Sélectionne une offre":o.own()?"Annuler et récupérer":"Acheter le lot",698,193,1,ok?WHITE:SOFT,true);
            });
        marketPrice=new net.minecraft.client.gui.components.EditBox(font,offsetX+Math.round(581*scale),
            offsetY+Math.round(296*scale),Math.round(111*scale),Math.max(14,Math.round(23*scale)),Component.literal("Prix total"));
        marketPrice.setMaxLength(9);marketPrice.setFilter(t->t.matches("[0-9]*"));marketPrice.setValue("100");
        addRenderableWidget(marketPrice);
        simple(699,296,116,23,"Publier le lot",()->net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new fr.taczoperations.operations.ListPlayerItem(menu.containerId,listingPrice())),()->false,
            ()->!page.held().isEmpty()&&listingPrice()>0&&page.ownCount()<10);
        simple(581,369,234,27,"Retirer les crédits",()->send(OperationsMenu.PLAYER_CLAIM),()->false,()->page.pending()>0);
    }
    private void drawPlayerMarket(GuiGraphics g){
        var v=menu.view();var page=v.playerMarket();
        pageTitle(g,"▱","MARCHÉ JOUEURS","Échange entre aventuriers · prix du lot");
        for(int i=0;i<fr.taczoperations.operations.PlayerMarket.PAGE_SIZE;i++){
            int x=169+(i%4)*94,y=171+(i/4)*53;
            g.fill(x,y,x+90,y+50,0xff1b201b);g.renderOutline(x,y,90,50,0xff343d32);
        }
        if(page.offers().isEmpty())centered(g,"Aucune annonce disponible",357,247,1,SOFT,false);
        centered(g,"Page "+(v.page()+1)+" / "+v.pages(),357,344,1,SOFT,false);
        text(g,"Tarifs libres · annonces classées par prix",169,375,.91F,SOFT,false);
        text(g,"Taxe sur les ventes : 5 % (arrondie à l'entier inférieur)",169,390,.83F,SOFT,false);
        text(g,"OFFRE SÉLECTIONNÉE",581,88,1.04F,GOLD,true);
        panel(g,578,106,242,73,0xff20251f);
        var o=marketOffer();
        if(o!=null){
            discoveryItem(g,o.item(),585,115,3F);
            text(g,fit(discoveredName(o.item(),o.item().getHoverName().getString()),174),640,113,.92F,WHITE,true);
            text(g,"Vendeur : "+fit(o.seller(),118),640,129,.87F,SOFT,false);
            text(g,numberLong(o.price())+" cr. · x"+o.item().getCount(),640,144,.92F,GOLD,true);
            text(g,fr.taczoperations.operations.RaidOrigin.found(o.item())?"Trouvé en raid":"Matériel standard",640,159,.82F,SOFT,false);
        }else text(g,"Sélectionne une offre",589,139,.94F,SOFT,false);
        text(g,"METTRE EN VENTE · "+page.ownCount()+"/10",581,222,1.02F,GOLD,true);
        panel(g,578,239,242,50,0xff20251f);
        if(!page.held().isEmpty()){
            discoveryItem(g,page.held(),584,244,2.2F);
            text(g,fit(discoveredName(page.held(),page.held().getHoverName().getString()),178),625,246,.92F,WHITE,true);
            text(g,"Lot : x"+page.held().getCount()+" · main principale",625,263,.83F,SOFT,false);
        }else{
            text(g,"Tiens un objet vendable en main",584,245,.86F,SOFT,false);
            text(g,"avant d'ouvrir le tableau.",584,261,.86F,SOFT,false);
        }
        int price=listingPrice();
        text(g,price>0?"Recette nette : "+numberLong(price-price/20)+" cr.":"Prix requis : 1 à 100 000 000 cr.",581,324,.83F,price>0?SOFT:0xffdf9c94,false);
        text(g,"CRÉDITS À RETIRER",581,346,1.01F,GOLD,true);
        text(g,numberLong(page.pending())+" cr.",701,346,1.02F,LIME,true);
    }

    private static final String[] SHOP_FILTERS={"Tout","Armes","Munitions","Access.","Protections","Soins","Divers"};
    private OperationsShop.Offer shopOffer(int row){
        var rows=menu.view().shopRows();
        if(row<0||row>=rows.size())return null;
        var offers=shopCatalogue;
        int index=rows.get(row);
        return index>=0&&index<offers.size()?offers.get(index):null;
    }
    private int shopPrice(OperationsShop.Offer o){
        return o.action()==OperationsShop.Action.SELL?fr.taczoperations.operations.ResaleMarket.price(o,Long.parseLong(menu.view().market().getFirst())):OperationsShop.purchasePrice(o,menu.view().commerceDiscount());
    }
    private boolean shopLocked(OperationsShop.Offer o){
        return o.action()==OperationsShop.Action.BUY&&(OperationsShop.reputationLevel(menu.view().reputation().get(vendor().ordinal()),menu.view().experience())<o.loyaltyLevel()||o.key()!=null&&!unlocked(o.key()));
    }
    private boolean shopCanTrade(boolean all){
        var o=shopOffer(shopSelection);var v=menu.view();
        if(o==null)return false;
        if(o.action()==OperationsShop.Action.SELL){
            int n=v.saleCounts().get(shopSelection);
            return n>=(all?2:1)&&(long)v.credits()+(long)shopPrice(o)*(all?n:1)<=Integer.MAX_VALUE;
        }
        if(all)return false;
        if(o.action()==OperationsShop.Action.REPAIR){var quote=vendor().repairQuote(minecraft.player,o);return quote.cost()>0&&v.credits()>=quote.cost();}
        if(o.action()==OperationsShop.Action.UNLOCK)return !unlocked(o.key());
        return !shopLocked(o)&&v.credits()>=(long)shopPrice(o)*(o.action()==OperationsShop.Action.BUY?shopLots:1);
    }
    private void shopWidgets(){
        var v=menu.view();var merchant=vendor();
        for(var m:OperationsShop.values()){
            int x=169+m.ordinal()*95;
            simple(x,126,92,18,m.title,()->send(OperationsMenu.VENDOR_FIRST+m.ordinal()),()->m==merchant,()->true);
        }
        for(int i=0;i<SHOP_FILTERS.length;i++){
            final int cat=i;
            simple(169+i*54,146,52,17,SHOP_FILTERS[i],()->send(OperationsMenu.SHOP_CATEGORY_FIRST+cat),
                ()->!v.shopOwned()&&(v.shopAccessories()?cat==3:v.category()==cat),()->true);
        }
        // Selection is local; only explicit confirmation submits the server-validated offer.
        var offers=merchant.offers();
        shopCatalogue=offers;
        for(int i=0;i<v.shopRows().size();i++){
            final int row=i,index=v.shopRows().get(i);
            if(index<0||index>=offers.size())continue;
            var o=offers.get(index);var stack=o.stack().get();
            int x=SHOP_X+(i%OperationsMenu.SHOP_COLUMNS)*SHOP_DX,y=SHOP_Y+(i/OperationsMenu.SHOP_COLUMNS)*SHOP_DY;
            widget(x,y,SHOP_CELL,SHOP_CELL,discoveredName(stack,o.title()),()->{shopSelection=row;shopLots=1;},()->true,(g,hover)->{
                int tier=o.loyaltyLevel();
                int top=tier>=4?0xff42374b:tier==3?0xff32434a:0xff354030;
                g.fillGradient(x,y,x+SHOP_CELL,y+SHOP_CELL,top,0xff1c211d);
                frame(g,x,y,SHOP_CELL,SHOP_CELL,shopSelection==row,hover);
                if(o.action()==OperationsShop.Action.REPAIR)item(g,stack,x+5,y+8,1.5F);else discoveryItem(g,stack,x+5,y+8,1.5F);
                String cost=o.action()==OperationsShop.Action.UNLOCK?"Accès":o.action()==OperationsShop.Action.REPAIR?"Répar.":numberLong(shopPrice(o));
                text(g,fit(cost,43),x+2,y+2,.72F,GOLD,true);
                if(shopLocked(o))text(g,"F"+tier,x+21,y+25,.7F,0xffe5a9a0,true);
                else if(o.action()==OperationsShop.Action.SELL)text(g,"x"+v.saleCounts().get(row),x+2,y+25,.7F,LIME,true);
                else if(stack.getCount()>1)text(g,"x"+stack.getCount(),x+2,y+25,.7F,SOFT,true);
            });
        }
        simple(169,317,32,20,"↑",()->send(OperationsMenu.PREVIOUS),()->false,()->v.page()%OperationsMenu.VENDOR_PAGES>0);
        simple(513,317,32,20,"↓",()->send(OperationsMenu.NEXT),()->false,()->v.page()%OperationsMenu.VENDOR_PAGES+1<v.shopPages());
        simple(169,344,122,25,v.shopOwned()?"Achats":"Mes reventes",()->send(OperationsMenu.SHOP_OWNED_FILTER),()->v.shopOwned(),()->true);
        simple(295,344,122,25,"Marché noir",()->send(OperationsMenu.BLACK_MARKET));
        simple(421,344,124,25,"Marché joueurs",()->send(OperationsMenu.PLAYER_MARKET));
        simple(169,376,184,22,"Transactions",()->send(OperationsMenu.TRADE_JOURNAL));
        simple(358,376,187,22,"Commandes",()->send(OperationsMenu.MERCHANT_ORDERS));
        widget(581,215,147,26,"Confirmer",()->{rememberShop();if(shopOffer(shopSelection)!=null&&shopOffer(shopSelection).action()==OperationsShop.Action.BUY)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new fr.taczoperations.operations.ShopBuy(menu.containerId,shopSelection,shopLots));else send(OperationsMenu.TRADE_FIRST+shopSelection);},()->shopOffer(shopSelection)!=null&&shopCanTrade(false),(g,hover)->{
            var o=shopOffer(shopSelection);boolean ok=shopCanTrade(false);
            key(g,581,215,147,26,ok,hover,ok);
            centered(g,o==null?"Indisponible":o.action().label+(o.action()==OperationsShop.Action.SELL?" x1":o.action()==OperationsShop.Action.BUY?" x"+shopLots:""),654,223,1,ok?WHITE:SOFT,true);
        });
        widget(733,215,83,26,"Tout vendre",()->{rememberShop();send(OperationsMenu.SELL_ALL_FIRST+shopSelection);},()->shopOffer(shopSelection)!=null&&shopOffer(shopSelection).action()==OperationsShop.Action.SELL&&!shopUnknown(shopOffer(shopSelection))&&shopCanTrade(true),(g,hover)->{
            if(shopOffer(shopSelection)!=null&&shopOffer(shopSelection).action()==OperationsShop.Action.SELL){
                boolean enabled=shopCanTrade(true)&&!shopUnknown(shopOffer(shopSelection));
                key(g,733,215,83,26,false,hover,enabled);centered(g,"Tout vendre",774,223,.9F,enabled?WHITE:SOFT,true);
            }
        });
        for(int delta:new int[]{-1,1}){
            int x=delta<0?733:776;
            widget(x,215,40,26,delta<0?"−":"+",()->shopLots=Math.clamp(shopLots+delta,1,64),
                ()->canAdjustLots()&&(delta<0?shopLots>1:shopLots<64),(g,hover)->{
                    if(canAdjustLots()){
                        boolean enabled=delta<0?shopLots>1:shopLots<64;
                        key(g,x,215,40,26,false,hover,enabled);
                        centered(g,delta<0?"−":"+",x+20,223,1.15F,enabled?WHITE:SOFT,true);
                    }
                });
        }
    }
    private boolean shopUnknown(OperationsShop.Offer o){return o!=null&&o.action()!=OperationsShop.Action.REPAIR&&DiscoveryClient.unknown(o.stack().get());}
    private boolean canAdjustLots(){var o=shopOffer(shopSelection);return o!=null&&o.action()==OperationsShop.Action.BUY&&!DiscoveryClient.unknown(o.stack().get());}
    private void drawShop(GuiGraphics g){
        var v=menu.view();var merchant=vendor();
        pageTitle(g,"▱",upper(merchant.title),v.shopOwned()?"Revente · objets en inventaire":fit(merchant.specialty,275));
        for(int i=0;i<OperationsMenu.SHOP_ROWS;i++){
            int x=SHOP_X+i%OperationsMenu.SHOP_COLUMNS*SHOP_DX,y=SHOP_Y+i/OperationsMenu.SHOP_COLUMNS*SHOP_DY;
            g.fill(x,y,x+SHOP_CELL,y+SHOP_CELL,0xff1b201b);g.renderOutline(x,y,SHOP_CELL,SHOP_CELL,0xff343d32);
        }
        if(v.shopRows().isEmpty())text(g,"Aucune offre pour ce filtre.",180,308,.9F,SOFT,false);
        int maxScroll=v.shopPages()-1,scroll=v.page()%OperationsMenu.VENDOR_PAGES;
        int track=143,thumb=Math.max(10,track*OperationsMenu.SHOP_VISIBLE_ROWS/(maxScroll+OperationsMenu.SHOP_VISIBLE_ROWS));
        int thumbY=SHOP_Y+(maxScroll==0?0:(track-thumb)*scroll/maxScroll);
        g.fill(541,SHOP_Y,547,SHOP_Y+track,0xff252a24);
        g.fill(541,thumbY,547,thumbY+thumb,0xff89976a);
        centered(g,maxScroll==0?"Catalogue complet":"Défiler avec la molette",357,323,.9F,SOFT,false);
        int rep=v.reputation().get(merchant.ordinal()),loyalty=OperationsShop.reputationLevel(rep,v.experience());
        ResourceLocation portrait=texture("merchant_"+merchant.name().toLowerCase(java.util.Locale.ROOT));
        image(g,portrait,581,84,52,52);
        g.renderOutline(580,83,54,54,FRAME);
        text(g,"FIDÉLITÉ DU MARCHAND",642,88,.86F,GOLD,true);
        for(int i=1;i<=4;i++){
            int x=642+(i-1)*44;
            key(g,x,104,41,20,i==loyalty,false,i<=loyalty);
            centered(g,new String[]{"I","II","III","IV"}[i-1],x+20,109,.9F, i<=loyalty?WHITE:SOFT,true);
        }
        text(g,loyalty==4?"Fidélité maximale":rep+" / "+OperationsShop.nextReputation(rep,v.experience())+" rép. · Niv. "+OperationsShop.requiredPlayerLevel(loyalty+1)+" requis",642,129,.68F,SOFT,false);
        panel(g,578,143,242,68,0xff20251f);
        var o=shopOffer(shopSelection);
        if(o!=null){
            var stack=o.stack().get();
            if(o.action()==OperationsShop.Action.REPAIR){
                var quote=merchant.repairQuote(minecraft.player,o);
                item(g,quote.target().isEmpty()?stack:quote.target(),585,151,3F);
                text(g,fit(quote.target().isEmpty()?o.title():quote.target().getHoverName().getString(),174),640,149,.91F,WHITE,true);
                text(g,quote.target().isEmpty()?"Aucun équipement compatible":"État : "+quote.condition()+" %",640,163,.83F,SOFT,false);
                text(g,quote.target().isEmpty()?"Place l'objet dans ton inventaire":quote.cost()==0?"Déjà en parfait état":numberLong(quote.cost())+" crédits · réparation complète",640,177,.9F,GOLD,true);
                text(g,quote.cost()>v.credits()?"Crédits insuffisants":"Remise à neuf · 100 %",640,191,.82F,SOFT,false);
            }else{
            discoveryItem(g,stack,585,151,3F);
            text(g,fit(discoveredName(stack,o.title()),174),640,149,.91F,WHITE,true);
            text(g,fit(o.requirement(),174),640,163,.83F,SOFT,false);
            String cost=o.action()==OperationsShop.Action.SELL?numberLong(shopPrice(o))+" cr/u · x"+v.saleCounts().get(shopSelection):o.action()==OperationsShop.Action.BUY?numberLong((long)shopPrice(o)*shopLots)+" cr. · x"+(stack.getCount()*shopLots):o.action()==OperationsShop.Action.UNLOCK?"Remettre un objet":merchant==OperationsShop.ARMORER?"8 cr./% · min. 50":numberLong(o.price())+" cr.";
            text(g,fit(cost,174),640,177,.9F,GOLD,true);
            text(g,shopLocked(o)?"Fidélité ou accès requis":o.action()==OperationsShop.Action.SELL?"Revente au tarif du jour":"Fidélité "+o.loyaltyLevel()+(o.action()==OperationsShop.Action.BUY&&v.commerceDiscount()>0?" · -"+v.commerceDiscount()+" %":""),640,191,.82F,shopLocked(o)?0xffe5a9a0:SOFT,false);
            }
        }else text(g,"Sélectionne un objet",589,169,1,SOFT,false);
        text(g,"INVENTAIRE DU JOUEUR",581,249,1.02F,GOLD,true);
        if(minecraft.player!=null){
            var inv=minecraft.player.getInventory();
            for(int k=0;k<36;k++){
                int i=k<27?k+9:k-27;
                int x=581+(k%9)*26,y=265+(k/9)*26;
                g.fill(x,y,x+24,y+24,0xff252a24);g.renderOutline(x,y,24,24,0xff485044);
                var stack=inv.getItem(i);
                if(!stack.isEmpty()){
                    discoveryItem(g,stack,x+3,y+3,1.12F);
                    if(stack.getCount()>1)text(g,""+stack.getCount(),x+12,y+15,.78F,WHITE,true);
                }
            }
        }
        text(g,"Revente possible : "+numberLong(v.shopValue())+" cr.",581,378,.92F,LIME,true);
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
        if(id.getPath().contains("/hd/")){TacticalSkin.picture(g,id,x,y,w,h);return;}
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
        if(w>=14&&h>=14)TacticalSkin.panel(g,x,y,w,h);
        else{g.fill(x,y,x+w,y+h,color);g.renderOutline(x,y,w,h,FRAME);}
    }

    private static void key(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover, boolean enabled) {
        TacticalSkin.tile(g,x,y,w,h,selected&&enabled,hover&&enabled);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hover) {
        int border = selected ? TacticalSkin.ORANGE : hover ? GOLD : FRAME;
        if (selected || hover) {
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, selected ? 0x9fc27431 : 0x707e6b3a);
        }
        g.renderOutline(x, y, w, h, border);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, selected ? 0x8affbd75 : 0x527c7257);
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
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(questMode&&campaign.drag((my-offsetY)/scale))return true;return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(questMode&&campaign.release())return true;return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(questMode&&button==0&&campaign.click((mouseX-offsetX)/scale,(mouseY-offsetY)/scale))return true;
        if(questMode&&button!=0)return false;
        if(button==2&&menu.view().tab()==OperationsMenu.SHOP&&scale>0&&minecraft.player!=null){
            double x=(mouseX-offsetX)/scale,y=(mouseY-offsetY)/scale;
            if(x>=581&&x<815&&y>=265&&y<369){
                int col=(int)(x-581)/26,row=(int)(y-265)/26,k=row*9+col;
                int i=k<27?k+9:k-27;
                if(i>=0&&i<36&&DiscoveryClient.unknown(minecraft.player.getInventory().getItem(i))){
                    DiscoveryClient.request(menu.containerId,3,i);return true;
                }
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
}
