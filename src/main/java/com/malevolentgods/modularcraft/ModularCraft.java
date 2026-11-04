package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.game.ModuleBlock;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ModularCraft.ID)
public final class ModularCraft {
  public static final String ID = "modularcraft";
  private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
  private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
  private static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
  public static final Map<ModuleKind, DeferredBlock<ModuleBlock>> MODULES =
      new EnumMap<>(ModuleKind.class);

  static {
    for (ModuleKind kind : ModuleKind.values()) {
      String name = kind.name().toLowerCase(java.util.Locale.ROOT);
      var block =
          BLOCKS.register(
              name,
              () ->
                  new ModuleBlock(
                      kind, BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()));
      MODULES.put(kind, block);
      ITEMS.register(
          name,
          () ->
              new com.malevolentgods.modularcraft.game.ModuleItem(
                  block.get(), new Item.Properties()));
    }
  }

  public static final DeferredItem<Item> TOOL =
      ITEMS.register(
          "patching_tool",
          () ->
              new com.malevolentgods.modularcraft.game.PatchingTool(
                  new Item.Properties().stacksTo(1)));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ModuleEntity>> ENTITY =
      ENTITIES.register(
          "module",
          () ->
              BlockEntityType.Builder.of(
                      ModuleEntity::new,
                      MODULES.values().stream().map(DeferredBlock::get).toArray(Block[]::new))
                  .build(null));
  public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
      TABS.register(
          "instruments",
          () ->
              CreativeModeTab.builder()
                  .title(Component.translatable("tab.modularcraft"))
                  .icon(
                      () -> MODULES.get(ModuleKind.OSCILLATOR).get().asItem().getDefaultInstance())
                  .displayItems(
                      (params, out) -> {
                        for (var block : MODULES.values()) out.accept(block.get());
                        out.accept(TOOL.get());
                      })
                  .build());

  public ModularCraft(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    ENTITIES.register(bus);
    TABS.register(bus);
    bus.addListener(com.malevolentgods.modularcraft.network.Messages::register);
    com.malevolentgods.modularcraft.game.ServerRuntime.register();
  }
}
