package fr.taczoperations.client;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.operations.OperationsMenu;
import fr.taczoperations.operations.SupplyContract;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = OperationsMod.ID, value = Dist.CLIENT)
public final class OperationsScreen extends AbstractContainerScreen<OperationsMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        OperationsMod.ID, "textures/gui/operations_board.png");
    private static final int TEXT = 0xffeee7d5;
    private static final int AMBER = 0xffe3b575;
    private static final int MUTED = 0xffb7c5b7;

    public OperationsScreen(OperationsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 304;
        imageHeight = 202;
    }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(OperationsMod.MENU.get(), OperationsScreen::new);
    }

    @Override
    protected void init() {
        super.init();
        var view = menu.view();
        button(12, 56, 69, view.tab() == OperationsMenu.CONTRACTS ? "• Contrats" : "Contrats",
            OperationsMenu.CONTRACTS);
        button(85, 56, 64, view.tab() == OperationsMenu.GROUP ? "• Groupe" : "Groupe",
            OperationsMenu.GROUP);
        button(153, 56, 78, view.tab() == OperationsMenu.FOREST ? "• Forêt Noire" : "Forêt Noire",
            OperationsMenu.FOREST);
        button(235, 56, 57, "Actualiser", OperationsMenu.REFRESH);
        if (view.tab() == OperationsMenu.CONTRACTS) {
            for (int i = 0; i < view.contracts().size(); i++) {
                int status = view.contracts().get(i);
                String label = switch (status) {
                    case SupplyContract.ACTIVE -> "Livrer";
                    case SupplyContract.COMPLETED -> "Terminé";
                    default -> "Accepter";
                };
                button(219, 85 + i * 33, 67, label, OperationsMenu.FIRST_ROW + i)
                    .active = status != SupplyContract.COMPLETED;
            }
        } else if (view.tab() == OperationsMenu.FOREST) {
            button(94, 145, 94, "Départ de jour", OperationsMenu.DEPART);
            button(192, 145, 94, "Départ de nuit", OperationsMenu.DEPART_NIGHT);
        } else if (!view.partner().isEmpty()) {
            button(180, 91, 106, "Quitter le duo", OperationsMenu.LEAVE);
        } else if (!view.inviter().isEmpty()) {
            button(125, 118, 78, "Accepter", OperationsMenu.ACCEPT);
            button(208, 118, 78, "Refuser", OperationsMenu.DECLINE);
        } else {
            for (int i = 0; i < view.candidates().size(); i++) {
                button(219, 83 + i * 21, 67, "Inviter", OperationsMenu.FIRST_ROW + i);
            }
            if (view.pages() > 1) {
                button(167, 169, 24, "<", OperationsMenu.PREVIOUS).active = view.page() > 0;
                button(262, 169, 24, ">", OperationsMenu.NEXT).active = view.page() + 1 < view.pages();
            }
        }
    }

    private Button button(int x, int y, int width, String label, int action) {
        return addRenderableWidget(Button.builder(Component.literal(label), clicked -> {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
            }
        }).bounds(leftPos + x, topPos + y, width, 19).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float tick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        if (menu.view().tab() == OperationsMenu.CONTRACTS) {
            for (int i = 0; i < menu.view().contracts().size(); i++) {
                int y = topPos + 82 + i * 33;
                graphics.fill(leftPos + 12, y, leftPos + 292, y + 27, 0xff253632);
                var contract = SupplyContract.values()[i];
                graphics.renderItem(new ItemStack(contract.requested), leftPos + 17, y + 5);
                ItemStack reward = new ItemStack(contract.reward, contract.rewardAmount);
                graphics.renderItem(reward, leftPos + 192, y + 5);
                graphics.renderItemDecorations(font, reward, leftPos + 192, y + 5);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.view().tab() == OperationsMenu.CONTRACTS) {
            for (int i = 0; i < menu.view().contracts().size(); i++) {
                var contract = SupplyContract.values()[i];
                if (isHovering(192, 87 + i * 33, 16, 16, mouseX, mouseY)) {
                    graphics.renderTooltip(font, new ItemStack(contract.reward, contract.rewardAmount), mouseX, mouseY);
                    break;
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var view = menu.view();
        graphics.drawString(font, title, 15, 14, TEXT, false);
        graphics.drawString(font, view.credits() + " crédits militaires", 15, 32, AMBER, false);
        if (view.tab() == OperationsMenu.CONTRACTS) {
            for (int i = 0; i < view.contracts().size(); i++) {
                var contract = SupplyContract.values()[i];
                graphics.drawString(font, contract.title + "   +" + contract.credits + " cr. / "
                    + contract.experience + " XP", 40, 85 + i * 33, TEXT, false);
                Component items = Component.literal(contract.amount + " × ")
                    .append(contract.requested.getDescription());
                graphics.drawString(font, items,
                    40, 96 + i * 33, MUTED, false);
            }
        } else if (view.tab() == OperationsMenu.FOREST) {
            graphics.drawString(font, "Forêt Noire · 900 × 900 · " + (view.partner().isEmpty() ? "Solo" : "Duo"),
                17, 88, AMBER, false);
            graphics.drawString(font, "Extraction principale sur le bloc d’or.", 17, 105, TEXT, false);
            graphics.drawString(font, "Relais de la station : ouvre la sortie secondaire.", 17, 118, MUTED, false);
        } else if (!view.partner().isEmpty()) {
            graphics.drawString(font, "Duo · " + view.partner(), 17, 97, TEXT, false);
        } else if (!view.inviter().isEmpty()) {
            graphics.drawString(font, "Invitation de " + view.inviter(), 17, 97, AMBER, false);
        } else {
            for (int i = 0; i < view.candidates().size(); i++) {
                graphics.drawString(font, view.candidates().get(i), 17, 89 + i * 21, TEXT, false);
            }
            if (view.candidates().isEmpty()) {
                graphics.drawString(font, "Aucun joueur disponible.", 17, 89, MUTED, false);
            }
            if (view.pages() > 1) {
                graphics.drawCenteredString(font, (view.page() + 1) + " / " + view.pages(), 227, 174, TEXT);
            }
        }
        graphics.drawString(font, font.plainSubstrByWidth(view.notice(), 274), 15, 190, AMBER, false);
    }
}
