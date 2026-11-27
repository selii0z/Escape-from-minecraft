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
    // Legacy compatibility: every item is identified immediately. No keybind or packets.
    static boolean inspectKey(int key,int scan){return false;}
    static boolean inspectMouse(int button){return false;}
    static String keyLabel(){return "";}
    public static void handle(DiscoveryState state,IPayloadContext context){}
    public static boolean unknown(ItemStack stack){return false;}
    public static void request(int menu,int kind,int row){}
    public static boolean inspectSlot(AbstractContainerMenu menu,Slot slot){return false;}
    public static void maskSlots(GuiGraphics g,AbstractContainerMenu menu,int left,int top){}
    @SubscribeEvent
    public static void legacyMilitaryTagTooltip(ItemTooltipEvent event){
        var stack=event.getItemStack();
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("taczoperations:military_tag"))return;
        var lines=event.getToolTip();
        for(int i=0;i<lines.size();i++){
            var line=lines.get(i);
            String raw=line.getString();
            String prefix="Arme tenue : ";
            if(raw.startsWith(prefix+"item.")){
                String key=raw.substring(prefix.length());
                String fallback=key.substring(key.lastIndexOf('.')+1).replace('_',' ').replace('-',' ').toUpperCase(java.util.Locale.ROOT);
                lines.set(i,Component.literal(prefix).append(Component.translatableWithFallback(key,fallback)).withStyle(line.getStyle()));
            }else if(raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T.*Z")){
                try{
                    String date=java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.parse(raw));
                    lines.set(i,Component.literal("Date : "+date).withStyle(line.getStyle()));
                }catch(java.time.DateTimeException ignored){}
            }
        }
    }
    private DiscoveryClient(){}
}
