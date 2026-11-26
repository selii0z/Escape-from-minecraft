package fr.taczoperations.client;

import fr.taczoperations.operations.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT)
public final class DiscoveryClient {
    private static final net.minecraft.client.KeyMapping INSPECT=new net.minecraft.client.KeyMapping(
        "key.taczoperations.inspect",org.lwjgl.glfw.GLFW.GLFW_KEY_U,"key.categories.taczoperations");
    @EventBusSubscriber(modid="taczoperations",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event){event.register(INSPECT);}
    }
    static boolean inspectKey(int key,int scan){return INSPECT.matches(key,scan);}
    static boolean inspectMouse(int button){return button==2||INSPECT.matchesMouse(button);}
    static String keyLabel(){return INSPECT.getTranslatedKeyMessage().getString();}
    private static final Set<String> KNOWN=new HashSet<>();
    public static void handle(DiscoveryState state,IPayloadContext context){
        context.enqueueWork(()->{if(state.replace())KNOWN.clear();KNOWN.addAll(state.keys());});
    }
    public static boolean unknown(ItemStack stack){
        String key=ItemDiscovery.identity(stack);return !key.isEmpty()&&!KNOWN.contains(key);
    }
    public static void request(int menu,int kind,int row){
        PacketDistributor.sendToServer(new InspectItem(menu,kind,row));
    }
    public static boolean inspectSlot(AbstractContainerMenu menu,Slot slot){
        if(slot==null||!slot.hasItem()||!unknown(slot.getItem()))return false;
        request(menu.containerId,0,slot.index);return true;
    }
    public static void maskSlots(GuiGraphics g,AbstractContainerMenu menu,int left,int top){
        var mc=Minecraft.getInstance();
        g.pose().pushPose();g.pose().translate(0,0,350);
        for(var slot:menu.slots){
            if(!slot.isActive()||!unknown(slot.getItem()))continue;
            int x=left+slot.x,y=top+slot.y;
            g.fill(x,y,x+16,y+16,0xff242c23);
            g.renderOutline(x,y,16,16,0xff68785c);
            g.drawCenteredString(mc.font,"?",x+8,y+4,0xffe5ce88);
        }
        g.pose().popPose();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tooltip(ItemTooltipEvent event){
        var screen=Minecraft.getInstance().screen;
        if(!(screen instanceof RaidSearchScreen||screen instanceof FieldInventoryScreen||screen instanceof OperationsScreen))return;
        if(!unknown(event.getItemStack()))return;
        event.getToolTip().clear();
        event.getToolTip().add(Component.literal("Objet non identifié").withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.literal(keyLabel()+" ou clic molette pour examiner").withStyle(ChatFormatting.GRAY));
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){KNOWN.clear();}
    private DiscoveryClient(){}
}
