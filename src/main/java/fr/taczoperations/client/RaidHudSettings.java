package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Per-panel local preferences and a draggable preview; no server protocol change. */
@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidHudSettings {
    private static final KeyMapping SHOW=new KeyMapping("Afficher les HUD EFM",GLFW.GLFW_KEY_F8,"key.categories.taczoperations");
    private static final KeyMapping SETTINGS=new KeyMapping("Réglages des HUD EFM",GLFW.GLFW_KEY_F9,"key.categories.taczoperations");
    private static final int[] SIZES={40,50,65,80,100,120,150};
    private static final int[] OPACITIES={0,25,50,75,100};
    private static final int[] TIMES={5,10,20,0,-1};
    enum Panel {
        HEALTH("Santé",142,235,0), TIMER("Chronomètre",150,64,500), EXITS("Sorties",146,92,1000);
        final String label;final int w,h,defaultX;
        int size,opacity,duration,x,y;
        Panel(String label,int w,int h,int defaultX){this.label=label;this.w=w;this.h=h;this.defaultX=defaultX;reset();}
        void reset(){size=2;opacity=3;duration=1;x=defaultX;y=0;}
    }
    private static long shownAt;
    private static Object previousLevel;
    private static final Path FILE=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("efm-client-hud.properties");
    static { load(); }
    private static int read(Properties p,String key,int fallback,int min,int max){
        try{return Math.clamp(Integer.parseInt(p.getProperty(key,Integer.toString(fallback))),min,max);}catch(NumberFormatException ex){return fallback;}
    }
    private static void load(){
        if(!Files.isRegularFile(FILE))return;
        Properties p=new Properties();
        try(var in=Files.newInputStream(FILE)){
            p.load(in);
            boolean oldRight=Boolean.parseBoolean(p.getProperty("right","false"));
            for(Panel panel:Panel.values()){
                String k=panel.name().toLowerCase(java.util.Locale.ROOT)+".";
                panel.size=read(p,k+"size",read(p,"size",1,0,4)+1,0,SIZES.length-1);
                panel.opacity=read(p,k+"opacity",read(p,"opacity",2,0,3)+1,0,OPACITIES.length-1);
                panel.duration=read(p,k+"duration",read(p,"duration",1,0,3),0,TIMES.length-1);
                int oldX=oldRight?(panel==Panel.HEALTH?1000:panel==Panel.EXITS?0:500):panel.defaultX;
                panel.x=read(p,k+"x",oldX,0,1000);panel.y=read(p,k+"y",0,0,1000);
            }
        }catch(Exception ex){for(Panel panel:Panel.values())panel.reset();}
    }
    private static void save(){
        Properties p=new Properties();
        for(Panel panel:Panel.values()){
            String k=panel.name().toLowerCase(java.util.Locale.ROOT)+".";
            p.setProperty(k+"size",Integer.toString(panel.size));p.setProperty(k+"opacity",Integer.toString(panel.opacity));
            p.setProperty(k+"duration",Integer.toString(panel.duration));p.setProperty(k+"x",Integer.toString(panel.x));p.setProperty(k+"y",Integer.toString(panel.y));
        }
        try{Files.createDirectories(FILE.getParent());try(var out=Files.newOutputStream(FILE)){p.store(out,"Escape from Minecraft HUD");}}
        catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot save EFM HUD settings",ex);}
    }
    public static boolean inRaid(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&mc.level.dimension().equals(ForestExpedition.FOREST);}
    static boolean visible(Panel p){int seconds=TIMES[p.duration];return inRaid()&&(seconds==0||(seconds>0&&shownAt!=0&&System.nanoTime()-shownAt<seconds*1_000_000_000L));}
    private static int screenW(){return Minecraft.getInstance().getWindow().getGuiScaledWidth();}
    private static int screenH(){return Minecraft.getInstance().getWindow().getGuiScaledHeight();}
    private static int safeH(){return Math.max(24,screenH()-45);}
    static float scale(Panel p){return Math.max(.05F,Math.min(SIZES[p.size]/100f,Math.min(Math.max(1,screenW()-8)/(float)p.w,Math.max(1,safeH()-8)/(float)(p==Panel.HEALTH?100:p.h))));}
    static int width(Panel p){return (int)(screenW()/scale(p));}
    static int height(Panel p){return (int)(safeH()/scale(p));}
    private static float travelX(Panel p){return Math.max(0,screenW()-8-p.w*scale(p));}
    private static float travelY(Panel p){return Math.max(0,safeH()-8-p.h*scale(p));}
    private static float pixelX(Panel p){return 4+travelX(p)*p.x/1000f;}
    private static float pixelY(Panel p){return 4+travelY(p)*p.y/1000f;}
    static int x(Panel p){return Math.round(pixelX(p)/scale(p));}
    static int y(Panel p){return Math.round(pixelY(p)/scale(p));}
    static int bg(Panel p,int argb){return (argb&0xffffff)|((argb>>>24)*OPACITIES[p.opacity]/100<<24);}
    @EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static class Keys{
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(SHOW);e.register(SETTINGS);}
    }
    @SubscribeEvent public static void hideCobblemon(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e){
        if(inRaid()&&e.getName().getNamespace().equals("cobblemon"))e.setCanceled(true);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(mc.level!=previousLevel||!inRaid()){shownAt=0;previousLevel=mc.level;}
        while(SHOW.consumeClick())if(inRaid()&&mc.screen==null)shownAt=System.nanoTime();
        while(SETTINGS.consumeClick())if(mc.player!=null&&mc.screen==null)mc.setScreen(new Screen(null));
    }
    public static final class Screen extends net.minecraft.client.gui.screens.Screen {
        private final net.minecraft.client.gui.screens.Screen parent;
        private Panel selected=Panel.HEALTH,dragging;
        private double grabX,grabY;
        private float factor,previewX,previewY;
        public Screen(net.minecraft.client.gui.screens.Screen parent){super(Component.literal("HUD de raid"));this.parent=parent;}
        @Override protected void init(){
            int usable=Math.min(450,width-12),left=(width-usable)/2,cell=(usable-8)/3;
            for(Panel panel:Panel.values()){
                addRenderableWidget(Button.builder(Component.literal((selected==panel?"• ":"")+panel.label),b->{selected=panel;rebuildWidgets();}).bounds(left+panel.ordinal()*(cell+4),17,cell,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Taille : "+SIZES[selected.size]+" %"),b->{selected.size=(selected.size+1)%SIZES.length;save();rebuildWidgets();}).bounds(left,40,cell,20).build());
            addRenderableWidget(Button.builder(Component.literal("Fond : "+OPACITIES[selected.opacity]+" %"),b->{selected.opacity=(selected.opacity+1)%OPACITIES.length;save();rebuildWidgets();}).bounds(left+cell+4,40,cell,20).build());
            addRenderableWidget(Button.builder(Component.literal(timeLabel()),b->{selected.duration=(selected.duration+1)%TIMES.length;save();rebuildWidgets();}).bounds(left+2*(cell+4),40,cell,20).build());
            addRenderableWidget(Button.builder(Component.literal("Réinitialiser ce HUD"),b->{selected.reset();save();rebuildWidgets();}).bounds(left,height-25,cell,20).build());
            addRenderableWidget(Button.builder(Component.literal("Tout réinitialiser"),b->{for(Panel panel:Panel.values())panel.reset();save();rebuildWidgets();}).bounds(left+cell+4,height-25,cell,20).build());
            addRenderableWidget(Button.builder(Component.literal("Terminé"),b->onClose()).bounds(left+2*(cell+4),height-25,cell,20).build());
            factor=Math.max(.02f,Math.min((width-12)/(float)Math.max(1,screenW()),Math.max(1,height-105)/(float)safeH()));
            previewX=(width-screenW()*factor)/2;previewY=75;
        }
        private String timeLabel(){int t=TIMES[selected.duration];return t<0?"Masqué":t==0?"Permanent":t+" secondes";}
        private int px(Panel p){return Math.round(previewX+pixelX(p)*factor);}
        private int py(Panel p){return Math.round(previewY+pixelY(p)*factor);}
        private float ps(Panel p){return scale(p)*factor;}
        private int ph(Panel p){return Math.max(1,Math.round(Math.min(p.h*scale(p),safeH()-pixelY(p))*factor));}
        private int pw(Panel p){return Math.max(1,Math.round(p.w*ps(p)));}
        private void sample(GuiGraphics g,Panel p){
            int x=px(p),y=py(p),w=pw(p),h=ph(p);
            g.fill(x,y,x+w,y+h,bg(p,0xe019241a));g.renderOutline(x,y,w,h,p==selected?0xffe5ce88:0xff879176);
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(ps(p),ps(p),1);
            g.drawString(font,p.label,5,4,0xfff5f3e8,false);
            if(p==Panel.TIMER){
                g.drawCenteredString(font,"28:42",p.w/2,15,0xffe5ce88);
                g.fill(4,26,p.w-4,28,0xff9fb877);
                g.drawCenteredString(font,"Extraction · 5 s",p.w/2,43,0xffd3dbcc);
            }else if(p==Panel.EXITS){
                g.drawString(font,"Principale · 245 m",5,24,0xffb5d78e,false);
                g.drawString(font,"Relais · 390 m",5,48,0xffe5ce88,false);
                g.drawString(font,"Payante · 520 m",5,72,0xffd3dbcc,false);
            }else{
                g.fill(17,25,30,38,0xff9eae91);g.fill(14,40,33,65,0xff9eae91);
                g.fill(5,41,12,64,0xff9eae91);g.fill(35,41,42,64,0xff9eae91);
                g.fill(14,67,22,88,0xff9eae91);g.fill(25,67,33,88,0xff9eae91);
                g.drawString(font,"Santé : 80 %",50,40,0xffd3dbcc,false);
                g.fill(50,54,132,58,0xff465141);g.fill(50,54,115,58,0xffa6bf7d);
                g.drawString(font,"Hydratation : 100 %",5,105,0xff83b7ce,false);
                g.drawString(font,"Énergie : 100 %",5,125,0xffcbb776,false);
                g.drawString(font,"Équipement",5,151,0xffd3dbcc,false);
                g.fill(5,166,132,170,0xff9fb877);
            }
            g.pose().popPose();
        }
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
            // Avoid the vanilla blur pass over the HUD preview.
            g.fill(0,0,width,height,0xb5101611);
        }
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            super.render(g,mx,my,partial);
            g.fill(Math.round(previewX),Math.round(previewY),Math.round(previewX+screenW()*factor),Math.round(previewY+safeH()*factor),0xff111711);
            g.renderOutline(Math.round(previewX),Math.round(previewY),Math.round(screenW()*factor),Math.round(safeH()*factor),0xff716751);
            g.enableScissor(Math.round(previewX),Math.round(previewY),Math.round(previewX+screenW()*factor),Math.round(previewY+safeH()*factor));
            try{for(Panel p:Panel.values())if(p!=selected)sample(g,p);sample(g,selected);}finally{g.disableScissor();}
            g.drawCenteredString(font,"HUD de raid · réglages indépendants",width/2,4,0xffe5ce88);
            g.drawCenteredString(font,"Glisse un panneau à la souris · F8 pour afficher",width/2,64,0xffc8d0bf);
        }
        private boolean hit(Panel p,double mx,double my){return mx>=px(p)&&mx<px(p)+pw(p)&&my>=py(p)&&my<py(p)+ph(p);}
        @Override public boolean mouseClicked(double mx,double my,int button){
            if(button==0&&factor>0){
                Panel target=hit(selected,mx,my)?selected:null;
                if(target==null)for(Panel p:Panel.values())if(hit(p,mx,my)){target=p;break;}
                if(target!=null){selected=target;dragging=target;grabX=mx-px(target);grabY=my-py(target);rebuildWidgets();return true;}
            }
            return super.mouseClicked(mx,my,button);
        }
        @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
            if(button==0&&dragging!=null){
                Panel p=dragging;
                float tx=travelX(p),ty=travelY(p);
                p.x=tx<=0?0:Math.clamp((int)Math.round(1000*((mx-grabX-previewX)/factor-4)/tx),0,1000);
                p.y=ty<=0?0:Math.clamp((int)Math.round(1000*((my-grabY-previewY)/factor-4)/ty),0,1000);
                return true;
            }
            return super.mouseDragged(mx,my,button,dx,dy);
        }
        @Override public boolean mouseReleased(double mx,double my,int button){
            if(button==0&&dragging!=null){dragging=null;save();return true;}
            return super.mouseReleased(mx,my,button);
        }
        @Override public void onClose(){save();if(minecraft!=null)minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private RaidHudSettings(){}
}
