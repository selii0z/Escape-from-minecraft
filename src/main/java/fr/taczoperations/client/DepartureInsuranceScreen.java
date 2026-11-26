package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.DepartureInsuranceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class DepartureInsuranceScreen extends AbstractContainerScreen<DepartureInsuranceMenu>{
    private static final int W=DepartureInsuranceMenu.WIDTH,H=DepartureInsuranceMenu.HEIGHT;
    private static final int GOLD=0xffe5ce88,WHITE=0xfff5f3e8,SOFT=0xffbfc8b8,FRAME=0xff716751,LIME=0xffb5d78e;
    private static final ResourceLocation DESK=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"textures/gui/dashboard.png");
    private static final ResourceLocation BODY=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"interface");
    private static final ResourceLocation HEADING=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"heading");
    private float scale;
    public DepartureInsuranceScreen(DepartureInsuranceMenu menu,Inventory inv,Component title){super(menu,inv,title);}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(OperationsMod.DEPARTURE_INSURANCE.get(),DepartureInsuranceScreen::new);}
    @Override protected void init(){scale=Math.min(width/(float)W,height/(float)H)*.98F;imageWidth=Math.round(W*scale);imageHeight=Math.round(H*scale);super.init();}
    private Component styled(String s,boolean bold){return Component.literal(s).withStyle(Style.EMPTY.withFont(bold?HEADING:BODY));}
    private void text(GuiGraphics g,String s,int x,int y,float factor,int color,boolean bold){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(factor,factor,1);g.drawString(font,styled(s,bold),0,0,color,false);g.pose().popPose();
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);g.renderOutline(x,y,w,h,FRAME);}
    private boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void button(GuiGraphics g,String s,int x,int y,int w,int h,double mx,double my,boolean enabled,boolean primary){
        boolean hover=enabled&&hit(mx,my,x,y,w,h);
        g.fillGradient(x,y,x+w,y+h,!enabled?0xff272c26:hover?0xff66784a:primary?0xff4d6138:0xff303a2d,0xff171c16);
        g.renderOutline(x,y,w,h,hover?GOLD:FRAME);
        text(g,s,x+(w-font.width(styled(s,true)))/2,y+(h-8)/2,1,enabled?WHITE:0xff8b9487,true);
    }
    private void icon(GuiGraphics g,ItemStack s,int x,int y){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(2,2,1);g.renderItem(s,0,0);g.pose().popPose();
    }
    private String map(){return menu.express()!=null?menu.express().title:menu.industrial()?"Zone industrielle":"Forêt Noire";}
    private String time(){return menu.night()?"22 h 00 · Nuit":"09 h 30 · Jour";}
    private String duration(){return menu.express()!=null?"15 minutes":menu.industrial()?"30 minutes":"45 minutes";}
    private int cost(){return menu.selected()*DepartureInsuranceMenu.FEE;}
    private boolean affordable(){return cost()<=menu.balance();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
        g.pose().pushPose();g.pose().translate(leftPos,topPos,0);g.pose().scale(scale,scale,1);
        minecraft.getTextureManager().bindForSetup(DESK);minecraft.getTextureManager().getTexture(DESK).setFilter(true,false);
        g.blit(DESK,0,0,0,0,W,H,W,H);
        panel(g,10,10,740,52,0xf0111612);
        text(g,menu.reviewing()?"CONFIRMATION DU DÉPART":"ASSURANCE AVANT RAID",23,17,2,GOLD,true);
        text(g,menu.reviewing()?"ÉTAPE 2 / 2 · Vérifie ton opération avant de partir":"ÉTAPE 1 / 2 · Sélectionne les équipements à protéger",24,44,1,SOFT,false);
        if(menu.reviewing())review(g,x,y);else insurance(g,x,y);
        g.pose().popPose();
    }
    private void insurance(GuiGraphics g,double mx,double my){
        panel(g,10,70,480,290,0xe5111612);panel(g,500,70,250,290,0xe9111612);
        text(g,"ÉQUIPEMENT ASSURABLE",24,82,1.35F,GOLD,true);
        button(g,"Tout sélectionner / retirer",24,105,232,24,mx,my,!menu.slots.isEmpty(),false);
        for(int i=0;i<45;i++){
            int x=24+(i%9)*50,y=139+(i/9)*41;
            int state=i<menu.slots.size()?menu.state(i):0;
            boolean hover=i<menu.slots.size()&&hit(mx,my,x,y,42,39);
            int fill=state==2?0xff48613a:state==3?0xff304d4a:state==0?0x66141914:0xff222c22;
            int border=hover?GOLD:state==2?LIME:state==3?0xff69b6ae:0xff505a4b;
            g.fill(x,y,x+42,y+39,fill);g.renderOutline(x,y,42,39,border);
            if(i<menu.slots.size())icon(g,menu.slots.get(i).getItem(),x+5,y+3);
        }
        if(menu.slots.isEmpty()){
            g.fill(34,200,458,248,0xf0111612);
            text(g,"Aucun équipement assurable",53,211,1.3F,WHITE,true);
            text(g,"Tu peux continuer sans nouvelle assurance.",53,231,1,SOFT,false);
        }
        text(g,"ASSURANCE",514,82,1.4F,GOLD,true);
        text(g,"100 crédits par équipement",514,109,1,SOFT,false);
        g.fill(514,126,735,127,FRAME);
        text(g,"Déjà assurés : "+menu.insured(),514,140,1,0xff83bdb8,true);
        text(g,"À assurer : "+menu.selected(),514,162,1,LIME,true);
        text(g,"Coût : "+cost()+" crédits",514,194,1.1F,affordable()?GOLD:0xffe98b80,true);
        text(g,"Solde : "+menu.balance()+" crédits",514,219,1,WHITE,false);
        text(g,"Après paiement : "+(menu.balance()-cost())+" cr.",514,241,1,SOFT,false);
        text(g,"Vert : sélectionné",514,281,1,LIME,false);
        text(g,"Bleu : déjà assuré",514,299,1,0xff83bdb8,false);
        text(g,"Paiement seulement au départ.",514,332,.9F,SOFT,false);
        panel(g,10,368,740,43,0xf0111612);
        button(g,"Annuler",20,377,150,25,mx,my,true,false);
        button(g,"Sans nouvelle assurance",180,377,260,25,mx,my,true,false);
        button(g,"Continuer vers le récapitulatif",450,377,290,25,mx,my,affordable(),true);
    }
    private void detail(GuiGraphics g,String label,String value,int x,int y){
        text(g,label,x,y,.95F,SOFT,false);text(g,value,x,y+16,1.35F,WHITE,true);
    }
    private void review(GuiGraphics g,double mx,double my){
        panel(g,10,70,410,290,0xe5111612);panel(g,430,70,320,290,0xe9111612);
        text(g,"OPÉRATION",24,83,1.5F,GOLD,true);
        detail(g,"CARTE",map(),24,117);
        detail(g,"HEURE DE DÉPART (JEU)",time(),24,165);
        detail(g,"DURÉE MAXIMALE",duration(),24,213);
        detail(g,"MÉTÉO","Variable pendant le raid",24,261);
        text(g,"OBJECTIF : survivre et rejoindre une extraction.",24,333,1,SOFT,false);
        text(g,"ÉQUIPE ET ASSURANCE",444,83,1.4F,GOLD,true);
        detail(g,"MODE",menu.duo()?"Duo":"Solo",444,117);
        detail(g,"ÉQUIPIER",menu.duo()?menu.partnerName():"Aucun",444,165);
        text(g,"Déjà assurés : "+menu.insured(),444,216,1,0xff83bdb8,false);
        text(g,"Nouveaux équipements : "+menu.selected(),444,237,1,LIME,false);
        text(g,"Coût au départ : "+cost()+" crédits",444,261,1.1F,GOLD,true);
        text(g,"Solde après paiement : "+(menu.balance()-cost())+" cr.",444,284,1,SOFT,false);
        text(g,menu.waiting()?"En attente de la validation de ton équipier.":menu.duo()?"Les deux joueurs doivent confirmer.":"Prêt à lancer le raid ?",444,332,1,menu.waiting()?GOLD:SOFT,true);
        panel(g,10,368,740,43,0xf0111612);
        button(g,"Annuler le départ",20,377,208,25,mx,my,true,false);
        button(g,"Modifier l'assurance",238,377,216,25,mx,my,!menu.waiting(),false);
        button(g,menu.waiting()?"En attente de l'équipier":"LANCER LE RAID",464,377,276,25,mx,my,!menu.waiting()&&affordable(),true);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&scale>0){
            double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
            if(menu.reviewing()){
                if(hit(x,y,20,377,208,25)){send(DepartureInsuranceMenu.BACK);return true;}
                if(hit(x,y,238,377,216,25)){if(!menu.waiting())send(DepartureInsuranceMenu.EDIT);return true;}
                if(hit(x,y,464,377,276,25)){if(!menu.waiting()&&affordable())send(DepartureInsuranceMenu.LAUNCH);return true;}
            }else{
                for(int i=0;i<menu.slots.size();i++)if(hit(x,y,24+(i%9)*50,139+(i/9)*41,42,39)){send(i);return true;}
                if(hit(x,y,24,105,232,24)){send(DepartureInsuranceMenu.SELECT_ALL);return true;}
                if(hit(x,y,20,377,150,25)){send(DepartureInsuranceMenu.BACK);return true;}
                if(hit(x,y,180,377,260,25)){send(DepartureInsuranceMenu.SKIP);return true;}
                if(hit(x,y,450,377,290,25)){if(affordable())send(DepartureInsuranceMenu.CONFIRM);return true;}
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);
        if(scale<=0||menu.reviewing())return;
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
        for(int i=0;i<menu.slots.size();i++)if(hit(x,y,24+(i%9)*50,139+(i/9)*41,42,39)){
            var item=menu.slots.get(i).getItem();if(!item.isEmpty())g.renderTooltip(font,item,mx,my);return;
        }
    }
}
