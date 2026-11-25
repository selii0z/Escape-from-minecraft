package fr.taczoperations.client;

import net.minecraft.client.gui.GuiGraphics;
import fr.taczoperations.operations.MilitaryProgression;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact, self-contained end-of-raid report. */
public final class RaidResultScreen extends Screen {
    private final boolean success;
    private final long duration;
    private final int kills, crates, contracts, combatXp, searchXp, contractXp, adjustment;

    private final int xpBefore, xpAfter,lootCount;
    private final long lootValue;
    private final long animationStart = System.nanoTime();

    public RaidResultScreen(boolean success, long duration, int kills, int crates,
                            int contracts, int combatXp, int searchXp, int contractXp, int adjustment, int xpBefore, int xpAfter,long lootValue,int lootCount) {
        super(Component.literal(success ? "EXTRACTION RÉUSSIE" : "RAID ÉCHOUÉ"));
        this.success = success;
        this.duration = duration;
        this.kills = kills;
        this.crates = crates;
        this.contracts = contracts;
        this.combatXp = combatXp;
        this.searchXp = searchXp;
        this.contractXp = contractXp;
        this.adjustment = adjustment;
        this.xpBefore = xpBefore;
        this.xpAfter = xpAfter;
        this.lootValue=lootValue;
        this.lootCount=lootCount;
    }

    @Override
    protected void init() {
        float scale=reportScale();
        int w=Math.max(1,Math.round(150*scale)),h=Math.max(1,Math.round(20*scale));
        addRenderableWidget(Button.builder(Component.literal("FERMER"), button -> onClose())
            .bounds(width/2-w/2,height/2+Math.round(106*scale),w,h).build());
    }

    private float reportScale(){
        return Math.max(.1F,Math.min(1F,Math.min((width-12)/350F,(height-12)/268F)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var illustration = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",
            success ? "textures/gui/raid_success.png" : "textures/gui/raid_failure.png");
        if (minecraft.getResourceManager().getResource(illustration).isEmpty()) {
            illustration = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",
                success ? "textures/gui/forest.png" : "textures/gui/dashboard.png");
        }
        if (minecraft.getResourceManager().getResource(illustration).isPresent()) {
            // The fallback art is rendered using normalized UVs.
            int imageWidth = Math.max(width, (height * 16 + 8) / 9);
            int imageHeight = imageWidth * 9 / 16;
            graphics.blit(illustration, (width - imageWidth) / 2, (height - imageHeight) / 2,
                imageWidth, imageHeight, 0f, 0f, 1024, 576, 1024, 576);
        }
        graphics.fill(0, 0, width, height, 0x55000000);
        graphics.pose().pushPose();
        graphics.pose().translate(width/2F,height/2F,0);
        float scale=reportScale();
        graphics.pose().scale(scale,scale,1);
        int x=0,y=0;
        graphics.fill(x - 175, y - 125, x + 175, y + 134, 0xb8202426);
        graphics.fill(x - 175, y - 125, x + 175, y - 121,
            success ? 0xff72ba87 : 0xffc76862);
        graphics.drawCenteredString(font, title, x, y - 111,
            success ? 0xff91dca1 : 0xffed9288);
        graphics.drawCenteredString(font, "Durée : " + duration / 60 + " min " + duration % 60 + " s",
            x, y - 91, 0xffdadada);
        graphics.drawCenteredString(font, "Éliminations : " + kills + "   |   Coffres : " + crates,
            x, y - 75, 0xffdadada);
        graphics.drawCenteredString(font, "Contrats terminés : " + contracts + "   |   XP contrats : " + contractXp,
            x, y - 59, 0xffdadada);
        graphics.drawCenteredString(font, "XP combats : " + combatXp + "   |   XP fouilles : " + searchXp,
            x, y - 43, 0xffdadada);
        graphics.drawCenteredString(font,
            (success ? "Bonus extraction : +" : "Malus de défaite : ") + adjustment + " XP",
            x, y - 27, success ? 0xff91dca1 : 0xffed9288);
        graphics.drawCenteredString(font,
            "XP FINALE : " + (xpBefore >= 0 && xpAfter >= xpBefore ? (long)xpAfter-xpBefore : Math.max(0L, (long) combatXp + searchXp + contractXp + adjustment)),
            x, y - 9, 0xffe9d394);
        renderProgress(graphics,x,y+8);
        String loot=lootValue>=0
            ?"Butin revendable : "+java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(lootValue)+" cr. · "+lootCount+" objet(s)"
            :success?"Estimation du butin indisponible":"Butin non évalué (échec)";
        graphics.drawCenteredString(font,loot,x,y+90,lootValue>=0?0xffe9d394:0xffb8bdba);
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String number(int xp) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE).format(xp);
    }

    private void renderProgress(GuiGraphics g,int x,int y) {
        if (xpBefore < 0 || xpAfter < xpBefore) {
            g.drawCenteredString(font,"Progression indisponible pour cet ancien raid",x,y+32,0xffb8bdba);
            return;
        }
        int beforeLevel=MilitaryProgression.level(xpBefore);
        int afterLevel=MilitaryProgression.level(xpAfter);
        double elapsed=(System.nanoTime()-animationStart)/1_000_000_000.0;
        double fraction=Math.clamp((elapsed-.25)/2.4,0.0,1.0);
        int shown=(int)(xpBefore+Math.round((xpAfter-(long)xpBefore)*fraction));
        int level=MilitaryProgression.level(shown);
        int lower=MilitaryProgression.threshold(level);
        int upper=MilitaryProgression.threshold(level+1);
        int w=300, left=x-w/2;
        long span=Math.max(1L,(long)upper-lower);
        int priorWidth=(int)Math.clamp((xpBefore-(long)lower)*w/span,0L,w);
        int filled=(int)Math.clamp((shown-(long)lower)*w/span,0L,w);
        g.drawCenteredString(font,MilitaryProgression.label(shown)+"  ·  "+MilitaryProgression.grade(shown),x,y+1,0xffdadada);
        g.fill(left-1,y+15,left+w+1,y+29,0xff626964);
        g.fill(left,y+16,left+w,y+28,0xff303639);
        if(priorWidth>0) g.fill(left,y+16,left+priorWidth,y+28,0xff829d75);
        if(filled>priorWidth) g.fill(left+priorWidth,y+16,left+filled,y+28,0xff8fdc57);
        g.drawCenteredString(font,number(shown)+" / "+number(upper)+" XP",x,y+33,0xffdadada);
        g.fill(left,y+48,left+8,y+56,0xff829d75);
        g.drawString(font,"Avant raid",left+12,y+48,0xffb8bdba,false);
        g.fill(x+14,y+48,x+22,y+56,0xff8fdc57);
        g.drawString(font,"XP gagnée : +"+number(shown-xpBefore),x+26,y+48,0xff8fdc57,false);
        if(afterLevel>beforeLevel) {
            g.drawCenteredString(font,"+"+(afterLevel-beforeLevel)+" niveau(x) · "+MilitaryProgression.label(xpAfter),x,y+67,0xff8fdc57);
        } else {
            g.drawCenteredString(font,number(Math.max(0,upper-shown))+" XP avant le prochain niveau",x,y+67,0xffb8bdba);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The report draws its own dim overlay; never blur its foreground.
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
