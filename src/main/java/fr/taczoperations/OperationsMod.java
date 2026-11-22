package fr.taczoperations;

import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.ForestCombat;
import fr.taczoperations.operations.ForestRaid;
import fr.taczoperations.operations.ForestProtection;
import fr.taczoperations.operations.ForestPreparation;
import fr.taczoperations.operations.OperationsBoardBlock;
import fr.taczoperations.operations.OperationsMenu;
import fr.taczoperations.worldgen.ForestChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

@Mod(OperationsMod.ID)
public final class OperationsMod {
    public static final String ID = "taczoperations";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);

    public static final Supplier<OperationsBoardBlock> BOARD = BLOCKS.registerBlock(
        "operations_board", OperationsBoardBlock::new, BlockBehaviour.Properties.of().strength(2.5F));
    public static final Supplier<MenuType<OperationsMenu>> MENU = MENUS.register(
        "operations_board", () -> IMenuTypeExtension.create(OperationsMenu::new));

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);

    static {
        GENERATORS.register("forest", () -> ForestChunkGenerator.CODEC);
        ITEMS.registerSimpleBlockItem("operations_board", BOARD);
    }

    public OperationsMod(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        GENERATORS.register(bus);
        NeoForge.EVENT_BUS.addListener(ForestPreparation::tick);
        NeoForge.EVENT_BUS.addListener(ForestExpedition::tick);
        NeoForge.EVENT_BUS.addListener(ForestExpedition::interact);
        NeoForge.EVENT_BUS.addListener(ForestProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onDrops);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onInspect);
        NeoForge.EVENT_BUS.addListener(ForestCombat::tick);
        NeoForge.EVENT_BUS.addListener(ForestProtection::onExplosion);
    }
}
