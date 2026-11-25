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
    private Button confirm,skip,selectAll;
    private static final net.minecraft.resources.ResourceLocation DESK=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"textures/gui/dashboard.png");
    private static final net.minecraft.resources.ResourceLocation BODY=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"interface");
    private static final net.minecraft.resources.ResourceLocation HEADING=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,"heading");
    private static Component styled(String label,boolean bold){return Component.literal(label).withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(bold?HEADING:BODY));}
    private Button button(String label,int x,int y,int w,int h,java.util.function.Consumer<Button> action){
        return addRenderableWidget(new Button(leftPos+x,topPos+y,w,h,styled(label,true),action::accept,java.util.function.Supplier::get){
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
                int xx=getX(),yy=getY();boolean hover=isHoveredOrFocused();
                g.fillGradient(xx,yy,xx+getWidth(),yy+getHeight(),!active?0xff292d27:hover?0xff515d28:0xff30342d,0xff181c17);
                g.renderOutline(xx,yy,getWidth(),getHeight(),hover&&active?0xffe5ce88:0xff716751);
                g.drawCenteredString(font,getMessage(),xx+getWidth()/2,yy+(getHeight()-8)/2,active?0xfff5f3e8:0xffa2a397);
            }
        });
    }
    public DepartureInsuranceScreen(DepartureInsuranceMenu menu,Inventory inv,Component title){
        super(menu,inv,title);imageWidth=DepartureInsuranceMenu.WIDTH;imageHeight=DepartureInsuranceMenu.HEIGHT;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(OperationsMod.DEPARTURE_INSURANCE.get(),DepartureInsuranceScreen::new);}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void init(){
        super.init();
        selectAll=button("Tout sélectionner / retirer",10,32,181,20,b->send(DepartureInsuranceMenu.SELECT_ALL));
        button("Retour",201,32,99,20,b->send(DepartureInsuranceMenu.BACK));
        confirm=button("Assurer et partir",10,208,142,21,b->send(DepartureInsuranceMenu.CONFIRM));
        skip=button("Sans assurance",158,208,142,21,b->send(DepartureInsuranceMenu.SKIP));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        minecraft.getTextureManager().bindForSetup(DESK);minecraft.getTextureManager().getTexture(DESK).setFilter(true,false);
        g.blit(DESK,x,y,0,0,imageWidth,imageHeight,imageWidth,imageHeight);
        g.fill(x+6,y+5,x+imageWidth-6,y+29,0xe9111612);
        g.fill(x+6,y+55,x+imageWidth-6,y+184,0xe5111612);
        g.fill(x+6,y+188,x+imageWidth-6,y+232,0xe9111612);
        g.renderOutline(x,y,imageWidth,imageHeight,0xff716751);
        for(int i=0;i<menu.slots.size();i++){
            var slot=menu.slots.get(i);int sx=x+slot.x-2,sy=y+slot.y-2,state=menu.state(i);
            g.fill(sx,sy,sx+21,sy+21,state==DepartureInsuranceMenu.SELECTED?0xff506b39:state==DepartureInsuranceMenu.INSURED?0xff304d4a:0xff141914);
            g.renderOutline(sx,sy,21,21,state==DepartureInsuranceMenu.SELECTED?0xffb7de79:state==DepartureInsuranceMenu.INSURED?0xff69b6ae:0xff626b56);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,styled("ASSURANCE AVANT RAID",true),10,8,0xffebd99e,false);
        g.drawString(font,styled((menu.industrial()?"Zone industrielle":"Forêt Noire")+" · "+(menu.night()?"Nuit":"Jour")+" · "+(menu.duo()?"Duo":"Solo"),false),10,19,0xffc8d0bf,false);
        if(menu.slots.isEmpty())g.drawString(font,"Aucun équipement assurable.",11,77,0xffc8d0bf,false);
        int cost=menu.selected()*DepartureInsuranceMenu.FEE;
        g.drawString(font,menu.waiting()?"En attente de ton équipier...":"Vert : sélection · Bleu : déjà assuré",10,190,0xffc8d0bf,false);
        g.drawString(font,menu.selected()+" objet(s) · "+cost+" cr. / "+menu.balance()+" cr.",10,199,cost<=menu.balance()?0xffe5d48f:0xffe98b80,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(confirm!=null){
            confirm.active=!menu.waiting()&&menu.selected()*DepartureInsuranceMenu.FEE<=menu.balance();
            confirm.setMessage(styled(menu.waiting()?"En attente du duo":"Assurer et partir",true));
            skip.active=!menu.waiting();selectAll.active=!menu.waiting();
        }
        super.render(g,mx,my,partial);renderTooltip(g,mx,my);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&!menu.waiting())for(int i=0;i<menu.slots.size();i++){
            var slot=menu.slots.get(i);int sx=leftPos+slot.x-2,sy=topPos+slot.y-2;
            if(x>=sx&&x<sx+21&&y>=sy&&y<sy+21){send(i);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
}
