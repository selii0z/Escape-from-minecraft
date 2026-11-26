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
public final class CampaignDashboard {
    private static final int GOLD=0xffe6d9c4,WHITE=0xfff5f3e8,SOFT=0xffbdb9b2,OLIVE=0xffb4ca77,ORANGE=0xffee9e43,FRAME=0xff625d55;
    private static final ResourceLocation FONT=ResourceLocation.fromNamespaceAndPath("taczoperations","interface"),HEAD=ResourceLocation.fromNamespaceAndPath("taczoperations","heading");
    private static final Pattern PROGRESS=Pattern.compile("^(.*) : (\\d+)/(\\d+)$");
    private static final Item[] ICONS={Items.CROSSBOW,Items.GOLDEN_APPLE,Items.IRON_NUGGET,Items.GOLD_INGOT,Items.ANVIL,Items.LEATHER_CHESTPLATE,Items.COMPASS,Items.ENDER_CHEST,Items.TARGET,Items.LANTERN,Items.MINECART};
    private static final int ROWS=7, LIST_X=167,LIST_Y=178,LIST_W=292,ROW=31,DETAIL_X=480,DETAIL_Y=259,DETAIL_W=330,DETAIL_ROWS=4;
    private static final String[] FILTERS={"Toutes","Disponibles","En cours","Terminées","Verrouillées"};
    private static final int[] FILTER_W={43,58,49,52,68};
    private static final int TRADER_W=90, TRADER_STEP=94, TRADER_VISIBLE=7;
    private final int menuId;
    private final HashMap<Integer,CampaignTabData> cache=new HashMap<>();
    private int trader,filter,scroll,detailScroll,traderStart,rewardScroll;
    private String selected="";
    private boolean waiting,changed,dragList,dragDetail;
    private long askedAt;
    private CampaignTabData last;
    private String message="";
    private CampaignTabData.Quest detailCacheKey;
    private List<Detail> detailCache=List.of();
    private CampaignTabData visibleCacheKey;
    private int visibleCacheFilter=-1;
    private List<CampaignTabData.Quest> visibleCache=List.of();
    CampaignDashboard(int menuId){this.menuId=menuId;}
    public static void handle(CampaignTabData payload,IPayloadContext context){
        context.enqueueWork(()->{
            var mc=Minecraft.getInstance();
            if(mc.screen instanceof OperationsScreen screen)screen.receiveCampaign(payload);
        });
    }
    void enter(){if(!cache.containsKey(trader)&&!waiting)request(0,"");}
    boolean changed(){return changed;}
    String merchant(){return CampaignTrader.values()[trader].title;}
    CampaignTabData latest(){return last;}
    private CampaignTabData data(){return cache.get(trader);}
    private void request(int action,String id){
        waiting=true;askedAt=System.nanoTime();message="";
        if(action>=1&&action<=4){changed=true;cache.keySet().removeIf(key->key!=trader);}
        PacketDistributor.sendToServer(new CampaignTabRequest(menuId,trader,action,id));
    }
    void receive(CampaignTabData data){
        if(data.menuId()!=menuId)return;
        cache.put(data.trader(),data);last=data;
        if(data.trader()==trader){waiting=false;message=data.notice();ensureSelection();}
    }
    private List<CampaignTabData.Quest> visible(){
        var d=data();if(d==null)return List.of();
        if(d==visibleCacheKey&&filter==visibleCacheFilter)return visibleCache;
        visibleCacheKey=d;visibleCacheFilter=filter;
        visibleCache=d.quests().stream().filter(q->switch(filter){
            case 1->q.status().equals("Disponible");
            case 2->q.status().equals("En cours")||q.status().equals("À valider");
            case 3->q.status().equals("Terminée");
            case 4->q.status().equals("Verrouillée")||q.status().equals("Non jouable")||q.status().equals("Échouée");
            default->true;
        }).toList();
        return visibleCache;
    }
    private void ensureSelection(){
        var list=visible();
        if(list.stream().noneMatch(q->q.id().equals(selected))){selected=list.isEmpty()?"":list.getFirst().id();detailScroll=rewardScroll=0;}
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
    private static void tile(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hover){TacticalSkin.tile(g,x,y,w,h,selected,hover);}
    private static void button(GuiGraphics g,String s,int x,int y,int w,int h,double mx,double my,boolean enabled,boolean selected){
        tile(g,x,y,w,h,enabled&&selected,enabled&&hit(mx,my,x,y,w,h));
        text(g,s,x+(w-font().width(styled(s,true)))/2,y+(h-8)/2,1,enabled?WHITE:0xff777777,true);
    }
    private static void accentButton(GuiGraphics g,String s,int x,int y,int w,int h,double mx,double my,boolean enabled,int accent){
        tile(g,x,y,w,h,enabled&&accent==ORANGE,enabled&&hit(mx,my,x,y,w,h));
        if(enabled){g.fillGradient(x+2,y+2,x+w-2,y+h-2,accent==ORANGE?0xff54391f:0xff3d4428,0xff1d2017);TacticalSkin.outline(g,x,y,w,h,accent);}
        text(g,s,x+(w-font().width(styled(s,true)))/2,y+(h-8)/2,1,enabled?accent:0xff777777,true);
    }
    private static void badge(GuiGraphics g,String s,int x,int y,int w,int h){
        int c=status(s);TacticalSkin.cut(g,x,y,w,h,0xff242422);TacticalSkin.outline(g,x,y,w,h,c);
        text(g,s,x+(w-Math.round(font().width(styled(s,false))*.85f))/2,y+(h-7)/2,.85f,c,false);
    }
    private static void section(GuiGraphics g,String s,int x,int y,int w){
        text(g,s,x,y,1.08f,GOLD,true);g.fill(x,y+12,x+w,y+13,0xff655340);TacticalSkin.stripes(g,x+w-40,y+3,0xffa6642d);
    }
    private static ItemStack questIcon(CampaignTabData.Quest q){
        return new ItemStack(switch(q.status()){case "Disponible"->Items.WRITABLE_BOOK;case "En cours"->Items.PAPER;case "À valider","Terminée"->Items.LIME_DYE;case "Échouée"->Items.BARRIER;default->Items.GRAY_DYE;});
    }
    private static void item(GuiGraphics g,ItemStack s,int x,int y,float scale){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(s,0,0);g.pose().popPose();
    }
    private static int status(String s){return switch(s){
        case "Disponible"->OLIVE;case "En cours"->ORANGE;case "À valider","Terminée"->OLIVE;case "Échouée"->0xffdf8b80;default->SOFT;
    };}
    private static void scrollBar(GuiGraphics g,int x,int y,int h,int total,int rows,int offset){
        g.fill(x,y,x+4,y+h,0xff343434);int size=Math.min(h,Math.max(12,h*rows/Math.max(1,total)));
        int top=y+(h-size)*offset/Math.max(1,total-rows);g.fill(x,top,x+4,top+size,0xff99958d);
    }
    private static void art(GuiGraphics g,String id,int x,int y,int w,int h){
        if(!List.of("forest","industrial","village","underground","quarantine","military").contains(id))id="military";
        var tex=ResourceLocation.fromNamespaceAndPath("taczoperations","textures/gui/"+id+".png");
        var textures=Minecraft.getInstance().getTextureManager();textures.bindForSetup(tex);textures.getTexture(tex).setFilter(true,false);
        // Center crop from the original 380x170 illustration; never stretch.
        int sourceH=Math.min(170,Math.round(380f*h/w));
        g.blit(tex,x,y,w,h,0,(170-sourceH)/2,380,sourceH,380,170);
        TacticalSkin.outline(g,x,y,w,h,0xff8a8070);
    }
    private int mx(int i){return 169+(i-traderStart)*TRADER_STEP;}
    private void shiftTraders(int delta){traderStart=Math.clamp(traderStart+delta,0,Math.max(0,CampaignTrader.values().length-TRADER_VISIBLE));}
    void draw(GuiGraphics g,double x,double y){
        if(waiting&&System.nanoTime()-askedAt>10_000_000_000L){waiting=false;message="Actualise pour relancer le chargement.";}
        TacticalSkin.panel(g,157,65,667,43);TacticalSkin.panel(g,157,112,305,326);TacticalSkin.panel(g,467,112,357,326);
        var loyalties=last==null?List.<Integer>of():last.loyalties();
        int end=Math.min(CampaignTrader.values().length,traderStart+TRADER_VISIBLE);
        for(int i=traderStart;i<end;i++){
            int bx=mx(i);
            tile(g,bx,70,TRADER_W,33,i==trader,hit(x,y,bx,70,TRADER_W,33));
            if(!TacticalSkin.portrait(g,i,bx+4,74,25))item(g,new ItemStack(ICONS[i]),bx+6,76,1.25f);
            text(g,fit(CampaignTrader.values()[i].title,69,true),bx+31,77,.86f,WHITE,true);
            if(i<loyalties.size())text(g,loyalties.get(i)==0?"Verrouillé":"Fidélité "+loyalties.get(i),bx+31,89,.70f,loyalties.get(i)==0?SOFT:ORANGE,false);
        }
        // The entire merchant list is available with the mouse wheel; compact arrows overlay the outer gutter.
        if(traderStart>0)text(g,"‹",160,82,1.05f,ORANGE,true);
        if(end<CampaignTrader.values().length)text(g,"›",817,82,1.05f,ORANGE,true);
        text(g,"Quêtes de "+merchant(),169,121,1.35f,GOLD,true);
        var d=data();
        if(d!=null){long done=d.quests().stream().filter(q->q.status().equals("Terminée")).count();
            String count=done+" / "+d.quests().size();text(g,count,449-font().width(styled(count,false)),124,1,SOFT,false);}
        g.fill(168,140,450,141,FRAME);
        int fx=167;
        for(int i=0;i<FILTERS.length;i++){button(g,FILTERS[i],fx,148,FILTER_W[i],22,x,y,true,filter==i);fx+=FILTER_W[i]+3;}
        if(d==null){text(g,waiting?"Chargement des quêtes…":"Aucune donnée reçue.",173,188,1.1F,SOFT,false);}
        else{
            ensureSelection();var list=visible();
            for(int i=0;i<ROWS&&i+scroll<list.size();i++){
                var q=list.get(i+scroll);int ry=LIST_Y+i*ROW;
                tile(g,LIST_X,ry,LIST_W-12,ROW-3,q.id().equals(selected),hit(x,y,LIST_X,ry,LIST_W-12,ROW-3));
                item(g,questIcon(q),LIST_X+5,ry+5,1.12f);
                text(g,fit((q.followed()?"★ ":"")+q.name(),177,false),LIST_X+28,ry+10,1,WHITE,false);
                badge(g,q.status(),LIST_X+205,ry+6,70,16);
            }
            scrollBar(g,453,LIST_Y,ROWS*ROW-3,list.size(),ROWS,scroll);
            if(list.isEmpty())text(g,"Aucune quête pour ce filtre.",175,185,1,SOFT,false);
        }
        drawQuest(g,x,y);
        button(g,"Réserve",168,408,68,22,x,y,!waiting,false);
        button(g,"Offres débloquées",240,408,122,22,x,y,!waiting,false);
        button(g,"Actualiser",366,408,83,22,x,y,!waiting,false);
        if(last!=null)text(g,last.inbox()+" objet(s) en réserve",168,443,.9F,SOFT,false);
        if(waiting)text(g,"Synchronisation…",480,443,.95F,ORANGE,false);else if(!message.isEmpty())text(g,fit(message,330,false),480,443,.95F,GOLD,false);
    }
    private record Detail(String label,int color,float progress){}
    private static String[] rewardStats(CampaignTabData.Quest q){
        for(String line:q.lines())if(line.contains(" crédits · ")&&line.contains(" XP · "))return line.split(" · ",3);
        return new String[]{"— crédits","— XP","— réputation"};
    }
    private List<Detail> details(CampaignTabData.Quest q){
        var result=new ArrayList<Detail>();if(q==null)return result;
        for(int i=1;i<q.lines().size();i++){
            String line=q.lines().get(i);if(line.contains(" crédits · ")&&line.contains(" XP · "))continue;
            var m=PROGRESS.matcher(line);
            if(i<=q.objectives()&&m.matches()){
                try{int a=Integer.parseInt(m.group(2)),b=Integer.parseInt(m.group(3));
                    result.add(new Detail(line,a>=b?OLIVE:WHITE,b>0?a/(float)b:0));continue;
                }catch(NumberFormatException ignored){}
            }
            result.add(new Detail(line,SOFT,-1));
        }
        for(var stack:q.rewards()){
            var lore=stack.get(DataComponents.LORE);String count=lore==null||lore.lines().isEmpty()?"":lore.lines().getFirst().getString();
            result.add(new Detail("×"+count+" "+stack.getHoverName().getString(),SOFT,-1));
        }
        return result;
    }
    private List<Detail> wrapped(CampaignTabData.Quest q){
        if(q==detailCacheKey)return detailCache;
        var result=new ArrayList<Detail>();
        for(var line:details(q)){
            var split=font().split(styled(line.label(),false),DETAIL_W-10);
            for(int j=0;j<split.size();j++){
                var out=new StringBuilder();split.get(j).accept((idx,st,cp)->{out.appendCodePoint(cp);return true;});
                result.add(new Detail(out.toString(),line.color(),j==split.size()-1?line.progress():-1));
            }
        }
        detailCacheKey=q;detailCache=List.copyOf(result);return detailCache;
    }
    private void drawQuest(GuiGraphics g,double x,double y){
        var q=current();
        if(q==null){text(g,"Sélectionne une quête",480,170,1.1F,SOFT,true);return;}
        tile(g,478,119,24,24,false,false);item(g,questIcon(q),482,123,1);
        text(g,fit(q.name(),230,true),508,119,1.35F,WHITE,true);
        text(g,fit(q.lines().isEmpty()?q.status():q.lines().getFirst(),300,false),508,135,.91F,SOFT,false);
        TacticalSkin.stripes(g,774,121,0xffa7612b);
        art(g,q.art(),478,151,335,77);
        String state=q.followed()?"★ Quête suivie en raid":q.status();
        text(g,state,480,232,.94F,status(q.status()),false);
        section(g,"⊕  Objectifs",480,244,332);
        var lines=wrapped(q);int max=Math.max(0,lines.size()-DETAIL_ROWS);
        detailScroll=Math.clamp(detailScroll,0,max);
        for(int i=detailScroll;i<Math.min(lines.size(),detailScroll+DETAIL_ROWS);i++){
            var l=lines.get(i);int ry=DETAIL_Y+(i-detailScroll)*15;
            if(l.progress()>=0){
                TacticalSkin.outline(g,480,ry,9,9,l.color());
                if(l.progress()>=1)text(g,"✓",481,ry,1,OLIVE,true);
                text(g,fit(l.label(),206,false),494,ry,1,l.color(),false);
                g.fill(708,ry+2,805,ry+8,0xff171717);TacticalSkin.outline(g,708,ry+2,97,6,0xff72695b);
                g.fillGradient(710,ry+3,710+(int)(93*Math.clamp(l.progress(),0f,1f)),ry+7,0xfff9bb73,ORANGE);
            }else text(g,l.label(),480,ry,.95F,l.color(),false);
        }
        if(max>0)scrollBar(g,816,DETAIL_Y,DETAIL_ROWS*15,lines.size(),DETAIL_ROWS,detailScroll);
        section(g,"✦  Récompenses",480,320,332);
        String[] stats=rewardStats(q);
        for(int i=0;i<3;i++){int bx=478+i*112;tile(g,bx,337,108,23,false,false);
            if(i==0)item(g,new ItemStack(Items.IRON_INGOT),bx+4,341,.9f);
            else text(g,i==1?"XP":"✦",bx+5,345,1.05f,ORANGE,true);
            text(g,fit(stats[i],86,false),bx+22,345,.9f,WHITE,false);}
        int total=q.rewards().size();rewardScroll=Math.clamp(rewardScroll,0,Math.max(0,total-4));
        for(int i=0;i<Math.min(4,total-rewardScroll);i++){
            var stack=q.rewards().get(i+rewardScroll);int ix=478+i*84;
            tile(g,ix,365,80,37,false,hit(x,y,ix,365,80,37));item(g,stack,ix+28,368,1.5f);
            var lore=stack.get(DataComponents.LORE);if(lore!=null&&!lore.lines().isEmpty()){
                String count="× "+lore.lines().getFirst().getString();text(g,count,ix+(80-font().width(styled(count,false)))/2,391,.85f,WHITE,false);}
            if(hit(x,y,ix,365,80,37)){g.fill(478,230,813,242,0xff161616);text(g,fit(stack.getHoverName().getString(),325,false),480,232,.95f,GOLD,false);}
        }
        if(total==0)text(g,"Aucun objet supplémentaire",480,377,.95f,SOFT,false);
        else if(total>4)text(g,(rewardScroll+1)+"–"+Math.min(total,rewardScroll+4)+" / "+total+" · molette",480,401,.75f,SOFT,false);
        accentButton(g,"✓ Accepter",478,412,108,21,x,y,!waiting&&q.status().equals("Disponible"),OLIVE);
        accentButton(g,"✓ Terminer",590,412,108,21,x,y,!waiting&&q.status().equals("À valider"),OLIVE);
        accentButton(g,q.followed()?"Ne plus suivre":"▱ Suivre",702,412,111,21,x,y,!waiting&&(q.status().equals("En cours")||q.status().equals("À valider")),ORANGE);
    }
    boolean click(double x,double y){
        if(x<157)return false;
        if(hit(x,y,157,65,12,43)){shiftTraders(-1);return true;}
        if(hit(x,y,815,65,13,43)){shiftTraders(1);return true;}
        for(int i=traderStart;i<Math.min(CampaignTrader.values().length,traderStart+TRADER_VISIBLE);i++)if(hit(x,y,mx(i),70,TRADER_W,33)){
            if(i!=trader&&!waiting){trader=i;scroll=detailScroll=rewardScroll=0;selected="";message="";if(cache.containsKey(i))ensureSelection();else request(0,"");}return true;
        }
        int fx=167;
        for(int i=0;i<FILTER_W.length;i++){if(hit(x,y,fx,148,FILTER_W[i],22)){filter=i;scroll=0;ensureSelection();return true;}fx+=FILTER_W[i]+3;}
        var list=visible();
        if(hit(x,y,451,LIST_Y,9,ROWS*ROW)){dragList=true;seekList(y);return true;}
        if(hit(x,y,814,DETAIL_Y,9,DETAIL_ROWS*15)){dragDetail=true;seekDetail(y);return true;}
        if(hit(x,y,LIST_X,LIST_Y,LIST_W-12,ROWS*ROW)){
            int index=scroll+(int)(y-LIST_Y)/ROW;if(index<list.size()){selected=list.get(index).id();detailScroll=rewardScroll=0;}return true;
        }
        if(waiting)return true;
        var q=current();
        if(hit(x,y,168,408,68,22)){request(4,"");return true;}
        if(hit(x,y,240,408,122,22)){request(5,"");return true;}
        if(hit(x,y,366,408,83,22)){cache.keySet().removeIf(key->key!=trader);request(0,"");return true;}
        if(q!=null){
            if(hit(x,y,478,412,108,21)&&q.status().equals("Disponible"))request(1,q.id());
            else if(hit(x,y,590,412,108,21)&&q.status().equals("À valider"))request(2,q.id());
            else if(hit(x,y,702,412,111,21)&&(q.status().equals("En cours")||q.status().equals("À valider")))request(3,q.id());
        }
        return true;
    }
    boolean scroll(double x,double y,double sy){
        if(sy==0)return false;int delta=sy>0?-1:1;
        if(hit(x,y,157,65,667,43)){shiftTraders(delta);return true;}
        if(hit(x,y,167,LIST_Y,292,ROWS*ROW)){scroll=Math.clamp(scroll+delta,0,Math.max(0,visible().size()-ROWS));return true;}
        if(hit(x,y,478,365,335,40)){var q=current();if(q!=null)rewardScroll=Math.clamp(rewardScroll+delta,0,Math.max(0,q.rewards().size()-4));return true;}
        if(hit(x,y,478,DETAIL_Y,345,DETAIL_ROWS*15)){detailScroll=Math.clamp(detailScroll+delta,0,Math.max(0,wrapped(current()).size()-DETAIL_ROWS));return true;}
        return false;
    }
    private void seekList(double y){scroll=(int)Math.round(Math.clamp((y-LIST_Y)/(ROWS*(double)ROW),0d,1d)*Math.max(0,visible().size()-ROWS));}
    private void seekDetail(double y){detailScroll=(int)Math.round(Math.clamp((y-DETAIL_Y)/(DETAIL_ROWS*15d),0d,1d)*Math.max(0,wrapped(current()).size()-DETAIL_ROWS));}
    boolean drag(double y){if(dragList){seekList(y);return true;}if(dragDetail){seekDetail(y);return true;}return false;}
    boolean release(){boolean was=dragList||dragDetail;dragList=dragDetail=false;return was;}
}
