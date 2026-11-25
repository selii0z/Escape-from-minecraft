package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.DepartureInsuranceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class DepartureInsuranceScreen extends AbstractContainerScreen<DepartureInsuranceMenu>{
    private Button confirm;
    public DepartureInsuranceScreen(DepartureInsuranceMenu menu,Inventory inv,Component title){
        super(menu,inv,title);imageWidth=DepartureInsuranceMenu.WIDTH;imageHeight=DepartureInsuranceMenu.HEIGHT;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(OperationsMod.DEPARTURE_INSURANCE.get(),DepartureInsuranceScreen::new);}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void init(){
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Tout sélectionner / retirer"),b->send(DepartureInsuranceMenu.SELECT_ALL)).bounds(leftPos+9,topPos+29,147,19).build());
        addRenderableWidget(Button.builder(Component.literal("Retour"),b->send(DepartureInsuranceMenu.BACK)).bounds(leftPos+162,topPos+29,82,19).build());
        confirm=addRenderableWidget(Button.builder(Component.literal("Assurer et partir"),b->send(DepartureInsuranceMenu.CONFIRM)).bounds(leftPos+9,topPos+195,117,20).build());
        addRenderableWidget(Button.builder(Component.literal("Sans assurance"),b->send(DepartureInsuranceMenu.SKIP)).bounds(leftPos+132,topPos+195,112,20).build());
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xff202720);g.renderOutline(x,y,imageWidth,imageHeight,0xff727c60);
        for(int i=0;i<menu.slots.size();i++){
            var slot=menu.slots.get(i);int sx=x+slot.x-2,sy=y+slot.y-2,state=menu.state(i);
            g.fill(sx,sy,sx+21,sy+21,state==DepartureInsuranceMenu.SELECTED?0xff506b39:state==DepartureInsuranceMenu.INSURED?0xff304d4a:0xff141914);
            g.renderOutline(sx,sy,21,21,state==DepartureInsuranceMenu.SELECTED?0xffb7de79:state==DepartureInsuranceMenu.INSURED?0xff69b6ae:0xff626b56);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,"ASSURANCE AVANT RAID",9,5,0xffebd99e,false);
        g.drawString(font,(menu.industrial()?"Zone industrielle":"Forêt Noire")+" · "+(menu.night()?"Nuit":"Jour")+" · "+(menu.duo()?"Duo":"Solo"),9,17,0xffc8d0bf,false);
        if(menu.slots.isEmpty())g.drawString(font,"Aucun équipement assurable.",11,77,0xffc8d0bf,false);
        int cost=menu.selected()*DepartureInsuranceMenu.FEE;
        g.drawString(font,"Vert : sélection · Bleu : déjà assuré",9,171,0xffc8d0bf,false);
        g.drawString(font,menu.selected()+" objet(s) · "+cost+" cr. / "+menu.balance()+" cr.",9,183,cost<=menu.balance()?0xffe5d48f:0xffe98b80,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(confirm!=null)confirm.active=menu.selected()*DepartureInsuranceMenu.FEE<=menu.balance();
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0)for(int i=0;i<menu.slots.size();i++){
            var slot=menu.slots.get(i);int sx=leftPos+slot.x-2,sy=topPos+slot.y-2;
            if(x>=sx&&x<sx+21&&y>=sy&&y<sy+21){send(i);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
}
