package fr.taczoperations.client;
import fr.taczoperations.operations.RaidQuestPayload;
import fr.taczoperations.operations.ForestExpedition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class RaidQuestHud {
    private static RaidQuestPayload state;
    private static Object level;
    private static long updated;
    private static final int GOLD=0xffdec38b,WHITE=0xffefeee5,GREEN=0xffb8cf8c,AMBER=0xffdba16b,SOFT=0xffacb1a3;
    public static void handle(RaidQuestPayload payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(mc.level==null||!mc.level.dimension().equals(ForestExpedition.FOREST))return;
            state=payload.title().isEmpty()?null:payload;level=mc.level;updated=System.nanoTime();
        });
    }
    private static boolean valid(Minecraft mc){
        return mc.getConnection()!=null&&mc.level!=null&&mc.level==level&&RaidHudSettings.inRaid();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!valid(Minecraft.getInstance())){state=null;level=null;}
    }
    private static String fit(Minecraft mc,String s,int width){
        if(mc.font.width(s)<=width)return s;
        return mc.font.plainSubstrByWidth(s,Math.max(1,width-mc.font.width("…")))+"…";
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        var panel=RaidHudSettings.Panel.QUESTS;
        if(!valid(mc)||state==null||mc.options.hideGui||!RaidHudSettings.visible(panel)||System.nanoTime()-updated>3_500_000_000L)return;
        var g=event.getGuiGraphics();
        g.pose().pushPose();
        try{
            float scale=RaidHudSettings.scale(panel);g.pose().scale(scale,scale,1);
            draw(g,mc,RaidHudSettings.x(panel),RaidHudSettings.y(panel),panel);
        }finally{g.pose().popPose();}
    }
    private static void draw(GuiGraphics g,Minecraft mc,int x,int y,RaidHudSettings.Panel panel){
        int w=panel.w,h=37+state.objectives().size()*27+(state.more()>0?11:0);
        g.fill(x,y,x+w,y+h,RaidHudSettings.bg(panel,0xd0181b18));
        g.fill(x,y,x+2,y+h,GOLD);
        g.drawString(mc.font,fit(mc,"QUÊTE SUIVIE · "+state.trader(),w-12),x+6,y+5,GOLD,false);
        g.drawString(mc.font,fit(mc,state.title(),w-12),x+6,y+16,WHITE,false);
        int ry=y+30;
        for(var o:state.objectives()){
            boolean done=o.saved()>=o.target();
            String label=(done?"✓ ":o.optional()?"· ":"")+o.title();
            g.drawString(mc.font,fit(mc,label,w-12),x+6,ry,done?GREEN:WHITE,false);
            String count=o.saved()+"/"+o.target()+(o.tentative()>0?"  (+"+o.tentative()+")":"");
            String status=done?"Validé":o.atBase()?"À la base":o.tentative()>0?"À extraire":"";
            g.drawString(mc.font,count,x+6,ry+11,o.tentative()>0?AMBER:SOFT,false);
            g.drawString(mc.font,status,x+w-6-mc.font.width(status),ry+11,o.tentative()>0?AMBER:SOFT,false);
            int length=w-12,used=(int)((long)length*o.saved()/o.target()),total=(int)((long)length*(o.saved()+o.tentative())/o.target());
            g.fill(x+6,ry+22,x+6+length,ry+24,0xff3d4138);
            if(total>used)g.fill(x+6+used,ry+22,x+6+total,ry+24,AMBER);
            if(used>0)g.fill(x+6,ry+22,x+6+used,ry+24,GREEN);
            ry+=27;
        }
        if(state.more()>0)g.drawString(mc.font,fit(mc,"+"+state.more()+" autre(s) objectif(s) au tableau",w-12),x+6,ry,SOFT,false);
    }
    private RaidQuestHud(){}
}
