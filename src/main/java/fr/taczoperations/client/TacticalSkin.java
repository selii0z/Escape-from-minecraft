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
    private static final ResourceLocation FILL=tex("rounded_fill"),BORDER=tex("rounded_border"),HEADER=tex("header"),BRAND=tex("blackzxne_brand");
    private static final ResourceLocation[] CHOICES=new ResourceLocation[4];
    static{for(int i=0;i<CHOICES.length;i++)CHOICES[i]=tex("choice_"+i);}
    private static final ResourceLocation[] NAV=new ResourceLocation[7];
    static{for(int i=0;i<NAV.length;i++)NAV[i]=tex("nav_"+i);}
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
    static void navigation(GuiGraphics g,int index,int x,int y,int w,int h,boolean selected,boolean hover){
        tile(g,x,y,w,h,selected,hover);
        if(index<0||index>=NAV.length)return;
        var id=NAV[index];
        linear(id);
        RenderSystem.enableBlend();
        try{
            g.blit(id,x+2,y+2,w-4,h-4,0,0,1040,224,1040,224);
            if(selected)g.fill(x+2,y+2,x+w-2,y+h-2,0x17e51929);
            else if(hover)g.fill(x+2,y+2,x+w-2,y+h-2,0x13ffffff);
            g.flush();
        }finally{RenderSystem.disableBlend();}
        outline(g,x,y,w,h,selected?ORANGE:hover?0xffbfc3c7:0xff616468);
    }
    static void choice(GuiGraphics g,int index,int x,int y,int w,int h,boolean selected,boolean hover){
        tile(g,x,y,w,h,selected,hover);
        var id=CHOICES[index];linear(id);RenderSystem.enableBlend();
        try{
            g.blit(id,x+2,y+2,w-4,h-4,0,0,560,288,560,288);
            if(hover)g.fill(x+2,y+2,x+w-2,y+h-2,0x14ffffff);
            g.fillGradient(x+2,y+h/2,x+w-2,y+h-2,0x00000000,0xe90a0b0d);
            g.flush();
        }finally{RenderSystem.disableBlend();}
        outline(g,x,y,w,h,selected?ORANGE:hover?0xffbec4ca:0xff73777d);
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
        linear(BRAND);g.blit(BRAND,10,9,234,54,0,0,1872,432,1872,432);
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
