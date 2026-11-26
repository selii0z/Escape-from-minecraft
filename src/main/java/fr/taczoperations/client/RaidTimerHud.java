package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.RaidTimerPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidTimerHud {
    private static RaidTimerPayload state;
    private static Object level;
    private static long updated;
    public static void handle(RaidTimerPayload payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(mc.level==null||!mc.level.dimension().equals(ForestExpedition.FOREST))return;
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
        var graphics=event.getGuiGraphics();
        graphics.pose().pushPose();
        try{
            graphics.pose().scale(RaidHudSettings.scale(RaidHudSettings.Panel.TIMER),RaidHudSettings.scale(RaidHudSettings.Panel.TIMER),1);
            if(RaidHudSettings.visible(RaidHudSettings.Panel.TIMER))draw(event);
        }finally{graphics.pose().popPose();}
    }
    private static void draw(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!valid(mc)||state==null||mc.options.hideGui)return;
        if(System.nanoTime()-updated>3_500_000_000L)return;
        var express=fr.taczoperations.worldgen.ExpressZone.at(mc.player.getBlockX(),mc.player.getBlockZ());
        int max=express!=null?900:state.industrial()?1800:2700;
        int seconds=Math.clamp(state.seconds(),0,max);
        int w=RaidHudSettings.width(RaidHudSettings.Panel.TIMER);
        // Prefer the upper center; at small GUI sizes keep clear of the injury column.
        int width=150;
        int x=RaidHudSettings.x(RaidHudSettings.Panel.TIMER),y=RaidHudSettings.y(RaidHudSettings.Panel.TIMER);
        
        int color=seconds<=60?0xffe07b72:seconds<=300?0xffe5ce88:0xffb5c9a4;
        var g=event.getGuiGraphics();
        g.fill(x,y,x+width,y+28,RaidHudSettings.bg(RaidHudSettings.Panel.TIMER,0xca171d18));
        g.fill(x,y,x+2,y+28,color);
        String label=express!=null?"EXPRESS · "+express.title.toUpperCase(java.util.Locale.ROOT):state.industrial()?"ZONE INDUSTRIELLE":"FORÊT NOIRE";
        g.drawCenteredString(mc.font,label,x+width/2,y+3,0xffd3dbcc);
        String clock=String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
        g.drawCenteredString(mc.font,clock,x+width/2,y+14,color);
        g.fill(x+3,y+25,x+width-3,y+27,0xff414b3c);
        g.fill(x+3,y+25,x+3+(width-6)*seconds/max,y+27,color);
    }
    private RaidTimerHud(){}
}
