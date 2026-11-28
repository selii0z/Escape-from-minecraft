package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.CampaignMenu;
import fr.taczoperations.campaign.CampaignTrader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

@EventBusSubscriber(modid=OperationsMod.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CampaignScreen extends AbstractContainerScreen<CampaignMenu>{
    private static final int W=836,H=470, ROWS=12,ROW_H=29,DETAIL_Y=238,DETAIL_H=150;
    private static final int GOLD=0xffe5e6e8,WHITE=0xffeeebe3,SOFT=0xffb8b8ac,FRAME=0xff65696d,
        LIME=0xffb8cf8c,ORANGE=0xffdba16b,DARK=0xff161716;
    private static final ResourceLocation BODY=asset("interface"),HEADING=asset("heading"),DESK=texture("dashboard");
    private static final Pattern PROGRESS=Pattern.compile("^(.*) : (\\d+)/(\\d+)$");
    private int selected,scroll,detailScroll,filter;
    private float scale=1;
    private boolean initial=true,dragList=false,dragDetail=false;
    public CampaignScreen(CampaignMenu menu,Inventory inv,Component title){super(menu,inv,title);selected=menu.selected();}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(OperationsMod.CAMPAIGN_MENU.get(),CampaignScreen::new);}
    private static ResourceLocation asset(String s){return ResourceLocation.fromNamespaceAndPath(OperationsMod.ID,s);}
    private static ResourceLocation texture(String s){return asset("textures/gui/"+s+".png");}
    @Override protected void init(){
        scale=Math.min(width/(float)W,height/(float)H)*.98F;
        imageWidth=Math.round(W*scale);imageHeight=Math.round(H*scale);
        super.init();
    }
    private Component styled(String s,boolean bold){return Component.literal(s).withStyle(Style.EMPTY.withFont(bold?HEADING:BODY));}
    private void text(GuiGraphics g,String s,int x,int y,float f,int color,boolean bold){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(f,f,1);g.drawString(font,styled(s,bold),0,0,color,false);g.pose().popPose();
    }
    private String fit(String s,int width,boolean bold){
        if(font.width(styled(s,bold))<=width)return s;
        while(!s.isEmpty()&&font.width(styled(s+"…",bold))>width)s=s.substring(0,s.length()-1);
        return s+"…";
    }
    private List<Component> lore(ItemStack s){var l=s.get(DataComponents.LORE);return l==null?List.of():l.lines();}
    private String line(ItemStack s,int i){var l=lore(s);return i>=0&&i<l.size()?l.get(i).getString():"";}
    private int num(String s){try{return Integer.parseInt(s);}catch(NumberFormatException e){return 0;}}
    private String name(ItemStack s){return s.isEmpty()?"Chargement…":s.getHoverName().getString();}
    private ItemStack quest(){return selected>=0&&selected<menu.count()?menu.questStack(selected):ItemStack.EMPTY;}
    private boolean hit(double x,double y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
    private void panel(GuiGraphics g,int x,int y,int w,int h){
        g.fill(x+2,y,x+w-2,y+h,DARK);g.fill(x,y+2,x+w,y+h-2,DARK);
        g.fill(x+2,y,x+w-2,y+1,FRAME);g.fill(x+2,y+h-1,x+w-2,y+h,FRAME);
        g.fill(x,y+2,x+1,y+h-2,FRAME);g.fill(x+w-1,y+2,x+w,y+h-2,FRAME);
    }
    private void tile(GuiGraphics g,int x,int y,int w,int h,boolean active,boolean hover){
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,active?0xff492327:hover?0xff343638:0xff292b2d,0xff191a1c);
        g.renderOutline(x,y,w,h,active?GOLD:hover?0xffaaa08b:0xff4b4d43);
    }
    private void item(GuiGraphics g,ItemStack s,int x,int y,float factor){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(factor,factor,1);g.renderItem(s,0,0);g.pose().popPose();
    }
    private void button(GuiGraphics g,String label,int x,int y,int w,int h,double mx,double my,boolean enabled,boolean active){
        boolean hover=enabled&&hit(mx,my,x,y,w,h);
        tile(g,x,y,w,h,enabled&&active,hover);
        text(g,label,x+(w-font.width(styled(label,true)))/2,y+(h-8)/2,1,enabled?WHITE:0xff777b72,true);
    }
    private int statusColor(String s){return switch(s){
        case "Disponible"->GOLD;case "En cours"->ORANGE;case "À valider","Terminée"->LIME;
        case "Échouée"->0xffd67b72;default->SOFT;
    };}
    private List<Integer> visible(){
        var ids=new ArrayList<Integer>();
        for(int i=0;i<menu.count();i++){
            var s=menu.questStack(i);String status=line(s,0);
            if(filter==0||filter==1&&status.equals("Disponible")||filter==2&&(status.equals("En cours")||status.equals("À valider"))||filter==3&&status.equals("Terminée")||filter==4&&(status.equals("Verrouillée")||status.equals("Non jouable")||status.equals("Échouée")))ids.add(i);
        }
        return ids;
    }
    private void bar(GuiGraphics g,int x,int y,int w,int h,float f){
        g.fill(x,y,x+w,y+h,0xff3d4138);g.fill(x,y,x+Math.round(w*Math.clamp(f,0f,1f)),y+h,LIME);
    }
    private void art(GuiGraphics g,String id,int x,int y,int w,int h){
        if(!List.of("forest","industrial","village","underground","quarantine","military").contains(id))id="military";
        var tex=texture(id);
        minecraft.getTextureManager().bindForSetup(tex);minecraft.getTextureManager().getTexture(tex).setFilter(true,false);
        // The six packaged raid illustrations are all 380 x 170. Fit the full image without stretching.
        int iw=w,ih=Math.round(w*170f/380f);if(ih>h){ih=h;iw=Math.round(h*380f/170f);}
        int ix=x+(w-iw)/2,iy=y+(h-ih)/2;
        g.fill(x,y,x+w,y+h,0xff121412);
        g.blit(tex,ix,iy,0,0,iw,ih,iw,ih);g.renderOutline(ix,iy,iw,ih,FRAME);
    }
    // Distinct compact Minecraft-style portraits, drawn at native pixel sizes.
    private void portrait(GuiGraphics g,int i,int x,int y){
        int[] uniforms={0xff555a37,0xffc7ccc6,0xff34394a,0xff8e8067,0xff53544d,0xff594b3d,0xff565a43,0xff22252a,0xff786341,0xff4d6967,0xff665d44};
        int[] hair={0xff4b2025,0xff361d21,0xff302024,0xff9a8059,0xff483a31,0xff4c3426,0xff7a3239,0xff15171a,0xff5d3925,0xffb8b6a3,0xff463d2b};
        int skin=i==7?0xff777470:i==1?0xffdfb69b:0xffc18f6d;
        g.fill(x,y,x+26,y+26,0xff343731);
        g.fill(x+3,y+19,x+23,y+26,uniforms[i]);
        g.fill(x+6,y+3,x+20,y+20,skin);
        g.fill(x+6,y+3,x+20,y+7,hair[i]);
        g.fill(x+6,y+7,x+8,y+12,hair[i]);
        g.fill(x+18,y+7,x+20,y+12,hair[i]);
        if(i!=1&&i!=3&&i!=9){g.fill(x+5,y+2,x+21,y+6,uniforms[i]);g.fill(x+4,y+6,x+22,y+8,uniforms[i]);}
        g.fill(x+9,y+10,x+12,y+12,0xff242725);g.fill(x+15,y+10,x+18,y+12,0xff242725);
        if(i==3||i==4||i==7)g.fill(x+7,y+9,x+19,y+13,0xff242925);
        if(i==0||i==2||i==4||i==6||i==8||i==10){g.fill(x+7,y+16,x+19,y+20,hair[i]);g.fill(x+11,y+15,x+15,y+17,hair[i]);}
        else g.fill(x+11,y+16,x+15,y+17,0xff815b4a);
        if(i==7)g.fill(x+7,y+14,x+19,y+20,uniforms[i]);
        if(i==1){g.fill(x+12,y+20,x+14,y+26,0xffa74440);g.fill(x+10,y+22,x+16,y+24,0xffa74440);}
        g.renderOutline(x,y,26,26,FRAME);
    }
    private void scrollbar(GuiGraphics g,int x,int y,int h,int total,int visible,int offset){
        g.fill(x,y,x+5,y+h,0xff343733);
        int size=Math.min(h,Math.max(14,h*visible/Math.max(1,total)));
        int top=y+(h-size)*offset/Math.max(1,total-visible);
        g.fill(x,top,x+5,top+size,0xff8c8a78);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
        var ids=visible();
        if(initial&&menu.count()>0&&!menu.questStack(selected).isEmpty()){
            scroll=Math.clamp(selected-ROWS/2,0,Math.max(0,ids.size()-ROWS));initial=false;
        }
        scroll=Math.clamp(scroll,0,Math.max(0,ids.size()-ROWS));
        g.pose().pushPose();g.pose().translate(leftPos,topPos,0);g.pose().scale(scale,scale,1);
        minecraft.getTextureManager().bindForSetup(DESK);minecraft.getTextureManager().getTexture(DESK).setFilter(true,false);
        g.blit(DESK,0,0,0,0,W,H,W,H);
        panel(g,8,8,820,49);panel(g,8,64,158,369);panel(g,172,64,302,369);panel(g,480,64,348,369);panel(g,8,439,820,23);
        TacticalSkin.logo(g);
        text(g,"CAMPAGNE  /  "+CampaignTrader.values()[menu.trader()].title,333,21,1.35F,GOLD,true);
        g.fill(20,48,816,49,0xffad343d);
        text(g,"MARCHANDS",17,73,1.15F,GOLD,true);
        for(int i=0;i<CampaignMenu.TRADERS;i++){
            int ry=90+i*30;var s=menu.traderStack(i);
            tile(g,15,ry,144,28,i==menu.trader(),hit(x,y,15,ry,144,28));
            portrait(g,i,18,ry+1);
            text(g,CampaignTrader.values()[i].title,49,ry+4,1,WHITE,true);
            text(g,fit(line(s,0),100,false),49,ry+16,.86F,SOFT,false);
        }
        text(g,"QUÊTES  ·  "+menu.count(),181,73,1.15F,GOLD,true);
        text(g,line(menu.traderStack(menu.trader()),1),337,74,.9F,SOFT,false);
        String[] filters={"Toutes","Disponibles","Actives","Finies","Bloquées"};
        int[] widths={44,67,48,43,58};int fx=180;
        for(int i=0;i<filters.length;i++){button(g,filters[i],fx,91,widths[i],20,x,y,true,filter==i);fx+=widths[i]+3;}
        // Eleven complete rows plus the twelfth compact row fit the scrollable panel.
        for(int row=0;row<ROWS&&row+scroll<ids.size();row++){
            int index=ids.get(row+scroll),ry=116+row*25;var s=menu.questStack(index);String status=line(s,0);
            tile(g,180,ry,282,24,index==selected,hit(x,y,180,ry,282,24));
            item(g,s,184,ry+4,1);
            String prefix=line(s,3).equals("1")?"★ ":"";
            text(g,fit(prefix+name(s),190,false),205,ry+3,1,WHITE,false);
            text(g,status,205,ry+14,.85F,statusColor(status),false);
            if(line(s,3).equals("1"))text(g,"SUIVIE",410,ry+14,.8F,ORANGE,true);
        }
        scrollbar(g,466,116,300,ids.size(),ROWS,scroll);
        if(ids.isEmpty())text(g,"Aucune quête dans ce filtre.",184,127,1,SOFT,false);
        drawDetails(g,x,y);
        button(g,"Retour",15,442,90,17,x,y,true,false);
        button(g,"Réserve",111,442,96,17,x,y,true,false);
        button(g,"Anciennes opérations",213,442,144,17,x,y,true,false);
        button(g,"Offres débloquées",363,442,132,17,x,y,true,false);
        button(g,"Actualiser",501,442,86,17,x,y,true,false);
        text(g,fit(line(menu.inboxStack(),0),219,false),601,447,.95F,SOFT,false);
        g.pose().popPose();
    }
    private record Detail(String label,int color,float progress){}
    private List<Detail> details(ItemStack s){
        var result=new ArrayList<Detail>();
        if(s.isEmpty())return result;
        int n=num(line(s,2));var lore=lore(s);
        if(n>0){
            result.add(new Detail("OBJECTIFS",GOLD,-1));
            for(int i=0;i<n&&6+i<lore.size();i++){
                String str=line(s,6+i);var m=PROGRESS.matcher(str);
                if(m.matches()){
                    int a=num(m.group(2)),b=num(m.group(3));
                    result.add(new Detail(str,a>=b?LIME:WHITE,b>0?a/(float)b:0));
                }else result.add(new Detail(str,WHITE,-1));
            }
        }
        boolean heading=false;
        for(int i=6+n;i<lore.size();i++){
            String str=line(s,i);
            if(!heading&&str.contains(" crédits · ")&&str.contains(" XP · ")){result.add(new Detail("RÉCOMPENSES",GOLD,-1));heading=true;}
            result.add(new Detail(str,SOFT,-1));
        }
        return result;
    }
    private record VisualLine(String s,int color,float progress){}
    private List<VisualLine> visualDetails(ItemStack s){
        var result=new ArrayList<VisualLine>();
        for(var d:details(s)){
            var lines=font.split(styled(d.label(),false),316);
            for(int j=0;j<lines.size();j++) {
                StringBuilder b=new StringBuilder();lines.get(j).accept((index,style,codepoint)->{b.appendCodePoint(codepoint);return true;});
                result.add(new VisualLine(b.toString(),d.color(),j==lines.size()-1?d.progress():-1));
            }
        }
        return result;
    }
    private void drawDetails(GuiGraphics g,double mx,double my){
        var s=quest();if(s.isEmpty()){text(g,"Sélectionne une quête",491,80,1.2F,SOFT,true);return;}
        String status=line(s,0);
        text(g,fit(name(s),250,true),491,75,1.24F,WHITE,true);
        text(g,status,491,93,1.04F,statusColor(status),true);
        text(g,fit(line(s,5),310,false),491,107,.95F,SOFT,false);
        art(g,line(s,4),491,123,326,108);
        var lines=visualDetails(s);int total=lines.size();
        var contents=s.get(DataComponents.CONTAINER);
        var rewards=contents==null?List.<ItemStack>of():contents.nonEmptyStream().toList();
        int rewardRows=(rewards.size()+5)/6;
        int visibleLines=rewardRows>0?5:10;
        int max=Math.max(0,total-visibleLines);
        detailScroll=Math.clamp(detailScroll,0,max);
        int ry=DETAIL_Y;
        for(int i=detailScroll;i<Math.min(total,detailScroll+visibleLines);i++){
            var l=lines.get(i);
            text(g,l.s(),492,ry,1,l.color(),l.color()==GOLD);
            if(l.progress()>=0)bar(g,492,ry+11,313,3,l.progress());
            ry+=15;
        }
        scrollbar(g,812,DETAIL_Y,visibleLines*15,total,visibleLines,detailScroll);
        if(rewardRows>0){
            text(g,"OBJETS DE RÉCOMPENSE",491,319,1,GOLD,true);
            int maxIcons=Math.min(12,rewards.size());
            for(int i=0;i<maxIcons;i++){
                var reward=rewards.get(i);int ix=492+(i%6)*53,iy=332+(i/6)*28;
                tile(g,ix,iy,50,26,false,hit(mx,my,ix,iy,50,26));
                item(g,reward,ix+3,iy+4,1);
                text(g,"×"+line(reward,0),ix+22,iy+9,.85F,WHITE,false);
                if(hit(mx,my,ix,iy,50,26)){
                    String caption=fit(name(reward)+" ×"+line(reward,0),315,false);
                    g.fill(490,389,818,402,0xff22231f);text(g,caption,494,391,1,WHITE,false);
                }
            }
            if(rewards.size()>12)text(g,"+"+(rewards.size()-12)+" autres objets",492,389,.85F,SOFT,false);
        }
        boolean accept=status.equals("Disponible"),claim=status.equals("À valider"),follow=status.equals("En cours")||claim;
        button(g,"Accepter",491,405,102,22,mx,my,accept,accept);
        button(g,"Terminer",599,405,102,22,mx,my,claim,claim);
        button(g,line(s,3).equals("1")?"Ne plus suivre":"Suivre",707,405,110,22,mx,my,follow,line(s,3).equals("1"));
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new fr.taczoperations.operations.CampaignAction(menu.containerId,action));}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;
        for(int i=0;i<CampaignMenu.TRADERS;i++)if(hit(x,y,15,90+i*30,144,28)){if(i!=menu.trader())send(i);return true;}
        int[] widths={44,67,48,43,58};int fx=180;
        for(int i=0;i<widths.length;i++){if(hit(x,y,fx,91,widths[i],20)){filter=i;scroll=0;var ids=visible();if(!ids.contains(selected)&&!ids.isEmpty()){selected=ids.getFirst();detailScroll=0;}return true;}fx+=widths[i]+3;}
        var ids=visible();
        for(int row=0;row<ROWS&&row+scroll<ids.size();row++)if(hit(x,y,180,116+row*25,282,24)){selected=ids.get(row+scroll);detailScroll=0;return true;}
        if(hit(x,y,465,116,8,300)){dragList=true;seekList(y);return true;}
        if(hit(x,y,810,DETAIL_Y,9,150)){dragDetail=true;seekDetail(y);return true;}
        String status=line(quest(),0);
        if(hit(x,y,491,405,102,22)){if(status.equals("Disponible"))send(CampaignMenu.ACCEPT+selected);return true;}
        if(hit(x,y,599,405,102,22)){if(status.equals("À valider"))send(CampaignMenu.CLAIM+selected);return true;}
        if(hit(x,y,707,405,110,22)){if(status.equals("En cours")||status.equals("À valider"))send(CampaignMenu.FOLLOW+selected);return true;}
        int[] xs={15,111,213,363,501},ws={90,96,144,132,86},actions={CampaignMenu.BACK,CampaignMenu.COLLECT,CampaignMenu.LEGACY,CampaignMenu.SHOP,CampaignMenu.REFRESH};
        for(int i=0;i<xs.length;i++)if(hit(x,y,xs[i],442,ws[i],17)){send(actions[i]);return true;}
        return true;
    }
    private void seekList(double y){scroll=(int)Math.round(Math.clamp((y-116)/300,0d,1d)*Math.max(0,visible().size()-ROWS));}
    private int detailMax(){
        var s=quest();var contents=s.get(DataComponents.CONTAINER);
        return Math.max(0,visualDetails(s).size()-(contents==null?10:5));
    }
    private void seekDetail(double y){detailScroll=(int)Math.round(Math.clamp((y-DETAIL_Y)/150,0d,1d)*detailMax());}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        double y=(my-topPos)/scale;
        if(dragList){seekList(y);return true;}
        if(dragDetail){seekDetail(y);return true;}
        return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(dragList||dragDetail){dragList=false;dragDetail=false;return true;}return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        double x=(mx-leftPos)/scale,y=(my-topPos)/scale;if(sy==0)return false;
        int delta=sy>0?-1:1;
        if(hit(x,y,172,64,302,369)){scroll=Math.clamp(scroll+delta,0,Math.max(0,visible().size()-ROWS));return true;}
        if(hit(x,y,480,64,348,369)){detailScroll=Math.clamp(detailScroll+delta,0,detailMax());return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }
}
