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
