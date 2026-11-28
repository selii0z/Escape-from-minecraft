package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.HideoutControlMenu;
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
public final class HideoutControlScreen extends AbstractContainerScreen<HideoutControlMenu>{
    private static final int W=760,H=420;
    private static final int GOLD=0xffe5e6e8,WHITE=0xfff5f3e8,SOFT=0xffc2c6ca,FRAME=0xff65696d;
    private static final ResourceLocation DESK=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"textures/gui/dashboard.png");
    private static final ResourceLocation BODY=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"interface");
    private static final ResourceLocation HEADING=ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"heading");
    private static final String[] NAMES={"Réserve","Infirmerie","Atelier","Générateur","Cuisine","Collecteur d'eau","Salle de repos","Stand de tir"};
    private static final String[] USE={"Stocker ton équipement","Fabriquer des soins","Réparer les armes","Alimenter les installations","Préparer des rations","Produire de l'eau","Préparer le prochain raid","Entraîner ta précision"};
    private static final int[][] ACTIONS={{28},{29,30},{34},{33},{31},{32},{35},{37}};
    private static final String[][] LABELS={{"Ouvrir la réserve"},{"Fabriquer 2 bandages","Fabriquer 1 Salewa"},{"Réparer l'arme en main"},{"Ajouter du carburant"},{"Fabriquer 3 rations"},{"Produire 4 bouteilles"},{"Préparer le repos"},{"Entrer au stand de tir"}};
    private static int selected;
    private float scale;
    public HideoutControlScreen(HideoutControlMenu menu,Inventory inv,Component title){super(menu,inv,title);}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(OperationsMod.HIDEOUT_MENU.get(),HideoutControlScreen::new);}
    @Override protected void init(){
        scale=Math.min(width/(float)W,height/(float)H)*0.98F;
        imageWidth=Math.round(W*scale);imageHeight=Math.round(H*scale);super.init();
    }
    private ItemStack stack(int slot){return menu.slots.get(slot).getItem();}
    private Component styled(String s,boolean bold){return Component.literal(s).withStyle(Style.EMPTY.withFont(bold?HEADING:BODY));}
    private void label(GuiGraphics g,String s,int x,int y,float factor,int color,boolean bold){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(factor,factor,1);
        g.drawString(font,styled(s,bold),0,0,color,false);g.pose().popPose();
    }
    private void centered(GuiGraphics g,String s,int x,int y,int color){
        label(g,s,x-font.width(styled(s,true))/2,y,1,color,true);
    }
    private String fit(String s,int max){while(!s.isEmpty()&&font.width(styled(s,false))>max)s=s.substring(0,s.length()-1);return s;}
    private void panel(GuiGraphics g,int x,int y,int w,int h,int color){g.fill(x,y,x+w,y+h,color);g.renderOutline(x,y,w,h,FRAME);}
    private void icon(GuiGraphics g,ItemStack item,int x,int y,float factor){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(factor,factor,1);g.renderItem(item,0,0);g.pose().popPose();
    }
    private boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void button(GuiGraphics g,String s,int x,int y,int w,int h,double mx,double my,boolean enabled){
        boolean hover=enabled&&inside(mx,my,x,y,w,h);
        g.fillGradient(x,y,x+w,y+h,!enabled?0xff242628:hover?0xff512326:0xff312022,0xff161719);
        g.renderOutline(x,y,w,h,hover?GOLD:FRAME);centered(g,s,x+w/2,y+(h-8)/2,enabled?WHITE:0xff8b9487);
    }
    private String name(int slot){return stack(slot).isEmpty()?"Chargement…":stack(slot).getHoverName().getString();}
    private String level(int i){String name=name(10+i);int p=name.lastIndexOf(" · ");return p<0?"…":name.substring(p+3);}
    private boolean maxed(){return level(selected).equals("3/3");}
    private void lines(GuiGraphics g,int slot,int x,int y,int max){
        var lore=stack(slot).get(DataComponents.LORE);
        if(lore==null)return;
        for(var line:lore.lines()){
            int color=line.getStyle().getColor()==null?SOFT:0xff000000|line.getStyle().getColor().getValue();
            label(g,fit(line.getString(),max),x,y,1,color,false);y+=11;
        }
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        double mx=(mouseX-leftPos)/scale,my=(mouseY-topPos)/scale;
        g.pose().pushPose();g.pose().translate(leftPos,topPos,0);g.pose().scale(scale,scale,1);
        minecraft.getTextureManager().bindForSetup(DESK);minecraft.getTextureManager().getTexture(DESK).setFilter(true,false);
        g.blit(DESK,0,0,0,0,W,H,W,H);
        panel(g,10,10,740,50,0xf0111214);
        label(g,"MA PLANQUE",23,17,2.0F,GOLD,true);
        label(g,"Base personnelle · gestion des installations",24,43,1,SOFT,false);
        label(g,fit(name(46),220),516,20,1,WHITE,true);
        label(g,fit(name(45),220),516,37,1,GOLD,false);
        panel(g,10,68,412,292,0xe5111214);
        label(g,"INSTALLATIONS",23,77,1.35F,GOLD,true);
        label(g,"Sélectionne une installation pour voir ses services.",23,95,1,SOFT,false);
        for(int i=0;i<8;i++){
            int x=20+(i%2)*196,y=113+(i/2)*59;
            boolean hover=inside(mx,my,x,y,190,54),active=selected==i;
            g.fillGradient(x,y,x+190,y+54,active?0xff3c2226:hover?0xff2c2e31:0xff25272a,0xff141517);
            g.renderOutline(x,y,190,54,active?GOLD:hover?0xff9aa583:FRAME);
            icon(g,stack(10+i),x+7,y+12,1.6F);
            label(g,NAMES[i],x+40,y+7,1.15F,WHITE,true);
            label(g,fit(USE[i],146),x+40,y+22,.9F,SOFT,false);
            label(g,"Niveau "+level(i),x+40,y+37,1,level(i).equals("0/3")?0xffa9afa1:0xffb6d58c,true);
        }
        panel(g,430,68,320,292,0xe9111214);
        label(g,NAMES[selected].toUpperCase(java.util.Locale.ROOT),444,78,1.65F,GOLD,true);
        label(g,USE[selected],444,100,1,SOFT,false);
        label(g,"Niveau actuel : "+level(selected),444,115,1.05F,WHITE,true);
        g.fill(443,132,737,133,FRAME);
        label(g,maxed()?"NIVEAU MAXIMUM":"PROCHAINE AMÉLIORATION",444,140,1.1F,GOLD,true);
        if(!maxed())lines(g,19+selected,444,155,290);
        button(g,maxed()?"Niveau maximum":"Améliorer l'installation",444,214,292,24,mx,my,!maxed()&&!stack(19+selected).isEmpty());
        label(g,"SERVICES",444,247,1.1F,GOLD,true);
        for(int i=0;i<ACTIONS[selected].length;i++){
            int id=ACTIONS[selected][i];
            button(g,LABELS[selected][i],444,264+i*29,292,25,mx,my,!stack(id).isEmpty());
        }
        label(g,"Survole un service pour connaître son coût.",444,330,.9F,SOFT,false);
        panel(g,10,368,740,43,0xf0111214);
        button(g,"Campagne",20,377,153,25,mx,my,true);
        button(g,"Compétences",180,377,153,25,mx,my,true);
        button(g,"Décoration",340,377,153,25,mx,my,true);
        button(g,"Quitter la planque",500,377,240,25,mx,my,true);
        g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&scale>0){
            double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
            for(int i=0;i<8;i++)if(inside(x,y,20+(i%2)*196,113+(i/2)*59,190,54)){selected=i;return true;}
            if(inside(x,y,444,214,292,24)){if(!maxed()&&!stack(19+selected).isEmpty())send(19+selected);return true;}
            for(int i=0;i<ACTIONS[selected].length;i++)if(inside(x,y,444,264+i*29,292,25)){send(ACTIONS[selected][i]);return true;}
            int[] ids={39,41,43,49};int[] xs={20,180,340,500};
            for(int i=0;i<ids.length;i++)if(inside(x,y,xs[i],377,i==3?240:153,25)){send(ids[i]);return true;}
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);
        if(scale<=0)return;
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
        for(int i=0;i<ACTIONS[selected].length;i++)if(inside(x,y,444,264+i*29,292,25)){
            var item=stack(ACTIONS[selected][i]);if(!item.isEmpty())g.renderTooltip(font,item,mx,my);return;
        }
        if(inside(x,y,340,377,153,25)&&!stack(43).isEmpty())g.renderTooltip(font,stack(43),mx,my);
    }
}
