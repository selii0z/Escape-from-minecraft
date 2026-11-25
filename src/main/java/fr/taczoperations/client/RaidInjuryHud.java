package fr.taczoperations.client;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.RaidInjuryPayload;
import fr.taczoperations.operations.FieldMedicine;
import fr.taczoperations.operations.WeaponCondition;
import fr.taczoperations.operations.BallisticVest;
import net.minecraft.world.entity.EquipmentSlot;
import com.tacz.guns.api.item.IGun;
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
        if(y+39<=mc.getWindow().getGuiScaledHeight()-65){
            g.fill(x,y,x+142,y+38,0xca171d18);
            survivalMeter(g,mc,x+5,y+3,"Hydratation",state.water(),0xff83b7ce);
            survivalMeter(g,mc,x+5,y+20,"Énergie",state.energy(),0xffcbb776);
            y+=40;
        }
        if(state.bleed()>0){
            row(g,mc,x,y,state.bleed()==2?"Hémorragie":"Saignement",state.bleed()==2?"Hémostatique / bandage":"Bandage",0xffe07b72);y+=25;
        }
        if(state.fracture()){row(g,mc,x,y,"Fracture","Attelle",0xffe5ce88);y+=25;}
        if(state.arm()){row(g,mc,x,y,"Bras blessé","Kit de premiers secours",0xffe5ce88);y+=25;}
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
            y+=33;
        }
        // Keep all raid information in one column, never cover the crosshair
        // or TaCZ's lower-right ammunition display. On small GUI sizes,
        // omit the noncritical armor panel rather than overlap the hotbar.
        int limit=mc.getWindow().getGuiScaledHeight()-65;
        var weapon=mc.player.getMainHandItem();
        if(weapon.getItem() instanceof IGun){
            int value=WeaponCondition.condition(weapon);
            boolean jammed=WeaponCondition.jammed(weapon);
            int color=value==0||jammed?0xffe07b72:conditionColor(value,10000);
            boolean alert=value==0||jammed;
            int height=alert?36:25;
            if(y+height<=limit){
                g.fill(x,y,x+142,y+height,0xca171d18);
                String percent=String.format(java.util.Locale.FRANCE,"%.1f %%",value/100.0);
                String name=mc.font.plainSubstrByWidth(weapon.getHoverName().getString(),Math.max(0,126-mc.font.width(percent)));
                g.drawString(mc.font,name,x+5,y+4,0xffd3dbcc,false);
                g.drawString(mc.font,percent,x+137-mc.font.width(percent),y+4,color,false);
                meter(g,x+5,y+16,132,value,10000,color);
                if(alert)g.drawString(mc.font,value==0?"HORS SERVICE":"ENRAYÉE · RECHARGER",x+5,y+25,color,false);
                y+=height+2;
            }
        }
        if(y+59<=limit){
            EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            String[] labels={"Tête","Torse","Jambes","Pieds"};
            boolean equipped=false;
            for(var slot:slots)if(BallisticVest.tier(mc.player.getItemBySlot(slot),slot)>0){equipped=true;break;}
            if(equipped){
                g.fill(x,y,x+142,y+59,0xca171d18);
                g.drawString(mc.font,"ARMURE BALISTIQUE",x+5,y+3,0xffb8c0af,false);
                for(int i=0;i<4;i++){
                    var piece=mc.player.getItemBySlot(slots[i]);
                    int tier=BallisticVest.tier(piece,slots[i]);
                    int xx=x+5+(i%2)*67,yy=y+14+(i/2)*22;
                    int value=tier==0?0:BallisticVest.condition(piece);
                    int color=tier==0?0xff777f75:conditionColor(value,100);
                    String label=labels[i]+(tier==0?" —":" C"+tier);
                    String percent=tier==0?"":value+"%";
                    g.drawString(mc.font,mc.font.plainSubstrByWidth(label,62),xx,yy,0xffb8c0af,false);
                    g.drawString(mc.font,percent,xx+62-mc.font.width(percent),yy+10,color,false);
                    meter(g,xx,yy+13,Math.max(0,58-mc.font.width(percent)),value,100,color);
                }
            }
        }
    }
    private static void survivalMeter(GuiGraphics g,Minecraft mc,int x,int y,String label,int value,int normalColor){
        int color=value<=20?0xffe07b72:normalColor;
        String percent=value+" %";
        g.drawString(mc.font,label,x,y,0xffd3dbcc,false);
        g.drawString(mc.font,percent,x+132-mc.font.width(percent),y,color,false);
        meter(g,x,y+11,132,value,100,color);
    }
    private static int conditionColor(int value,int max){
        return value*5<=max?0xffe07b72:value*10<max*7?0xffe5ce88:0xffa6bf7d;
    }
    private static void meter(GuiGraphics g,int x,int y,int width,int value,int max,int color){
        g.fill(x,y,x+width,y+3,0xff414b3c);
        if(value>0)g.fill(x,y,x+width*value/max,y+3,color);
    }
    private static void row(GuiGraphics g,Minecraft mc,int x,int y,String name,String treatment,int color){
        g.fill(x,y,x+142,y+23,0xca171d18);
        g.fill(x,y,x+2,y+23,color);
        g.drawString(mc.font,name,x+7,y+3,color,false);
        g.drawString(mc.font,mc.font.plainSubstrByWidth(treatment,130),x+7,y+13,0xffbdc7b8,false);
    }
    private RaidInjuryHud(){}
}
