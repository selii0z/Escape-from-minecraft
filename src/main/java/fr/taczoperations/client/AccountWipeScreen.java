package fr.taczoperations.client;

import fr.taczoperations.operations.WipeAccount;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AccountWipeScreen extends Screen {
    private final Screen parent;
    private final int menuId;
    private int stage;
    private EditBox confirmation;
    private Button confirm;
    public AccountWipeScreen(Screen parent,int menuId){
        super(Component.literal("Réinitialiser mon compte BLACKZXNE"));this.parent=parent;this.menuId=menuId;
    }
    @Override protected void init(){
        int w=Math.min(360,width-16),x=(width-w)/2,y=height/2;
        addRenderableWidget(Button.builder(Component.literal("Annuler"),b->onClose()).bounds(x+12,y+63,w/2-18,22).build());
        if(stage==0){
            addRenderableWidget(Button.builder(Component.literal("Continuer"),b->{stage=1;rebuildWidgets();}).bounds(x+w/2+6,y+63,w/2-18,22).build());
        }else{
            confirmation=new EditBox(font,x+12,y+31,w-24,22,Component.literal("WIPE"));
            confirmation.setMaxLength(4);
            addRenderableWidget(confirmation);
            confirm=addRenderableWidget(Button.builder(Component.literal("Réinitialiser définitivement"),b->{
                if(!"WIPE".equals(confirmation.getValue()))return;
                PacketDistributor.sendToServer(new WipeAccount(menuId,"WIPE"));
                minecraft.setScreen(parent);
            }).bounds(x+w/2+6,y+63,w/2-18,22).build());
            confirm.active=false;
            confirmation.setResponder(s->confirm.active="WIPE".equals(s));
            setInitialFocus(confirmation);
        }
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xe0101114);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g,mx,my,partial);
        int w=Math.min(360,width-16),x=(width-w)/2,y=height/2;
        g.fill(x,y-97,x+w,y+99,0xff202124);g.renderOutline(x,y-97,w,196,0xffbe343a);
        g.drawCenteredString(font,"RÉINITIALISATION DU COMPTE",width/2,y-83,0xffef9a87);
        g.drawCenteredString(font,"Étape "+(stage+1)+"/2 · Action irréversible",width/2,y-67,0xffe2e4e6);
        String[] lines={"Niveaux, compétences, crédits, contrats, campagne,","statistiques, améliorations et réserve seront effacés.","Les assurances non récupérées seront perdues.","Ton inventaire Minecraft et tes constructions restent intacts."};
        for(int i=0;i<lines.length;i++)g.drawCenteredString(font,lines[i],width/2,y-47+14*i,0xffd2d9ce);
        g.drawCenteredString(font,stage==0?"Souhaites-tu vraiment recommencer ?":"Saisis WIPE pour confirmer.",width/2,y+15,0xffef9a87);
        super.render(g,mx,my,partial);
    }
    @Override public void onClose(){if(minecraft!=null)minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
