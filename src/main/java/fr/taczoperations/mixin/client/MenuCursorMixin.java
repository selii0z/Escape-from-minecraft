package fr.taczoperations.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the pointer across EFM menu refreshes, including a server close/open pair. */
@Mixin(Minecraft.class)
public abstract class MenuCursorMixin {
    @Unique private double efm$cursorX,efm$cursorY;
    @Unique private long efm$cursorAt;
    @Unique private Object efm$cursorLevel,efm$cursorConnection;
    @Unique private static boolean efm$isMenu(Screen screen){
        if(screen==null)return false;
        if(screen.getClass().getName().startsWith("fr.taczoperations.client."))return true;
        return screen instanceof AbstractContainerScreen<?> && screen.getTitle().getString().startsWith("Réserve personnelle");
    }
    @Inject(method="setScreen",at=@At("HEAD"))
    private void efm$rememberCursor(Screen next,CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;
        if(!efm$isMenu(mc.screen)||mc.level==null||mc.getConnection()==null||mc.mouseHandler.isMouseGrabbed())return;
        if(next!=null&&!efm$isMenu(next)){efm$cursorAt=0;return;}
        // GLFW coordinates are window-relative and do not depend on the GUI scale.
        long window=mc.getWindow().getWindow();
        double[] x={0},y={0};
        GLFW.glfwGetCursorPos(window,x,y);
        efm$cursorX=x[0];efm$cursorY=y[0];efm$cursorAt=System.nanoTime();
        efm$cursorLevel=mc.level;efm$cursorConnection=mc.getConnection();
    }
    @Inject(method="setScreen",at=@At("RETURN"))
    private void efm$restoreCursor(Screen next,CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;
        if(efm$cursorAt==0)return;
        if(mc.level!=efm$cursorLevel||mc.getConnection()!=efm$cursorConnection
            ||System.nanoTime()-efm$cursorAt>2_000_000_000L){efm$cursorAt=0;return;}
        // Do not move the pointer while gameplay owns it. A following EFM screen
        // may be opened immediately by the next server packet.
        if(mc.screen==null)return;
        if(efm$isMenu(mc.screen)&&!mc.mouseHandler.isMouseGrabbed()){
            long window=mc.getWindow().getWindow();
            int[] w={0},h={0};GLFW.glfwGetWindowSize(window,w,h);
            if(w[0]>0&&h[0]>0){
                GLFW.glfwSetCursorPos(window,Math.clamp(efm$cursorX,0.0,w[0]-1.0),Math.clamp(efm$cursorY,0.0,h[0]-1.0));
            }
        }
        efm$cursorAt=0;
    }
}
