package dev.ancientrpg.client;

import dev.ancientrpg.protocol.PublicContent;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Public rendering/registry definitions only. No gameplay decisions live here. */
public final class DemoRegistration implements ModInitializer {
  public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
  public static Block block;
  public static Item item;
  public static Item sword;
  public static final java.util.Map<Integer, Item> inventoryItems = new java.util.LinkedHashMap<>();

  @Override
  public void onInitialize() {
    var blockKey = ResourceKey.create(Registries.BLOCK, Identifier.parse(PublicContent.BLOCK));
    block =
        Registry.register(
            BuiltInRegistries.BLOCK,
            blockKey,
            new DemoBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).setId(blockKey)));
    var itemKey = ResourceKey.create(Registries.ITEM, Identifier.parse(PublicContent.ITEM));
    item =
        Registry.register(
            BuiltInRegistries.ITEM, itemKey, new Item(new Item.Properties().setId(itemKey)));
    var swordKey = ResourceKey.create(Registries.ITEM, Identifier.parse(PublicContent.SWORD));
    sword =
        Registry.register(
            BuiltInRegistries.ITEM,
            swordKey,
            new Item(new Item.Properties().setId(swordKey).stacksTo(1)));
    inventoryItems.put(1, sword);
    inventoryItems.put(10, item);
    for (var definition : dev.ancientrpg.protocol.InventoryCatalog.ITEMS) {
      if (definition.id() == 1 || definition.id() == 10) continue;
      var key = ResourceKey.create(Registries.ITEM, Identifier.parse(definition.key()));
      inventoryItems.put(
          definition.id(),
          Registry.register(
              BuiltInRegistries.ITEM,
              key,
              new Item(new Item.Properties().setId(key).stacksTo(definition.maxStack()))));
    }
  }

  private static final class DemoBlock extends Block {
    DemoBlock(Properties properties) {
      super(properties);
      registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
      builder.add(ACTIVE);
    }
  }
}
