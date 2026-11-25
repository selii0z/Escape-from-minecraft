package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.ExtractionProgressPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class ExtractionHud {
    private static ExtractionProgressPayload state;
    private static Object level;
    private static long updated;
    public static void handle(ExtractionProgressPayload payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(!payload.active()||mc.level==null||!mc.level.dimension().equals(ForestExpedition.FOREST)){
                state=null;level=null;return;
            }
            state=payload;level=mc.level;updated=System.nanoTime();
        });
    }
    private static boolean valid(Minecraft mc){
        return mc.getConnection()!=null&&mc.level!=null&&mc.level==level&&mc.player!=null
            &&mc.player.isAlive()&&!mc.player.isSpectator()&&mc.level.dimension().equals(ForestExpedition.FOREST);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!valid(Minecraft.getInstance())){state=null;level=null;}
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!valid(mc)||state==null||mc.options.hideGui||System.nanoTime()-updated>1_500_000_000L)return;
        int w=mc.getWindow().getGuiScaledWidth();
        int width=w>=420?150:90;
        int x=w>=420?(w-width)/2:Math.max(154,w-width-8),y=40;
        if(x+width>w-2)return;
        int elapsed=Math.clamp(state.elapsed(),0,100);
        int seconds=(100-elapsed+19)/20;
        var g=event.getGuiGraphics();
        g.fill(x,y,x+width,y+32,0xe01a2219);
        g.fill(x,y,x+2,y+32,0xffa6bf7d);
        g.drawCenteredString(mc.font,"EXTRACTION",x+width/2,y+4,0xffb5d78e);
        g.drawCenteredString(mc.font,seconds+" s",x+width/2,y+16,0xffedf2e4);
        g.fill(x+5,y+27,x+width-5,y+29,0xff414b3c);
        g.fill(x+5,y+27,x+5+(width-10)*elapsed/100,y+29,0xffa6bf7d);
    }
    private ExtractionHud(){}
}
