package fr.taczoperations.client;

import fr.taczoperations.campaign.CampaignTrader;
import fr.taczoperations.operations.CampaignTabData;
import fr.taczoperations.operations.CampaignTabRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/** Local page inside the original EFM operations screen. */
final class CampaignDashboard {
    private static final int GOLD=0xffe5ce88,WHITE=0xfff5f3e8,SOFT=0xffbfc8b8,OLIVE=0xffa4c67c,ORANGE=0xffe3ab6e,FRAME=0xff716751;
    private static final ResourceLocation FONT=ResourceLocation.fromNamespaceAndPath("taczoperations","interface"),HEAD=ResourceLocation.fromNamespaceAndPath("taczoperations","heading");
    private static final Pattern PROGRESS=Pattern.compile("^(.*) : (\\d+)/(\\d+)$");
    private static final Item[] ICONS={Items.CROSSBOW,Items.GOLDEN_APPLE,Items.IRON_NUGGET,Items.GOLD_INGOT,Items.ANVIL,Items.LEATHER_CHESTPLATE,Items.COMPASS,Items.ENDER_CHEST,Items.TARGET,Items.LANTERN,Items.MINECART};
    private static final int ROWS=10, LIST_X=169,LIST_Y=178,LIST_W=272,ROW=20,DETAIL_X=455,DETAIL_Y=245,DETAIL_W=361,DETAIL_ROWS=9;
    private final int menuId;
    private final HashMap<Integer,CampaignTabData> cache=new HashMap<>();
    private int trader,filter,scroll,detailScroll;
    private String selected="";
    private boolean waiting,changed,dragList,dragDetail;
    private long askedAt;
    private CampaignTabData last;
    private String message="";
    CampaignDashboard(int menuId){this.menuId=menuId;}
    public static void handle(CampaignTabData payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(mc.screen instanceof OperationsScreen screen)screen.receiveCampaign(payload);
        });
    }
    void enter(){if(!cache.containsKey(trader)&&!waiting)request(0,"");}
    boolean changed(){return changed;}
    CampaignTabData latest(){return last;}
    private CampaignTabData data(){return cache.get(trader);}
    private void request(int action,String id){
        waiting=true;askedAt=System.nanoTime();message="";
        if(action>=1&&action<=4){changed=true;cache.clear();}
        PacketDistributor.sendToServer(new CampaignTabRequest(menuId,trader,action,id));
    }
    void receive(CampaignTabData data){
        if(data.menuId()!=menuId)return;
        cache.put(data.trader(),data);last=data;
        if(data.trader()==trader){waiting=false;message=data.notice();ensureSelection();}
    }
    private List<CampaignTabData.Quest> visible(){
        var d=data();if(d==null)return List.of();
        return d.quests().stream().filter(q->switch(filter){
            case 1->q.status().equals("Disponible");
            case 2->q.status().equals("En cours")||q.status().equals("À valider");
            case 3->q.status().equals("Terminée");
            case 4->q.status().equals("Verrouillée")||q.status().equals("Non jouable")||q.status().equals("Échouée");
            default->true;
        }).toList();
    }
    private void ensureSelection(){
        var list=visible();
        if(list.stream().noneMatch(q->q.id().equals(selected))){selected=list.isEmpty()?"":list.getFirst().id();detailScroll=0;}
        scroll=Math.clamp(scroll,0,Math.max(0,list.size()-ROWS));
    }
    private CampaignTabData.Quest current(){var d=data();return d==null?null:d.quests().stream().filter(q->q.id().equals(selected)).findFirst().orElse(null);}
    private static boolean hit(double x,double y,int l,int t,int w,int h){return x>=l&&x<l+w&&y>=t&&y<t+h;}
    private static Component styled(String s,boolean bold){return Component.literal(s).withStyle(Style.EMPTY.withFont(bold?HEAD:FONT));}
    private static net.minecraft.client.gui.Font font(){return Minecraft.getInstance().font;}
    private static String fit(String s,int w,boolean bold){
        if(font().width(styled(s,bold))<=w)return s;
        while(!s.isEmpty()&&font().width(styled(s+"…",bold))>w)s=s.substring(0,s.length()-1);
        return s+"…";
    }
    private static void text(GuiGraphics g,String s,int x,int y,float scale,int color,boolean bold){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.drawString(font(),styled(s,bold),0,0,color,false);g.pose().popPose();
    }
    private static void tile(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hover){
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,selected?0xff4a5730:hover?0xff374432:0xff2b3328,0xff171d16);
        g.fill(x+2,y,x+w-2,y+1,selected?GOLD:FRAME);g.fill(x+2,y+h-1,x+w-2,y+h,selected?GOLD:FRAME);
        g.fill(x,y+2,x+1,y+h-2,selected?GOLD:FRAME);g.fill(x+w-1,y+2,x+w,y+h-2,selected?GOLD:FRAME);
    }
    private static void button(GuiGraphics g,String s,int x,int y,int w,int h,double mx,double my,boolean enabled,boolean selected){
        tile(g,x,y,w,h,enabled&&selected,enabled&&hit(mx,my,x,y,w,h));
        text(g,s,x+(w-font().width(styled(s,true)))/2,y+(h-8)/2,1,enabled?WHITE:0xff777d72,true);
    }
    private static void item(GuiGraphics g,ItemStack s,int x,int y,float scale){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(s,0,0);g.pose().popPose();
    }
    private static int status(String s){return switch(s){
        case "Disponible"->GOLD;case "En cours"->ORANGE;case "À valider","Terminée"->OLIVE;case "Échouée"->0xffdf8b80;default->SOFT;
    };}
    private static void scrollBar(GuiGraphics g,int x,int y,int h,int total,int rows,int offset){
        g.fill(x,y,x+4,y+h,0xff343b30);int size=Math.min(h,Math.max(12,h*rows/Math.max(1,total)));
        int top=y+(h-size)*offset/Math.max(1,total-rows);g.fill(x,top,x+4,top+size,0xff8c9475);
    }
    private static void art(GuiGraphics g,String id,int x,int y,int w,int h){
        if(!List.of("forest","industrial","village","underground","quarantine","military").contains(id))id="military";
        var tex=ResourceLocation.fromNamespaceAndPath("taczoperations","textures/gui/"+id+".png");
        var textures=Minecraft.getInstance().getTextureManager();textures.bindForSetup(tex);textures.getTexture(tex).setFilter(true,false);
        g.blit(tex,x,y,0,0,w,h,w,h);g.renderOutline(x,y,w,h,FRAME);
    }
    private int mx(int i){return 169+(i%6)*108;}
    private int my(int i){return 108+(i/6)*22;}
    void draw(GuiGraphics g,double x,double y){
        if(waiting&&System.nanoTime()-askedAt>4_000_000_000L){waiting=false;message="Actualise pour relancer le chargement.";}
        text(g,"CAMPAGNE  /  QUÊTES",170,82,1.5F,WHITE,true);
        text(g,"Contrats des marchands",565,86,1,SOFT,false);
        var loyalties=last==null?List.<Integer>of():last.loyalties();
        for(int i=0;i<CampaignTrader.values().length;i++){
            int bx=mx(i),by=my(i);
            tile(g,bx,by,104,20,i==trader,hit(x,y,bx,by,104,20));
            item(g,new ItemStack(ICONS[i]),bx+3,by+3,.8F);
            text(g,fit(CampaignTrader.values()[i].title,75,true),bx+19,by+3,.95F,WHITE,true);
            if(i<loyalties.size())text(g,loyalties.get(i)==0?"Verrouillé":"Fidélité "+loyalties.get(i),bx+19,by+12,.68F,loyalties.get(i)==0?SOFT:GOLD,false);
        }
        g.fill(167,152,820,153,FRAME);
        var d=data();
        String[] names={"Toutes","Disponibles","Actives","Finies","Bloquées"};
        int[] widths={44,64,46,43,60};int fx=169;
        for(int i=0;i<names.length;i++){button(g,names[i],fx,157,widths[i],17,x,y,true,filter==i);fx+=widths[i]+3;}
        if(d==null){
            text(g,waiting?"Chargement des quêtes…":"Aucune donnée reçue.",173,188,1.1F,SOFT,false);
        }else{
            ensureSelection();var list=visible();
            for(int i=0;i<ROWS&&i+scroll<list.size();i++){
                var q=list.get(i+scroll);int ry=LIST_Y+i*ROW;
                tile(g,LIST_X,ry,LIST_W-7,ROW-2,q.id().equals(selected),hit(x,y,LIST_X,ry,LIST_W-7,ROW-2));
                int c=status(q.status());g.fill(LIST_X+2,ry+3,LIST_X+4,ry+ROW-5,c);
                text(g,fit((q.followed()?"★ ":"")+q.name(),205,false),LIST_X+8,ry+3,1,WHITE,false);
                text(g,q.status(),LIST_X+8,ry+12,.66F,c,false);
            }
            scrollBar(g,435,LIST_Y,ROWS*ROW,list.size(),ROWS,scroll);
            if(list.isEmpty())text(g,"Aucune quête pour ce filtre.",175,185,1,SOFT,false);
            long done=d.quests().stream().filter(q->q.status().equals("Terminée")).count();
            long playable=d.quests().stream().filter(CampaignTabData.Quest::playable).count();
            text(g,done+"/"+playable+" terminées  ·  "+d.quests().size()+" au catalogue",169,382,.9F,SOFT,false);
        }
        g.fill(447,157,448,395,FRAME);
        drawQuest(g,x,y);
        button(g,"Réserve",169,402,83,22,x,y,!waiting,false);
        button(g,"Offres débloquées",258,402,122,22,x,y,!waiting,false);
        button(g,"Actualiser",386,402,69,22,x,y,!waiting,false);
        if(last!=null)text(g,last.inbox()+" objet(s) en réserve",169,432,.9F,SOFT,false);
        if(!message.isEmpty())text(g,fit(message,350,false),460,432,.95F,GOLD,false);
    }
    private record Detail(String label,int color,float progress){}
    private List<Detail> details(CampaignTabData.Quest q){
        var result=new ArrayList<Detail>();
        if(q==null)return result;
        if(q.objectives()>0)result.add(new Detail("OBJECTIFS",GOLD,-1));
        boolean reward=false;
        for(int i=1;i<q.lines().size();i++){
            String line=q.lines().get(i);var m=PROGRESS.matcher(line);
            if(i<=q.objectives()&&m.matches()){
                try{int a=Integer.parseInt(m.group(2)),b=Integer.parseInt(m.group(3));
                    result.add(new Detail(line,a>=b?OLIVE:WHITE,b>0?a/(float)b:0));continue;
                }catch(NumberFormatException ignored){}
            }
            if(!reward&&line.contains(" crédits · ")&&line.contains(" XP · ")){result.add(new Detail("RÉCOMPENSES",GOLD,-1));reward=true;}
            result.add(new Detail(line,SOFT,-1));
        }
        for(var stack:q.rewards()){
            var lore=stack.get(DataComponents.LORE);String count=lore==null||lore.lines().isEmpty()?"":lore.lines().getFirst().getString();
            result.add(new Detail("×"+count+" "+stack.getHoverName().getString(),SOFT,-1));
        }
        return result;
    }
    private List<Detail> wrapped(CampaignTabData.Quest q){
        var result=new ArrayList<Detail>();
        for(var line:details(q)){
            var split=font().split(styled(line.label(),false),DETAIL_W-10);
            for(int j=0;j<split.size();j++){
                var out=new StringBuilder();split.get(j).accept((idx,st,cp)->{out.appendCodePoint(cp);return true;});
                result.add(new Detail(out.toString(),line.color(),j==split.size()-1?line.progress():-1));
            }
        }
        return result;
    }
    private void drawQuest(GuiGraphics g,double x,double y){
        var q=current();
        if(q==null){text(g,"Sélectionne une quête",460,170,1.1F,SOFT,true);return;}
        text(g,fit(q.name(),355,true),460,160,1.12F,WHITE,true);
        text(g,q.status(),460,175,1,status(q.status()),true);
        text(g,fit(q.lines().isEmpty()?"":q.lines().getFirst(),355,false),460,190,.9F,SOFT,false);
        art(g,q.art(),460,204,86,38);
        text(g,"RAVITAILLEMENT",557,205,.88F,GOLD,true);
        int limit=Math.min(8,q.rewards().size());
        for(int i=0;i<limit;i++){
            var stack=q.rewards().get(i);int ix=557+i*29;
            tile(g,ix,218,25,24,false,hit(x,y,ix,218,25,24));item(g,stack,ix+4,221,1);
        }
        if(limit==0)text(g,"Récompenses ci-dessous",557,220,.9F,SOFT,false);
        var lines=wrapped(q);int max=Math.max(0,lines.size()-DETAIL_ROWS);
        detailScroll=Math.clamp(detailScroll,0,max);
        for(int i=detailScroll;i<Math.min(lines.size(),detailScroll+DETAIL_ROWS);i++){
            var l=lines.get(i);int ry=DETAIL_Y+(i-detailScroll)*16;
            text(g,l.label(),460,ry,1,l.color(),l.color()==GOLD);
            if(l.progress()>=0){
                g.fill(460,ry+11,805,ry+14,0xff404b39);
                g.fill(460,ry+11,460+(int)(345*Math.clamp(l.progress(),0f,1f)),ry+14,OLIVE);
            }
        }
        scrollBar(g,812,DETAIL_Y,DETAIL_ROWS*16,lines.size(),DETAIL_ROWS,detailScroll);
        button(g,"Accepter",460,402,112,22,x,y,!waiting&&q.status().equals("Disponible"),true);
        button(g,"Terminer",577,402,112,22,x,y,!waiting&&q.status().equals("À valider"),true);
        button(g,q.followed()?"Ne plus suivre":"Suivre",694,402,122,22,x,y,!waiting&&(q.status().equals("En cours")||q.status().equals("À valider")),q.followed());
    }
    boolean click(double x,double y){
        if(x<157)return false;
        for(int i=0;i<CampaignTrader.values().length;i++)if(hit(x,y,mx(i),my(i),104,20)){
            if(i!=trader&&!waiting){trader=i;scroll=detailScroll=0;selected="";message="";if(cache.containsKey(i))ensureSelection();else request(0,"");}return true;
        }
        int[] widths={44,64,46,43,60};int fx=169;
        for(int i=0;i<widths.length;i++){if(hit(x,y,fx,157,widths[i],17)){filter=i;scroll=0;ensureSelection();return true;}fx+=widths[i]+3;}
        var list=visible();
        if(hit(x,y,433,LIST_Y,9,ROWS*ROW)){dragList=true;seekList(y);return true;}
        if(hit(x,y,810,DETAIL_Y,9,DETAIL_ROWS*16)){dragDetail=true;seekDetail(y);return true;}
        if(hit(x,y,LIST_X,LIST_Y,LIST_W-8,ROWS*ROW)){
            int index=scroll+(int)(y-LIST_Y)/ROW;if(index<list.size()){selected=list.get(index).id();detailScroll=0;}return true;
        }
        if(waiting)return true;
        var q=current();
        if(hit(x,y,169,402,83,22)){request(4,"");return true;}
        if(hit(x,y,258,402,122,22)){request(5,"");return true;}
        if(hit(x,y,386,402,69,22)){cache.clear();request(0,"");return true;}
        if(q!=null){
            if(hit(x,y,460,402,112,22)&&q.status().equals("Disponible"))request(1,q.id());
            else if(hit(x,y,577,402,112,22)&&q.status().equals("À valider"))request(2,q.id());
            else if(hit(x,y,694,402,122,22)&&(q.status().equals("En cours")||q.status().equals("À valider")))request(3,q.id());
        }
        return true;
    }
    boolean scroll(double x,double y,double sy){
        if(sy==0)return false;int delta=sy>0?-1:1;
        if(hit(x,y,169,LIST_Y,272,ROWS*ROW)){scroll=Math.clamp(scroll+delta,0,Math.max(0,visible().size()-ROWS));return true;}
        if(hit(x,y,455,DETAIL_Y,361,DETAIL_ROWS*16)){detailScroll=Math.clamp(detailScroll+delta,0,Math.max(0,wrapped(current()).size()-DETAIL_ROWS));return true;}
        return false;
    }
    private void seekList(double y){scroll=(int)Math.round(Math.clamp((y-LIST_Y)/(ROWS*(double)ROW),0d,1d)*Math.max(0,visible().size()-ROWS));}
    private void seekDetail(double y){detailScroll=(int)Math.round(Math.clamp((y-DETAIL_Y)/(DETAIL_ROWS*16d),0d,1d)*Math.max(0,wrapped(current()).size()-DETAIL_ROWS));}
    boolean drag(double y){if(dragList){seekList(y);return true;}if(dragDetail){seekDetail(y);return true;}return false;}
    boolean release(){boolean was=dragList||dragDetail;dragList=dragDetail=false;return was;}
}
