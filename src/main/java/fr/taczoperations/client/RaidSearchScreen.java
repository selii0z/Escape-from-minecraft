package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.RaidSearchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class RaidSearchScreen extends AbstractContainerScreen<RaidSearchMenu>{
    private static final int BG=0xff191a1c,FRAME=0xff55595d,OPEN=0xff303236,UNKNOWN=0xff202124,
        GOLD=0xffe5e6e8,LIME=0xffa6bf7d,SOFT=0xffb8c0af;
    public RaidSearchScreen(RaidSearchMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        imageWidth=176;imageHeight=187;
        inventoryLabelY=73;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){
        event.register(OperationsMod.SEARCH_MENU.get(),RaidSearchScreen::new);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);
        renderTooltip(g,mouseX,mouseY);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,BG);
        g.renderOutline(x,y,imageWidth,imageHeight,FRAME);
        int revealed=menu.discoveredSlots();
        for(int i=0;i<menu.slots.size();i++){
            var slot=menu.getSlot(i);
            int sx=x+slot.x,sy=y+slot.y;
            boolean hidden=i<27&&i>=revealed;
            g.fill(sx-1,sy-1,sx+17,sy+17,FRAME);
            g.fill(sx,sy,sx+16,sy+16,hidden?UNKNOWN:OPEN);
            if(hidden){
                if(i==revealed){
                    g.renderOutline(sx,sy,16,16,GOLD);
                    g.fill(sx+1,sy+13,sx+1+14*menu.itemProgress()/100,sy+15,LIME);
                    g.drawCenteredString(font,"?",sx+8,sy+3,GOLD);
                }else g.drawCenteredString(font,"?",sx+8,sy+4,0xff6e7276);
            }
        }
        int barX=x+8,barY=y+176;
        g.fill(barX,barY,barX+160,barY+4,UNKNOWN);
        int progress=(revealed*100+(revealed==27?0:menu.itemProgress()))/27;
        g.fill(barX,barY,barX+160*progress/100,barY+4,LIME);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        g.drawString(font,font.plainSubstrByWidth(title.getString(),160),8,6,GOLD,false);
        g.drawString(font,playerInventoryTitle,8,inventoryLabelY,SOFT,false);
        int n=menu.discoveredSlots();
        g.drawString(font,n==27?"Fouille terminée":"Fouille en cours...",8,164,n==27?LIME:GOLD,false);
        String count=n+"/27";
        g.drawString(font,count,168-font.width(count),164,SOFT,false);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        return super.mouseClicked(x,y,button);
    }
}
