package fr.taczoperations;

import fr.taczoperations.entity.GuardCorpse;
import fr.taczoperations.operations.ForestExpedition;
import fr.taczoperations.operations.RaidRelic;
import fr.taczoperations.operations.RaidResultPayload;
import fr.taczoperations.operations.SupplyContract;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.Supplier;

@Mod(OperationsMod.ID)
public final class OperationsMod {
    public static final String ID = "taczoperations";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister<net.minecraft.world.item.ArmorMaterial> ARMOR_MATERIALS =
        DeferredRegister.create(Registries.ARMOR_MATERIAL, ID);
    private static final net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> NVG_MATERIAL =
        ARMOR_MATERIALS.register("night_vision_goggles", () -> goggleMaterial("night_vision_goggles"));
    private static final net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> THERMAL_MATERIAL =
        ARMOR_MATERIALS.register("thermal_goggles", () -> goggleMaterial("thermal_goggles"));
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final java.util.List<Supplier<fr.taczoperations.operations.RaidFurnitureBlock>> FURNITURE=java.util.List.of(
        BLOCKS.registerBlock("raid_medbag",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("raid_toolbox",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("raid_weaponbox",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("raid_safe",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("raid_pc",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("raid_cabinet",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion())
    );
    // Kept separate from the six raid loot types: these are purely protected hideout props.
    public static final java.util.List<Supplier<fr.taczoperations.operations.RaidFurnitureBlock>> DECOR=java.util.List.of(
        BLOCKS.registerBlock("decor_shelf",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("decor_desk",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("decor_chair",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("decor_workbench",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()),
        BLOCKS.registerBlock("decor_crate",fr.taczoperations.operations.RaidFurnitureBlock::new,BlockBehaviour.Properties.of().strength(2.0F).noOcclusion())
    );
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<fr.taczoperations.operations.RaidFurnitureEntity>> FURNITURE_ENTITY=
        BLOCK_ENTITIES.register("raid_furniture",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
            fr.taczoperations.operations.RaidFurnitureEntity::new,java.util.stream.Stream.concat(FURNITURE.stream(),DECOR.stream()).map(Supplier::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    public static final Supplier<OperationsBoardBlock> BOARD = BLOCKS.registerBlock(
        "operations_board", OperationsBoardBlock::new, BlockBehaviour.Properties.of().strength(2.5F));
    public static final Supplier<EntityType<GuardCorpse>> GUARD_CORPSE = ENTITIES.register("guard_corpse", () ->
        EntityType.Builder.<GuardCorpse>of(GuardCorpse::new, MobCategory.MISC)
            .sized(1.55F, .36F).clientTrackingRange(8).updateInterval(10).build(ID + ":guard_corpse"));
    public static final Supplier<MenuType<fr.taczoperations.operations.HideoutControlMenu>> HIDEOUT_MENU=MENUS.register(
        "hideout_control",()->IMenuTypeExtension.create(fr.taczoperations.operations.HideoutControlMenu::new));
    public static final Supplier<MenuType<fr.taczoperations.operations.ProgressionControlMenu>> PROGRESSION_MENU=MENUS.register(
        "progression_control",()->IMenuTypeExtension.create(fr.taczoperations.operations.ProgressionControlMenu::new));
    public static final Supplier<MenuType<OperationsMenu>> MENU = MENUS.register(
        "operations_board", () -> IMenuTypeExtension.create(OperationsMenu::new));
    public static final Supplier<MenuType<fr.taczoperations.operations.RaidSearchMenu>> SEARCH_MENU=MENUS.register(
        "raid_search",()->IMenuTypeExtension.create((id,inventory,buffer)->new fr.taczoperations.operations.RaidSearchMenu(id,inventory)));

    public static final Supplier<MenuType<fr.taczoperations.operations.DepartureInsuranceMenu>> DEPARTURE_INSURANCE=MENUS.register(
        "departure_insurance",()->IMenuTypeExtension.create(fr.taczoperations.operations.DepartureInsuranceMenu::new));
    public static final Supplier<MenuType<fr.taczoperations.operations.FieldInventoryMenu>> FIELD_INVENTORY=MENUS.register(
        "field_inventory",()->IMenuTypeExtension.create(fr.taczoperations.operations.FieldInventoryMenu::new));

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);

    static {
        GENERATORS.register("forest", () -> ForestChunkGenerator.CODEC);
        RaidRelic.register(ITEMS);
        fr.taczoperations.operations.TradeLoot.register(ITEMS);
        fr.taczoperations.operations.CollectorItems.register(ITEMS);
        ITEMS.registerItem("raid_grenade",fr.taczoperations.operations.RaidGrenade::new,new net.minecraft.world.item.Item.Properties().stacksTo(8));
        fr.taczoperations.operations.CampaignSupplies.register(ITEMS);
        ITEMS.registerItem("night_vision_goggles", props -> new net.minecraft.world.item.ArmorItem(NVG_MATERIAL, net.minecraft.world.item.ArmorItem.Type.HELMET, props), new net.minecraft.world.item.Item.Properties().stacksTo(1));
        ITEMS.registerItem("thermal_goggles", props -> new net.minecraft.world.item.ArmorItem(THERMAL_MATERIAL, net.minecraft.world.item.ArmorItem.Type.HELMET, props), new net.minecraft.world.item.Item.Properties().stacksTo(1));
        for(int i=0;i<fr.taczoperations.operations.FieldPack.BAGS.length;i++){
            final int capacity=fr.taczoperations.operations.FieldPack.CAPACITY[i];
            ITEMS.registerItem("backpack_"+fr.taczoperations.operations.FieldPack.BAGS[i],props->new fr.taczoperations.operations.FieldPack(props,capacity,false),new net.minecraft.world.item.Item.Properties());
        }
        for(int i=0;i<fr.taczoperations.operations.FieldPack.RIGS.length;i++){
            final int capacity=fr.taczoperations.operations.FieldPack.RIG_CAPACITY[i];
            ITEMS.registerItem("rig_"+fr.taczoperations.operations.FieldPack.RIGS[i],props->new fr.taczoperations.operations.FieldPack(props,capacity,true),new net.minecraft.world.item.Item.Properties());
        }
        ITEMS.registerItem("tactical_headset",net.minecraft.world.item.Item::new,new net.minecraft.world.item.Item.Properties().stacksTo(1));
        ITEMS.registerItem("secure_case", fr.taczoperations.operations.SecureCaseItem::new, new net.minecraft.world.item.Item.Properties().stacksTo(1));
        for(var tier:fr.taczoperations.operations.SecureCaseItem.Tier.values()){
            if(tier==fr.taczoperations.operations.SecureCaseItem.Tier.LEGACY)continue;
            ITEMS.registerItem(tier.id,props->new fr.taczoperations.operations.SecureCaseItem(props,tier),
                new net.minecraft.world.item.Item.Properties().stacksTo(1));
        }
        for(String medical : new String[]{"bandage","hemostat","splint","medkit","salewa"}) {
            ITEMS.registerItem(medical,props -> new fr.taczoperations.operations.FieldMedicine(props,medical),new net.minecraft.world.item.Item.Properties().stacksTo(8));
        }
        ITEMS.registerItem("field_ration", net.minecraft.world.item.Item::new,
            new net.minecraft.world.item.Item.Properties().stacksTo(16).food(new net.minecraft.world.food.FoodProperties.Builder()
                .nutrition(6).saturationModifier(.6F).alwaysEdible().build()));
        ITEMS.registerItem("military_tag",net.minecraft.world.item.Item::new,new net.minecraft.world.item.Item.Properties().stacksTo(1));
        ITEMS.registerItem("key_ring",fr.taczoperations.operations.KeyRingItem::new,new net.minecraft.world.item.Item.Properties());
        ITEMS.registerItem("forest_key",net.minecraft.world.item.Item::new,new net.minecraft.world.item.Item.Properties().stacksTo(16));
        ITEMS.registerItem("industrial_key", props -> new net.minecraft.world.item.Item(props), new net.minecraft.world.item.Item.Properties().stacksTo(16));
        ITEMS.registerSimpleBlockItem("operations_board", BOARD);
        ITEMS.registerSimpleBlockItem("raid_medbag",FURNITURE.get(0));
        ITEMS.registerSimpleBlockItem("raid_toolbox",FURNITURE.get(1));
        ITEMS.registerSimpleBlockItem("raid_weaponbox",FURNITURE.get(2));
        ITEMS.registerSimpleBlockItem("raid_safe",FURNITURE.get(3));
        ITEMS.registerSimpleBlockItem("raid_pc",FURNITURE.get(4));
        ITEMS.registerSimpleBlockItem("raid_cabinet",FURNITURE.get(5));
    }

    private static net.minecraft.world.item.ArmorMaterial goggleMaterial(String id) {
        var leather = net.minecraft.world.item.ArmorMaterials.LEATHER.value();
        return new net.minecraft.world.item.ArmorMaterial(leather.defense(), leather.enchantmentValue(),
            leather.equipSound(), leather.repairIngredient(),
            java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ID, id))),
            leather.toughness(), leather.knockbackResistance());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("31");
        registrar.playToServer(fr.taczoperations.operations.WipeAccount.TYPE,fr.taczoperations.operations.WipeAccount.CODEC,
            (message,context)->context.enqueueWork(()->{
                if(context.player() instanceof net.minecraft.server.level.ServerPlayer p)
                    fr.taczoperations.operations.WipeAccount.handle(p,message);
            }));
        registrar.playToServer(fr.taczoperations.operations.ShopBuy.TYPE,fr.taczoperations.operations.ShopBuy.CODEC,
            (message,context)->context.enqueueWork(()->{
                if(context.player() instanceof net.minecraft.server.level.ServerPlayer p
                    &&p.containerMenu instanceof OperationsMenu menu&&menu.containerId==message.menuId())
                    menu.buyLots(p,message.row(),message.lots());
            }));
        registrar.playToServer(fr.taczoperations.operations.InspectItem.TYPE,fr.taczoperations.operations.InspectItem.CODEC,
            (message,context)->context.enqueueWork(()->{
                if(context.player() instanceof net.minecraft.server.level.ServerPlayer p)
                    fr.taczoperations.operations.ItemDiscovery.request(p,message);
            }));
        registrar.playToClient(fr.taczoperations.operations.DiscoveryState.TYPE,fr.taczoperations.operations.DiscoveryState.CODEC,
            (message,context)->{
                if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())fr.taczoperations.client.DiscoveryClient.handle(message,context);
            });
        registrar.playToServer(fr.taczoperations.operations.ListPlayerItem.TYPE,fr.taczoperations.operations.ListPlayerItem.CODEC,
            (message,context)->context.enqueueWork(()->{
                if(context.player() instanceof net.minecraft.server.level.ServerPlayer p
                    &&p.containerMenu instanceof OperationsMenu menu&&menu.containerId==message.menuId())
                    menu.listPlayerItem(p,message.price());
            }));
        registrar.playToServer(fr.taczoperations.operations.OpenFieldInventory.TYPE,fr.taczoperations.operations.OpenFieldInventory.CODEC,
            (message,context)->context.enqueueWork(()->{
                if(context.player() instanceof net.minecraft.server.level.ServerPlayer p)
                    fr.taczoperations.operations.FieldInventoryMenu.open(p);
            }));
        registrar.playToClient(RaidResultPayload.TYPE, RaidResultPayload.STREAM_CODEC,
            (report, context) -> {
                // Never resolve client-only screen classes on a dedicated server.
                if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                    fr.taczoperations.client.RaidResultHandler.handle(report, context);
                }
            });
        registrar.playToClient(fr.taczoperations.operations.RaidInjuryPayload.TYPE,
            fr.taczoperations.operations.RaidInjuryPayload.STREAM_CODEC,(injury,context)->{
                if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
                    fr.taczoperations.client.RaidInjuryHud.handle(injury,context);
            });
        registrar.playToClient(fr.taczoperations.operations.RaidPartnerPayload.TYPE,
            fr.taczoperations.operations.RaidPartnerPayload.STREAM_CODEC,(partner,context)->{
                if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
                    fr.taczoperations.client.RaidPartnerHud.handle(partner,context);
            });
        registrar.playToClient(fr.taczoperations.operations.RaidExitPayload.TYPE,
            fr.taczoperations.operations.RaidExitPayload.STREAM_CODEC,(exits,context)->{
                if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
                    fr.taczoperations.client.RaidExitHud.handle(exits,context);
            });
        registrar.playToClient(fr.taczoperations.operations.ExtractionProgressPayload.TYPE,
            fr.taczoperations.operations.ExtractionProgressPayload.STREAM_CODEC,(progress,context)->{
                if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
                    fr.taczoperations.client.ExtractionHud.handle(progress,context);
            });
        registrar.playToClient(fr.taczoperations.operations.RaidTimerPayload.TYPE,
            fr.taczoperations.operations.RaidTimerPayload.STREAM_CODEC,(timer,context)->{
                if(net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
                    fr.taczoperations.client.RaidTimerHud.handle(timer,context);
            });
        registrar.playToClient(fr.taczoperations.operations.RaidDropPayload.TYPE,
            fr.taczoperations.operations.RaidDropPayload.STREAM_CODEC, (drop, context) -> {
                if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient())
                    fr.taczoperations.client.RaidDropHud.handle(drop, context);
            });
    }

    public OperationsMod(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ARMOR_MATERIALS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        ENTITIES.register(bus);
        GENERATORS.register(bus);
        bus.addListener(OperationsMod::registerPayloads);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.PassiveSkills::commands);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.StoryMissions::commands);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.StoryMissions::tick);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.Hideout::commands);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.Hideout::tick);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.Hideout::interact);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.HideoutRange::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,fr.taczoperations.operations.HideoutRange::shoot);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.HideoutRange::interact);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.HideoutRange::interactSpecific);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,fr.taczoperations.operations.HideoutRange::hit);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,fr.taczoperations.operations.HideoutRange::fire);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.Hideout::protectBreak);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.Hideout::protectPlace);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.Hideout::protectExplosion);
        NeoForge.EVENT_BUS.addListener(ForestPreparation::tick);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.FieldInventoryMenu::refresh);
        NeoForge.EVENT_BUS.addListener(ForestExpedition::tick);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.ScavRun::tick);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidInjuries::tick);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidSurvival::consumed);
        NeoForge.EVENT_BUS.addListener(ForestExpedition::interact);
        NeoForge.EVENT_BUS.addListener(ForestProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onDrops);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidGrenade::impact);
        if(net.neoforged.fml.ModList.get().isLoaded("curios"))
            fr.taczoperations.compat.FieldCurios.registerRaidDropRules();
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, fr.taczoperations.operations.RaidSecurity::onDeathDrops);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidSecurity::commands);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onLogout);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onLogin);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.ItemDiscovery::login);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.ItemDiscovery::respawn);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onServerTick);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, fr.taczoperations.operations.RaidSecurity::cloneCases);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidSecurity::returnCases);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onRespawn);
        NeoForge.EVENT_BUS.addListener(ForestRaid::deliverReport);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.BallisticVest::normalizePlayerEquipment);
        NeoForge.EVENT_BUS.addListener(SupplyContract::assaultKill);
        NeoForge.EVENT_BUS.addListener(ForestRaid::onInspect);
        NeoForge.EVENT_BUS.addListener(ForestCombat::tick);
        NeoForge.EVENT_BUS.addListener(ForestCombat::onGunHurt);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,fr.taczoperations.operations.CampaignGunEvents::hit);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.CampaignGunEvents::kill);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,fr.taczoperations.operations.DetailedStats::fire);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.DetailedStats::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,fr.taczoperations.operations.WeaponCondition::shoot);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,fr.taczoperations.operations.WeaponCondition::fire);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,fr.taczoperations.operations.WeaponCondition::reload);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.WeaponCondition::tooltip);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidSecurity::tooltip);
        NeoForge.EVENT_BUS.addListener(fr.taczoperations.operations.RaidOrigin::tooltip);
        NeoForge.EVENT_BUS.addListener(ForestProtection::onExplosion);
    }
}
