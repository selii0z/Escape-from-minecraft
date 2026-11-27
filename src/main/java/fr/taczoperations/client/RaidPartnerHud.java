package fr.taczoperations.client;
import fr.taczoperations.operations.RaidPartnerPayload;
import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidPartnerHud {
    private static RaidPartnerPayload state;
    private static Object level;
    private static long updated,lastFrame;
    private static net.minecraft.world.phys.Vec3 smooth;
    public static void handle(RaidPartnerPayload payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(!payload.visible()||state==null||level!=mc.level||!state.name().equals(payload.name())){
                smooth=null;lastFrame=0;
            }
            state=payload.visible()?payload:null;level=mc.level;updated=System.nanoTime();
        });
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(state==null||mc.level==null||mc.level!=level||mc.player==null||!mc.player.isAlive()||mc.player.isSpectator()
            ||mc.options.hideGui||!mc.level.dimension().equals(ForestExpedition.FOREST)||System.nanoTime()-updated>3_000_000_000L)return;
        var camera=mc.gameRenderer.getMainCamera();
        var pos=camera.getPosition();
        var target=new net.minecraft.world.phys.Vec3(state.x(),state.y(),state.z());
        long now=System.nanoTime();
        double dt=lastFrame==0?0:Math.clamp((now-lastFrame)/1e9,0,.1);
        lastFrame=now;
        if(smooth==null||smooth.distanceToSqr(target)>64*64)smooth=target;
        // Outside client tracking range, smooth the five-tick server updates in world space.
        smooth=smooth.lerp(target,1-Math.exp(-dt*12));
        var marker=smooth;
        // Within tracking range use the exact same interpolated position as the player model.
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var partner:mc.level.players()){
            if(partner==mc.player||!partner.getGameProfile().getName().equals(state.name())||!partner.isAlive())continue;
            marker=partner.getPosition(partial).add(0,partner.getBbHeight()+.25,0);
            smooth=marker;
            break;
        }
        double dx=marker.x-pos.x,dy=marker.y-pos.y,dz=marker.z-pos.z;
        double yaw=Math.toRadians(camera.getYRot()),pitch=Math.toRadians(camera.getXRot());
        double right=-Math.cos(yaw)*dx-Math.sin(yaw)*dz;
        double horizontal=-Math.sin(yaw)*dx+Math.cos(yaw)*dz;
        double forward=horizontal*Math.cos(pitch)-dy*Math.sin(pitch);
        double up=horizontal*Math.sin(pitch)+dy*Math.cos(pitch);
        var g=event.getGuiGraphics();int w=g.guiWidth(),h=g.guiHeight();
        double focal=h/(2.0*Math.tan(Math.toRadians(mc.options.fov().get()/2.0)));
        boolean front=forward>.1;
        double sx=front?right*focal/forward:Math.copySign(w,right==0?1:right);
        double sy=front?-up*focal/forward:0;
        int x=(int)Math.round(Math.clamp(w/2.0+sx,60,w-60)),y=(int)Math.round(Math.clamp(h/2.0+sy,32,h-65));
        int color=0xff77e898;
        String name=state.name()+" · "+Math.round(Math.sqrt(dx*dx+dy*dy+dz*dz))+" m";
        int tw=mc.font.width(name);
        g.fill(x-tw/2-4,y-15,x+tw/2+4,y-3,0xb0132118);
        g.drawCenteredString(mc.font,name,x,y-13,color);
        g.fill(x-2,y-2,x+3,y+3,color);
        g.renderOutline(x-4,y-4,9,9,0xff264f33);
    }
    private RaidPartnerHud(){}
}
