package fr.taczoperations.client;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Antialiased, build-generated, nine-slice panels. Corners are never stretched. */
final class TacticalSkin {
    private static ResourceLocation tex(String path){return ResourceLocation.fromNamespaceAndPath("taczoperations","textures/gui/hd/"+path+".png");}
    private static final ResourceLocation FILL=tex("rounded_fill"),BORDER=tex("rounded_border"),HEADER=tex("header");
    private static final ResourceLocation[] PORTRAITS=new ResourceLocation[11];
    static{for(int i=0;i<PORTRAITS.length;i++)PORTRAITS[i]=tex("portrait_"+i);}
    static final int ORANGE=0xffff3838,PALE=0xffe2e4e6,EDGE=0xff74777b;
    private static void linear(ResourceLocation id){
        var textures=Minecraft.getInstance().getTextureManager();
        textures.bindForSetup(id);textures.getTexture(id).setFilter(true,false);
    }
    private static void slice(GuiGraphics g,ResourceLocation id,int x,int y,int w,int h,int u,int v,int sw,int sh){
        if(w>0&&h>0)g.blit(id,x,y,w,h,u,v,sw,sh,128,128);
    }
    private static void rounded(GuiGraphics g,ResourceLocation id,int x,int y,int w,int h,int color){
        if(w<=0||h<=0)return;
        int r=Math.min(5,Math.min(w,h)/2);
        if(r<2){g.fill(x,y,x+w,y+h,color);return;}
        linear(id);RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f);
        try{
            int m=w-2*r,n=h-2*r;
            slice(g,id,x,y,r,r,0,0,32,32);slice(g,id,x+r,y,m,r,32,0,64,32);slice(g,id,x+w-r,y,r,r,96,0,32,32);
            slice(g,id,x,y+r,r,n,0,32,32,64);slice(g,id,x+r,y+r,m,n,32,32,64,64);slice(g,id,x+w-r,y+r,r,n,96,32,32,64);
            slice(g,id,x,y+h-r,r,r,0,96,32,32);slice(g,id,x+r,y+h-r,m,r,32,96,64,32);slice(g,id,x+w-r,y+h-r,r,r,96,96,32,32);
            g.flush();
        }finally{RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();}
    }
    static void cut(GuiGraphics g,int x,int y,int w,int h,int c){rounded(g,FILL,x,y,w,h,c);}
    static void outline(GuiGraphics g,int x,int y,int w,int h,int c){rounded(g,BORDER,x,y,w,h,c);}
    static void panel(GuiGraphics g,int x,int y,int w,int h){
        cut(g,x,y,w,h,0xff111214);outline(g,x,y,w,h,0xff66696c);
        if(w>12&&h>12)outline(g,x+2,y+2,w-4,h-4,0xff27292c);
    }
    static void tile(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hover){
        if(selected){outline(g,x-2,y-2,w+4,h+4,0x35ff2727);outline(g,x-1,y-1,w+2,h+2,0xa0e92323);}
        cut(g,x,y,w,h,selected?0xff3a1318:hover?0xff34373a:0xff242629);
        if(w>12&&h>12)g.fillGradient(x+5,y+5,x+w-5,y+h-5,selected?0xff50191d:0xff303337,selected?0xff180a0c:0xff101113);
        outline(g,x,y,w,h,selected?ORANGE:hover?0xffbfc3c7:0xff616468);
    }
    static void stripes(GuiGraphics g,int x,int y,int color){
        for(int i=0;i<4;i++)for(int j=0;j<7;j++)g.fill(x+i*10+6-j,y+j,x+i*10+11-j,y+j+1,color);
    }
    static void shell(GuiGraphics g){
        cut(g,3,3,830,444,0xff0f1012);outline(g,3,3,830,444,0xffa3a6a9);
        outline(g,6,6,824,438,0xff46494d);
        panel(g,9,71,144,370);stripes(g,20,426,0xffdb2026);
    }
    static void logo(GuiGraphics g){
        linear(HEADER);g.blit(HEADER,10,9,814,54,0,0,2048,136,2048,136);
        var font=Minecraft.getInstance().font;
        var style=Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("taczoperations","heading"));
        var brand=Component.literal("BLACKZ").withStyle(style.withColor(0xffe9ebed))
            .append(Component.literal("X").withStyle(style.withColor(0xffff3138)))
            .append(Component.literal("NE").withStyle(style.withColor(0xffe9ebed)));
        float logoScale=Math.min(3.0f,212f/Math.max(1,font.width(brand)));
        g.pose().pushPose();g.pose().translate(23,16,0);g.pose().scale(logoScale,logoScale,1);
        g.drawString(font,brand,0,0,0xffffffff,true);g.pose().popPose();
        var sub=Component.literal("E S C A P E   E X P E R I E N C E").withStyle(style);
        float subScale=Math.min(.92f,200f/Math.max(1,font.width(sub)));
        g.pose().pushPose();g.pose().translate(23+(212-font.width(sub)*subScale)/2,47,0);g.pose().scale(subScale,subScale,1);
        g.drawString(font,sub,0,0,0xffd0d3d6,false);g.pose().popPose();
        g.fill(12,61,821,62,0xffe9272c);
        stripes(g,775,16,0xffd9232c);
    }
    static boolean portrait(GuiGraphics g,int trader,int x,int y,int size){
        if(trader<0||trader>=PORTRAITS.length)return false;
        var id=PORTRAITS[trader];linear(id);g.blit(id,x,y,size,size,0,0,512,512,512,512);return true;
    }
    static void picture(GuiGraphics g,ResourceLocation id,int x,int y,int w,int h){
        linear(id);
        final int sw=1920,sh=860;
        int cw=Math.min(sw,Math.round(sh*(float)w/h)),ch=Math.min(sh,Math.round(sw*(float)h/w));
        g.blit(id,x,y,w,h,(sw-cw)/2,(sh-ch)/2,cw,ch,sw,sh);
    }
    static void chevron(GuiGraphics g,int x,int y,boolean down,int color){
        for(int i=0;i<4;i++){int yy=down?y+i:y+3-i;g.fill(x+i,yy,x+7-i,yy+1,color);}
    }
    private TacticalSkin(){}
}
