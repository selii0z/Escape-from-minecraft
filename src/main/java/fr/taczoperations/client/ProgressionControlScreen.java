package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.ProgressionControlMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ProgressionControlScreen extends AbstractContainerScreen<ProgressionControlMenu>{
    private static final int W=760,H=420;
    private static final int GOLD=0xffe5ce88,WHITE=0xfff5f3e8,SOFT=0xffbfc8b8,FRAME=0xff716751,LIME=0xffb5d78e;
    private static final ResourceLocation DESK=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"textures/gui/dashboard.png");
    private static final ResourceLocation BODY=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"interface");
    private static final ResourceLocation HEADING=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"heading");
    private static final String[] SKILLS={"Endurance","Force","Médecine","Fouille","Discrétion","Survie","Maîtrise des armes","Commerce"};
    private static final String[] TRAIN={"Courir pendant les raids.","Se déplacer avec un inventaire chargé.","Utiliser des soins en raid.","Fouiller les conteneurs de butin.","Se déplacer accroupi en raid.","Réussir des extractions.","Éliminer des ennemis en raid.","Acheter et vendre chez les marchands."};
    private static final String[] BENEFITS={"Réduit la consommation liée à l'effort.","Réduit la fatigue liée à la charge.","Améliore les soins du Salewa.","Accélère la fouille.","Améliore la discrétion.","Améliore la gestion des ressources.","Réduit l'usure des armes.","Réduit les prix d'achat."};
    private int selected=0;
    private boolean initialSelection;
    private float scale;
    public ProgressionControlScreen(ProgressionControlMenu menu,Inventory inv,Component title){super(menu,inv,title);}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(OperationsMod.PROGRESSION_MENU.get(),ProgressionControlScreen::new);}
    @Override protected void init(){scale=Math.min(width/(float)W,height/(float)H)*.98F;imageWidth=Math.round(W*scale);imageHeight=Math.round(H*scale);super.init();}
    private ItemStack stack(int slot){return menu.slots.get(slot).getItem();}
    private int skillSlot(int i){return 10+(i/4)*18+(i%4)*2;}
    private int missionSlot(int i){return 10+(i/7)*9+i%7;}
    private Component styled(String s,boolean bold){return Component.literal(s).withStyle(Style.EMPTY.withFont(bold?HEADING:BODY));}
    private void text(GuiGraphics g,String s,int x,int y,float factor,int color,boolean bold){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(factor,factor,1);g.drawString(font,styled(s,bold),0,0,color,false);g.pose().popPose();
    }
    private String fit(String s,int max){while(!s.isEmpty()&&font.width(styled(s,false))>max)s=s.substring(0,s.length()-1);return s;}
    private String name(int slot){return stack(slot).isEmpty()?"Chargement…":stack(slot).getHoverName().getString();}
    private java.util.List<Component> lore(int slot){var l=stack(slot).get(DataComponents.LORE);return l==null?java.util.List.of():l.lines();}
    private String line(int slot,int i){var l=lore(slot);return i<l.size()?l.get(i).getString():"";}
    private void panel(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);g.renderOutline(x,y,w,h,FRAME);}
    private boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void button(GuiGraphics g,String s,int x,int y,int w,int h,double mx,double my,boolean enabled){
        boolean hover=enabled&&hit(mx,my,x,y,w,h);
        g.fillGradient(x,y,x+w,y+h,!enabled?0xff272c26:hover?0xff53633a:0xff394431,0xff171c16);
        g.renderOutline(x,y,w,h,hover?GOLD:FRAME);
        text(g,s,x+(w-font.width(styled(s,true)))/2,y+(h-8)/2,1,enabled?WHITE:0xff8b9487,true);
    }
    private void tile(GuiGraphics g,int x,int y,int w,int h,boolean active,boolean hover){
        g.fillGradient(x,y,x+w,y+h,active?0xff454f30:hover?0xff303b2c:0xff252d25,0xff141a15);
        g.renderOutline(x,y,w,h,active?GOLD:hover?0xff9aa583:FRAME);
    }
    private void icon(GuiGraphics g,ItemStack s,int x,int y,float scale){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(s,0,0);g.pose().popPose();
    }
    private float progress(String s){
        if(s.contains("maximum"))return 1;
        String[] parts=s.replace(" XP","").split("/");
        if(parts.length!=2)return 0;
        try{return Math.clamp(Float.parseFloat(parts[0].trim())/Math.max(1,Float.parseFloat(parts[1].trim())),0f,1f);}catch(NumberFormatException ex){return 0;}
    }
    private void bar(GuiGraphics g,int x,int y,int w,int h,float f){g.fill(x,y,x+w,y+h,0xff414b3c);if(f>0)g.fill(x,y,x+Math.round(w*f),y+h,LIME);}
    private boolean acceptable(){return !stack(40).isEmpty()&&name(40).startsWith("Accepter : ")&&name(missionSlot(selected)).equals(name(40).substring("Accepter : ".length()));}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
        if(menu.campaign()&&!initialSelection&&!stack(missionSlot(0)).isEmpty()){
            for(int i=0;i<12;i++){String status=line(missionSlot(i),0);if(status.startsWith("Disponible")||status.startsWith("Acceptée")||status.startsWith("Niveau insuffisant")){selected=i;break;}}
            initialSelection=true;
        }
        g.pose().pushPose();g.pose().translate(leftPos,topPos,0);g.pose().scale(scale,scale,1);
        minecraft.getTextureManager().bindForSetup(DESK);minecraft.getTextureManager().getTexture(DESK).setFilter(true,false);
        g.blit(DESK,0,0,0,0,W,H,W,H);
        panel(g,10,10,740,50,0xf0111612);
        text(g,menu.campaign()?"CAMPAGNE MILITAIRE":"COMPÉTENCES PASSIVES",23,17,2,GOLD,true);
        text(g,menu.campaign()?"12 opérations · progression scénarisée":"8 compétences · 50 niveaux chacune",24,43,1,SOFT,false);
        panel(g,10,68,412,292,0xe5111612);panel(g,430,68,320,292,0xe9111612);
        if(menu.campaign())drawCampaign(g,x,y);else drawSkills(g,x,y);
        panel(g,10,368,740,43,0xf0111612);
        button(g,"Retour",20,377,178,25,x,y,true);
        text(g,menu.campaign()?"Sélectionne une opération pour consulter ses objectifs.":"Les compétences progressent automatiquement selon tes actions.",215,385,.95F,SOFT,false);
        g.pose().popPose();
    }
    private void drawSkills(GuiGraphics g,double mx,double my){
        text(g,"TES COMPÉTENCES",23,78,1.35F,GOLD,true);
        for(int i=0;i<8;i++){
            int x=20+(i%2)*196,y=103+(i/2)*62,slot=skillSlot(i);
            tile(g,x,y,190,57,selected==i,hit(mx,my,x,y,190,57));icon(g,stack(slot),x+7,y+12,1.6F);
            String level=name(slot);int split=level.lastIndexOf(" · ");
            text(g,SKILLS[i],x+40,y+6,1.05F,WHITE,true);
            text(g,split<0?"Chargement…":"Niv. "+level.substring(split+3),x+40,y+21,1,GOLD,false);
            bar(g,x+40,y+37,141,5,progress(line(slot,0)));
            text(g,line(slot,0),x+40,y+44,.8F,SOFT,false);
        }
        int slot=skillSlot(selected);icon(g,stack(slot),444,81,2);
        text(g,SKILLS[selected].toUpperCase(java.util.Locale.ROOT),484,85,1.35F,GOLD,true);
        text(g,fit(name(slot),286),444,122,1.1F,WHITE,true);
        bar(g,444,142,292,8,progress(line(slot,0)));
        text(g,line(slot,0),444,157,1,SOFT,false);
        text(g,line(slot,1),444,179,1.15F,LIME,true);text(g,line(slot,2),444,197,1,SOFT,false);
        g.fill(444,218,736,219,FRAME);
        text(g,"COMMENT PROGRESSER",444,229,1.1F,GOLD,true);
        text(g,fit(TRAIN[selected],290),444,248,1,WHITE,false);
        text(g,"EFFET",444,278,1.1F,GOLD,true);
        text(g,fit(BENEFITS[selected],290),444,297,1,WHITE,false);
        text(g,"Progression automatique · aucun achat de niveau",444,337,.85F,SOFT,false);
    }
    private void drawCampaign(GuiGraphics g,double mx,double my){
        int done=0;for(int i=0;i<12;i++)if(line(missionSlot(i),0).startsWith("Terminée"))done++;
        text(g,"OPÉRATIONS · "+done+"/12",23,78,1.35F,GOLD,true);
        bar(g,23,95,385,4,done/12f);
        for(int i=0;i<12;i++){
            int x=20+(i%2)*196,y=107+(i/2)*40,slot=missionSlot(i);
            String status=line(slot,0);
            int color=status.startsWith("Terminée")?LIME:status.startsWith("Verrouillée")||status.startsWith("Niveau insuffisant")?SOFT:GOLD;
            tile(g,x,y,190,37,selected==i,hit(mx,my,x,y,190,37));
            text(g,String.format(java.util.Locale.ROOT,"%02d",i+1),x+6,y+6,1.2F,color,true);
            text(g,fit(name(slot),152),x+32,y+5,1,WHITE,true);
            text(g,fit(status,152),x+32,y+20,.92F,color,false);
        }
        int slot=missionSlot(selected);
        text(g,fit(name(slot).toUpperCase(java.util.Locale.ROOT),210),444,80,1.35F,GOLD,true);
        text(g,line(slot,0),444,104,1.05F,WHITE,true);
        text(g,fit(line(slot,1),290),444,122,1,SOFT,false);
        g.fill(444,140,736,141,FRAME);
        text(g,"OBJECTIFS",444,151,1.1F,GOLD,true);
        for(int j=2;j<=5;j++)text(g,fit(line(slot,j),290),444,174+(j-2)*19,1,WHITE,false);
        text(g,"RÉCOMPENSES",444,255,1.1F,GOLD,true);
        text(g,line(slot,6),444,275,1.1F,LIME,true);
        String status=line(slot,0);
        String caption=acceptable()?"Accepter l'opération":status.startsWith("Terminée")?"Opération terminée":status.startsWith("Acceptée")?"Opération en cours":status.startsWith("Niveau insuffisant")?"Niveau militaire insuffisant":"Opération verrouillée";
        button(g,caption,444,310,292,27,mx,my,acceptable());
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&scale>0){
            double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
            int n=menu.campaign()?12:8;
            for(int i=0;i<n;i++){
                int row=menu.campaign()?107+(i/2)*40:103+(i/2)*62;
                if(hit(x,y,20+(i%2)*196,row,190,menu.campaign()?37:57)){selected=i;initialSelection=true;return true;}
            }
            if(hit(x,y,20,377,178,25)){send(49);return true;}
            if(menu.campaign()&&hit(x,y,444,310,292,27)){if(acceptable())send(40);return true;}
        }
        return super.mouseClicked(mx,my,button);
    }
}
