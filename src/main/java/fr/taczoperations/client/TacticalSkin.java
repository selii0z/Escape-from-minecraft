package fr.taczoperations.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Shared tactical styling; source atlas contains cropped artwork from the approved EFM concept. */
final class TacticalSkin {
    private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("taczoperations","textures/gui/tactical_concept.png");
    static final int ORANGE=0xffee9e43,PALE=0xffe6d9c4,EDGE=0xff716d64;
    static void cut(GuiGraphics g,int x,int y,int w,int h,int color){
        g.fill(x+3,y,x+w-3,y+h,color);g.fill(x,y+3,x+w,y+h-3,color);
        g.fill(x+1,y+1,x+w-1,y+h-1,color);
    }
    static void outline(GuiGraphics g,int x,int y,int w,int h,int c){
        g.fill(x+3,y,x+w-3,y+1,c);g.fill(x+3,y+h-1,x+w-3,y+h,c);
        g.fill(x,y+3,x+1,y+h-3,c);g.fill(x+w-1,y+3,x+w,y+h-3,c);
        for(int i=1;i<=2;i++){g.fill(x+i,y+3-i,x+i+1,y+4-i,c);g.fill(x+w-i-1,y+3-i,x+w-i,y+4-i,c);
            g.fill(x+i,y+h-4+i,x+i+1,y+h-3+i,c);g.fill(x+w-i-1,y+h-4+i,x+w-i,y+h-3+i,c);}
    }
    static void panel(GuiGraphics g,int x,int y,int w,int h){
        cut(g,x,y,w,h,0xff141414);outline(g,x,y,w,h,0xff55514a);
        outline(g,x+2,y+2,w-4,h-4,0xff292929);
    }
    static void tile(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hover){
        if(selected){outline(g,x-1,y-1,w+2,h+2,0xff69421e);outline(g,x-2,y-2,w+4,h+4,0x405e371a);}
        cut(g,x,y,w,h,selected?0xff3d2c19:hover?0xff333231:0xff262626);
        g.fillGradient(x+2,y+2,x+w-2,y+h-2,selected?0xff493723:hover?0xff393735:0xff303030,selected?0xff211a13:0xff171717);
        outline(g,x,y,w,h,selected?ORANGE:hover?0xffb6a58e:0xff59554f);
        g.fill(x+4,y+1,x+w-4,y+2,selected?0xfff3bd75:0xff4a4845);
    }
    static void stripes(GuiGraphics g,int x,int y,int color){
        for(int i=0;i<4;i++)for(int j=0;j<7;j++)g.fill(x+i*10+6-j,y+j,x+i*10+11-j,y+j+1,color);
    }
    static void shell(GuiGraphics g){
        cut(g,3,3,830,444,0xff383735);outline(g,3,3,830,444,0xffa19b90);
        cut(g,6,6,824,438,0xff242424);outline(g,6,6,824,438,0xff5d5954);
        panel(g,9,71,144,370);
        // Fixed low-cost surface detail: no per-frame random generation or texture allocations.
        for(int i=0;i<40;i++){int x=12+(i*83)%805,y=8+(i*47)%430;
            if(x<151||y<63||y>438)g.fill(x,y,x+2+(i%5),y+1,0x247f7469);}
        stripes(g,20,426,0xff98602f);stripes(g,774,22,0xffb36b30);
        for(int x:new int[]{9,824})for(int y:new int[]{9,438}){
            g.fill(x,y,x+4,y+4,0xff141414);g.fill(x+1,y+1,x+3,y+3,0xffaaa59b);g.fill(x+1,y+2,x+3,y+3,0xff46433e);
        }
    }
    static void logo(GuiGraphics g){
        g.blit(ATLAS,12,8,224,53,0,0,128,27,128,48);
        g.fill(236,58,760,59,0xffb06b2e);g.fill(236,59,760,60,0xff56361c);
    }
    static boolean portrait(GuiGraphics g,int trader,int x,int y,int size){
        int index=switch(trader){case 0->0;case 1->1;case 2->2;case 3->5;case 4->3;case 6->6;case 7->4;default->-1;};
        if(index<0)return false;
        g.blit(ATLAS,x,y,size,size,index*16,32,16,16,128,48);return true;
    }
    private TacticalSkin(){}
}
