package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.RaidInjuryPayload;
import fr.taczoperations.operations.FieldMedicine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidInjuryHud{
    private static RaidInjuryPayload state;
    private static long updated;
    private static Object currentLevel;
    public static void handle(RaidInjuryPayload payload,IPayloadContext context){
        context.enqueueWork(()->{
            state=payload.active()?payload:null;
            updated=System.nanoTime();
            currentLevel=Minecraft.getInstance().level;
        });
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()
            ||!mc.level.dimension().equals(ForestExpedition.FOREST)||mc.level!=currentLevel){
            state=null;return;
        }
        if(mc.options.hideGui||state==null||System.nanoTime()-updated>3_500_000_000L)return;
        var g=event.getGuiGraphics();
        int x=8,y=8;
        boolean injured=state.bleed()>0||state.fracture()||state.arm();
        g.fill(x,y,x+142,y+17,0xca171d18);
        g.fill(x,y,x+2,y+17,injured?0xffdcaa66:0xff9db77e);
        g.drawString(mc.font,injured?"ÉTAT · BLESSURES":"ÉTAT · STABLE",x+7,y+5,injured?0xffe5ce88:0xffb5c9a4,false);
        y+=19;
        if(state.bleed()>0){
            row(g,mc,x,y,state.bleed()==2?"Hémorragie":"Saignement",state.bleed()==2?"Hémostatique / bandages":"Bandage",0xffe07b72);y+=25;
        }
        if(state.fracture()){row(g,mc,x,y,"Fracture","Attelle",0xffe5ce88);y+=25;}
        if(state.arm()){row(g,mc,x,y,"Bras blessé","IFAK",0xffe5ce88);y+=25;}
        var stack=mc.player.getUseItem();
        if(mc.player.isUsingItem()&&stack.getItem() instanceof FieldMedicine medicine){
            int duration=Math.max(1,medicine.getUseDuration(stack,mc.player));
            int progress=Math.clamp(100*(duration-mc.player.getUseItemRemainingTicks())/duration,0,100);
            g.fill(x,y,x+142,y+31,0xda171d18);
            String name=mc.font.plainSubstrByWidth(stack.getHoverName().getString(),132);
            g.drawString(mc.font,name,x+5,y+4,0xffe5ce88,false);
            g.fill(x+5,y+17,x+137,y+21,0xff414b3c);
            g.fill(x+5,y+17,x+5+132*progress/100,y+21,0xffa6bf7d);
            g.drawString(mc.font,progress+" %",x+5,y+22,0xffc7cfbf,false);
        }
    }
    private static void row(GuiGraphics g,Minecraft mc,int x,int y,String name,String treatment,int color){
        g.fill(x,y,x+142,y+23,0xca171d18);
        g.fill(x,y,x+2,y+23,color);
        g.drawString(mc.font,name,x+7,y+3,color,false);
        g.drawString(mc.font,treatment,x+7,y+13,0xffbdc7b8,false);
    }
    private RaidInjuryHud(){}
}
