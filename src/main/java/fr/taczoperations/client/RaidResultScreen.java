package fr.taczoperations.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Compact, self-contained end-of-raid report. */
public final class RaidResultScreen extends Screen {
    private final boolean success;
    private final long duration;
    private final int kills, crates, contracts, combatXp, searchXp, contractXp, adjustment;

    public RaidResultScreen(boolean success, long duration, int kills, int crates,
                            int contracts, int combatXp, int searchXp, int contractXp, int adjustment) {
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
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("FERMER"), button -> onClose())
            .bounds(width / 2 - 75, height / 2 + 100, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var illustration = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",
            success ? "textures/gui/raid_success.png" : "textures/gui/raid_failure.png");
        if (minecraft.getResourceManager().getResource(illustration).isPresent()) {
            int imageWidth = Math.max(width, (height * 16 + 8) / 9);
            int imageHeight = imageWidth * 9 / 16;
            graphics.blit(illustration, (width - imageWidth) / 2, (height - imageHeight) / 2,
                imageWidth, imageHeight, 0f, 0f, 1024, 576, 1024, 576);
        }
        graphics.fill(0, 0, width, height, 0x55000000);
        int x = width / 2, y = height / 2;
        graphics.fill(x - 175, y - 125, x + 175, y + 130, 0xe6202426);
        graphics.fill(x - 175, y - 125, x + 175, y - 121,
            success ? 0xff72ba87 : 0xffc76862);
        graphics.drawCenteredString(font, title, x, y - 107,
            success ? 0xff91dca1 : 0xffed9288);
        graphics.drawCenteredString(font, "Durée : " + duration / 60 + " min " + duration % 60 + " s",
            x, y - 77, 0xffdadada);
        graphics.drawCenteredString(font, "Éliminations : " + kills + "   |   Coffres : " + crates,
            x, y - 54, 0xffdadada);
        graphics.drawCenteredString(font, "Contrats terminés : " + contracts + "   |   XP contrats : " + contractXp,
            x, y - 31, 0xffdadada);
        graphics.drawCenteredString(font, "XP combats : " + combatXp + "   |   XP fouilles : " + searchXp,
            x, y - 8, 0xffdadada);
        graphics.drawCenteredString(font,
            (success ? "Bonus extraction : +" : "Malus de défaite : ") + adjustment + " XP",
            x, y + 19, success ? 0xff91dca1 : 0xffed9288);
        graphics.drawCenteredString(font,
            "XP FINALE : " + Math.max(0L, (long) combatXp + searchXp + contractXp + adjustment),
            x, y + 54, 0xffe9d394);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The report draws its own dim overlay; never blur its foreground.
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
