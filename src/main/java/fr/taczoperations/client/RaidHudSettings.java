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

/** Local preferences: no server protocol or raid mechanics changes. */
@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidHudSettings {
    private static final KeyMapping SHOW=new KeyMapping("Afficher les HUD EFM",GLFW.GLFW_KEY_F8,"key.categories.taczoperations");
    private static final KeyMapping SETTINGS=new KeyMapping("Réglages des HUD EFM",GLFW.GLFW_KEY_F9,"key.categories.taczoperations");
    private static final int[] SIZES={50,65,80,100,120};
    private static final int[] OPACITIES={25,50,75,100};
    private static final int[] TIMES={5,10,20,0};
    private static int size=1,opacity=2,duration=1;
    private static boolean right=false;
    private static long visibleUntil;
    private static Object previousLevel;
    private static final Path FILE=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("efm-client-hud.properties");
    static { load(); }
    private static void load(){
        Properties p=new Properties();
        if(!Files.isRegularFile(FILE))return;
        try(var in=Files.newInputStream(FILE)){
            p.load(in);
            size=Math.clamp(Integer.parseInt(p.getProperty("size","1")),0,SIZES.length-1);
            opacity=Math.clamp(Integer.parseInt(p.getProperty("opacity","2")),0,OPACITIES.length-1);
            duration=Math.clamp(Integer.parseInt(p.getProperty("duration","1")),0,TIMES.length-1);
            right=Boolean.parseBoolean(p.getProperty("right","false"));
        }catch(Exception ignored){size=1;opacity=2;duration=1;right=false;}
    }
    private static void save(){
        Properties p=new Properties();
        p.setProperty("size",Integer.toString(size));p.setProperty("opacity",Integer.toString(opacity));
        p.setProperty("duration",Integer.toString(duration));p.setProperty("right",Boolean.toString(right));
        try{Files.createDirectories(FILE.getParent());try(var out=Files.newOutputStream(FILE)){p.store(out,"Escape from Minecraft HUD");}}
        catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot save EFM HUD settings",ex);}
    }
    public static boolean inRaid(){var mc=Minecraft.getInstance();return mc.level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()&&mc.level.dimension().equals(ForestExpedition.FOREST);}
    public static boolean visible(){return inRaid()&&(TIMES[duration]==0||System.nanoTime()<visibleUntil);}
    public static float scale(){return SIZES[size]/100f;}
    public static int width(){return (int)(Minecraft.getInstance().getWindow().getGuiScaledWidth()/scale());}
    public static int height(){return (int)(Minecraft.getInstance().getWindow().getGuiScaledHeight()/scale());}
    public static boolean right(){return right;}
    public static int bg(int argb){return (argb&0xffffff)|((argb>>>24)*OPACITIES[opacity]/100<<24);}
    @EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static class Keys{
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(SHOW);e.register(SETTINGS);}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();
        if(mc.level!=previousLevel||!inRaid()){visibleUntil=0;previousLevel=mc.level;}
        while(SHOW.consumeClick())if(inRaid()&&mc.screen==null)visibleUntil=System.nanoTime()+TIMES[duration]*1_000_000_000L;
        while(SETTINGS.consumeClick())if(mc.player!=null&&mc.screen==null)mc.setScreen(new Screen(null));
    }
    public static final class Screen extends net.minecraft.client.gui.screens.Screen {
        private final net.minecraft.client.gui.screens.Screen parent;
        public Screen(net.minecraft.client.gui.screens.Screen parent){super(Component.literal("HUD de raid"));this.parent=parent;}
        @Override protected void init(){
            int w=Math.min(220,width-20),x=(width-w)/2,y=Math.max(24,(height-166)/2);
            addRenderableWidget(Button.builder(Component.literal("Taille : "+SIZES[size]+" %"),b->{size=(size+1)%SIZES.length;save();b.setMessage(Component.literal("Taille : "+SIZES[size]+" %"));}).bounds(x,y,w,20).build());
            addRenderableWidget(Button.builder(Component.literal("Opacité du fond : "+OPACITIES[opacity]+" %"),b->{opacity=(opacity+1)%OPACITIES.length;save();b.setMessage(Component.literal("Opacité du fond : "+OPACITIES[opacity]+" %"));}).bounds(x,y+23,w,20).build());
            addRenderableWidget(Button.builder(Component.literal("Santé : "+(right?"droite":"gauche")),b->{right=!right;save();b.setMessage(Component.literal("Santé : "+(right?"droite":"gauche")));}).bounds(x,y+46,w,20).build());
            addRenderableWidget(Button.builder(Component.literal(timeLabel()),b->{duration=(duration+1)%TIMES.length;save();b.setMessage(Component.literal(timeLabel()));}).bounds(x,y+69,w,20).build());
            addRenderableWidget(Button.builder(Component.literal("Réinitialiser"),b->{size=1;opacity=2;duration=1;right=false;save();rebuildWidgets();}).bounds(x,y+92,w,20).build());
            addRenderableWidget(Button.builder(Component.literal("Retour"),b->onClose()).bounds(x,y+115,w,20).build());
        }
        private static String timeLabel(){return "Affichage : "+(TIMES[duration]==0?"Permanent":TIMES[duration]+" secondes");}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            renderBackground(g,mx,my,partial);super.render(g,mx,my,partial);
            int y=Math.max(24,(height-166)/2);
            g.drawCenteredString(font,title,width/2,y-15,0xffe5ce88);
            g.drawCenteredString(font,"F8 : afficher · F9 : configurer",width/2,y+142,0xffc8d0bf);
        }
        @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private RaidHudSettings(){}
}
