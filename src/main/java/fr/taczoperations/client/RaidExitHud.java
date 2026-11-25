package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.RaidExitPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidExitHud {
    private static RaidExitPayload state;
    private static Object level;
    private static long updated;
    public static void handle(RaidExitPayload payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(!payload.visible()||mc.level==null||!mc.level.dimension().equals(ForestExpedition.FOREST)){
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
        var graphics=event.getGuiGraphics();
        graphics.pose().pushPose();
        try{
            graphics.pose().scale(RaidHudSettings.scale(),RaidHudSettings.scale(),1);
            if(RaidHudSettings.visible())draw(event);
        }finally{graphics.pose().popPose();}
    }
    private static void draw(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!valid(mc)||state==null||mc.options.hideGui||System.nanoTime()-updated>3_500_000_000L)return;
        int w=RaidHudSettings.width();
        // The original nearest-exit actionbar remains the compact fallback.
        if(w<320)return;
        var g=event.getGuiGraphics();
        int width=146,x=RaidHudSettings.right()?8:w-width-8,y=8;
        g.fill(x,y,x+width,y+17,RaidHudSettings.bg(0xca171d18));
        g.drawString(mc.font,"EXTRACTIONS",x+7,y+5,0xffd3dbcc,false);
        y+=19;
        row(g,mc,x,y,width,"Principale",RaidExitPayload.OPEN,state.primaryDistance());y+=25;
        row(g,mc,x,y,width,state.industrial()?"Clé industrielle":"Relais",state.secondState(),state.secondDistance());y+=25;
        if(state.industrial())row(g,mc,x,y,width,"Payante · "+state.price()+" cr.",state.thirdState(),state.thirdDistance());
    }
    private static void row(GuiGraphics g,Minecraft mc,int x,int y,int w,String title,int status,int distance){
        int color=status==RaidExitPayload.OPEN?0xffa6bf7d:status==RaidExitPayload.CLOSED?0xffe07b72:0xffe5ce88;
        String label=switch(status){
            case RaidExitPayload.OPEN -> "Disponible";
            case RaidExitPayload.CLOSED -> "Fermée";
            case RaidExitPayload.RELAY -> "Activer le relais";
            case RaidExitPayload.KEY -> "Clé requise";
            case RaidExitPayload.CREDITS -> "Crédits requis";
            default -> "Indisponible";
        };
        String meters=Math.max(0,distance)+" m";
        g.fill(x,y,x+w,y+23,0xca171d18);g.fill(x,y,x+2,y+23,color);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(title,w-12),x+7,y+3,0xffd3dbcc,false);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(label,Math.max(0,w-15-mc.font.width(meters))),x+7,y+13,color,false);
        g.drawString(mc.font,meters,x+w-5-mc.font.width(meters),y+13,0xffb8c0af,false);
    }
    private RaidExitHud(){}
}
