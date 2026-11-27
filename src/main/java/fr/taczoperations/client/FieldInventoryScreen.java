package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.FieldInventoryMenu;
import fr.taczoperations.operations.OpenFieldInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT)
public final class FieldInventoryScreen extends AbstractContainerScreen<FieldInventoryMenu>{
    private static final net.minecraft.client.KeyMapping TACTICAL=new net.minecraft.client.KeyMapping(
        "key.taczoperations.field_inventory",org.lwjgl.glfw.GLFW.GLFW_KEY_I,"key.categories.taczoperations");
    private static final int TEXT=0xffd3dac7, GOLD=0xffd8c783;
    public FieldInventoryScreen(FieldInventoryMenu menu,Inventory inv,Component title){
        super(menu,inv,title);imageWidth=FieldInventoryMenu.WIDTH;imageHeight=FieldInventoryMenu.HEIGHT;
    }
    @EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static class Registration{
        @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(OperationsMod.FIELD_INVENTORY.get(),FieldInventoryScreen::new);}
        @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e){e.register(TACTICAL);}
    }
    private static boolean canOpen(Minecraft mc){
        if(mc.player==null||mc.level==null||!mc.player.isAlive()||mc.player.isCreative()||mc.player.isSpectator()
            ||!ModList.get().isLoaded("curios"))return false;
        return fr.taczoperations.compat.FieldCurios.handler(mc.player,FieldInventoryMenu.BAG)!=null
            &&fr.taczoperations.compat.FieldCurios.handler(mc.player,FieldInventoryMenu.RIG)!=null
            &&fr.taczoperations.compat.FieldCurios.handler(mc.player,FieldInventoryMenu.GOGGLES)!=null
            &&fr.taczoperations.compat.FieldCurios.handler(mc.player,FieldInventoryMenu.HEADSET)!=null;
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        Minecraft mc=Minecraft.getInstance();
        boolean requested=false;
        while(TACTICAL.consumeClick())requested=true;
        if(requested&&mc.screen==null&&canOpen(mc))PacketDistributor.sendToServer(new OpenFieldInventory());
    }
    // E opens the tactical inventory only in the raid dimension. Shift+E stays classic.
    @SubscribeEvent public static void opening(ScreenEvent.Opening event){
        Minecraft mc=Minecraft.getInstance();
        if(openingClassic||event.getCurrentScreen()!=null||!(event.getNewScreen() instanceof InventoryScreen)
            ||mc.player==null||mc.player.isCreative()||mc.player.isSpectator()||Screen.hasShiftDown()
            ||mc.level==null||!mc.level.dimension().equals(fr.taczoperations.operations.ForestExpedition.FOREST)
            ||!canOpen(mc))return;
        event.setCanceled(true);
        PacketDistributor.sendToServer(new OpenFieldInventory());
    }
    @Override protected void init(){
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Classique"),b->{
            if(minecraft==null||minecraft.player==null)return;
            minecraft.player.closeContainer();
            // This opening originates from the tactical screen; suppress the E interception.
            openingClassic=true;
            try{minecraft.setScreen(new InventoryScreen(minecraft.player));}finally{openingClassic=false;}
        }).bounds(leftPos+115,topPos+102,62,14).build());
    }
    private static boolean openingClassic;
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        int x=leftPos,y=topPos;
        g.fill(x,y,x+imageWidth,y+imageHeight,0xff20251f);
        g.renderOutline(x,y,imageWidth,imageHeight,0xff66715b);
        g.fill(x+3,y+3,x+imageWidth-3,y+14,0xff353e2d);
        for(var slot:menu.slots){
            int sx=x+slot.x-1,sy=y+slot.y-1;
            g.fill(sx,sy,sx+18,sy+18,0xff141914);
            g.renderOutline(sx,sy,18,18,slot.index>=FieldInventoryMenu.STORAGE_START?0xff85976c:0xff596351);
        }
        g.fill(x+30,y+18,x+86,y+92,0xff171b17);
        if(minecraft!=null&&minecraft.player!=null)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+30,y+18,x+86,y+92,32,.0625F,mx,my,minecraft.player);
        if(menu.bagSize()==0)g.drawString(font,"Aucun sac équipé",x+194,y+40,TEXT,false);
        if(menu.rigSize()==0)g.drawString(font,"Aucun gilet équipé",x+194,y+210,TEXT,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        g.drawString(font,"INVENTAIRE TACTIQUE",8,4,TEXT,false);
        g.drawString(font,"Lunettes",111,22,TEXT,false);
        g.drawString(font,"Gilet",111,44,TEXT,false);
        g.drawString(font,"Sac à dos",111,66,TEXT,false);
        g.drawString(font,"Casque audio",111,88,TEXT,false);
        g.drawString(font,"INVENTAIRE",10,118,GOLD,false);
        g.drawString(font,"BARRE RAPIDE",10,182,GOLD,false);
        g.drawString(font,"SAC À DOS · "+menu.bagSize(),194,17,GOLD,false);
        g.drawString(font,"GILET · "+menu.rigSize(),194,182,GOLD,false);
        g.drawString(font,"E / "+TACTICAL.getTranslatedKeyMessage().getString()+" : fermer",10,229,TEXT,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);
        renderTooltip(g,mx,my);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(TACTICAL.matches(key,scan)){
            onClose();return true;
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        return super.mouseClicked(x,y,button);
    }
}
