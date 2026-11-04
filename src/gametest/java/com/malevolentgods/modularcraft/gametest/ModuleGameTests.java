package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModularCraft.ID)
@PrefixGameTestTemplate(false)
public final class ModuleGameTests {
  private ModuleGameTests() {}

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void placedIdentityAndPermissions(GameTestHelper h) {
    BlockPos relative = new BlockPos(1, 1, 1);
    h.setBlock(relative, ModularCraft.MODULES.get(ModuleKind.OSCILLATOR).get());
    ModuleEntity module = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
    UUID owner = UUID.randomUUID();
    module.initialize(owner);
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "SynthOwner"));
    player.setPos(module.getBlockPos().getCenter());
    h.assertTrue(module.editable(player), "Nearby owner can edit");
    h.assertTrue(module.state().value(Parameter.FREQUENCY) == 220, "Default pitch");
    var stranger =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "SynthGuest"));
    stranger.setPos(module.getBlockPos().getCenter());
    h.assertTrue(!module.editable(stranger), "Guest cannot edit");
    player.setPos(10000, 100, 10000);
    h.assertTrue(!module.editable(player), "Distance enforced");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void moduleSaveRoundtripAndReplacement(GameTestHelper h) {
    BlockPos relative = new BlockPos(2, 1, 2);
    h.setBlock(relative, ModularCraft.MODULES.get(ModuleKind.SPEAKER).get());
    BlockPos pos = h.absolutePos(relative);
    ModuleEntity module = (ModuleEntity) h.getLevel().getBlockEntity(pos);
    UUID owner = UUID.randomUUID();
    module.initialize(owner);
    var identity = module.state().id();
    var tag = module.saveWithFullMetadata(h.getLevel().registryAccess());
    var restored =
        (ModuleEntity)
            BlockEntity.loadStatic(pos, module.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(restored != null && restored.state().id().equals(identity), "UUID persists");
    h.assertTrue(restored.state().owner().equals(owner), "Owner persists");
    h.assertTrue(!restored.state().enabled(), "Restart clears speaker activation");
    h.setBlock(relative, net.minecraft.world.level.block.Blocks.AIR);
    h.setBlock(relative, ModularCraft.MODULES.get(ModuleKind.SPEAKER).get());
    ModuleEntity replacement = (ModuleEntity) h.getLevel().getBlockEntity(pos);
    replacement.initialize(owner);
    h.assertTrue(!replacement.state().id().equals(identity), "Replacement gets new identity");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void releaseCannotBeOvertakenByQueuedGatePress(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "GateOwner"));
    var runtime = com.malevolentgods.modularcraft.game.ServerRuntime.get(h.getLevel().getServer());
    BlockPos a = h.absolutePos(new BlockPos(1, 1, 1));
    BlockPos b = h.absolutePos(new BlockPos(2, 1, 1));
    h.getLevel()
        .setBlock(a, ModularCraft.MODULES.get(ModuleKind.ENVELOPE).get().defaultBlockState(), 3);
    h.getLevel()
        .setBlock(b, ModularCraft.MODULES.get(ModuleKind.SPEAKER).get().defaultBlockState(), 3);
    var envelope = (ModuleEntity) h.getLevel().getBlockEntity(a);
    var speaker = (ModuleEntity) h.getLevel().getBlockEntity(b);
    envelope.initialize(owner);
    speaker.initialize(owner);
    player.setPos(a.getCenter());
    var cable =
        new com.malevolentgods.modularcraft.domain.Cable(
            envelope.state().id(),
            com.malevolentgods.modularcraft.domain.Port.ENVELOPE_OUT,
            speaker.state().id(),
            com.malevolentgods.modularcraft.domain.Port.GAIN_IN);
    runtime.state().connect(owner, false, cable, 0, 0);
    runtime.attach(envelope);
    runtime.attach(speaker);
    runtime.state().enable(owner, false, speaker.state().id(), speaker.state().revision(), true);
    runtime.attach(speaker);
    runtime.inspect(player, envelope, true);
    long revision = envelope.state().revision();
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Control(
            envelope.state().id(),
            revision,
            com.malevolentgods.modularcraft.network.Messages.Action.GATE,
            Parameter.SUSTAIN,
            1));
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Control(
            envelope.state().id(),
            revision,
            com.malevolentgods.modularcraft.network.Messages.Action.GATE,
            Parameter.SUSTAIN,
            0));
    h.runAfterDelay(
        3,
        () -> {
          var checkpoint =
              runtime.currentPatch(envelope.state().id()).envelopes().get(envelope.state().id());
          h.assertTrue(
              checkpoint == null || !checkpoint.gate(),
              "A key release must not be overtaken by its queued press");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void unsupportedBlockDataIsPreserved(GameTestHelper h) {
    BlockPos relative = new BlockPos(1, 1, 1);
    h.setBlock(relative, ModularCraft.MODULES.get(ModuleKind.FILTER).get());
    var module = (ModuleEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
    var unsupported = new net.minecraft.nbt.CompoundTag();
    unsupported.putInt("Schema", 77);
    unsupported.putString("FutureInstrument", "untouched");
    module.loadCustomOnly(unsupported, h.getLevel().registryAccess());
    var roundtrip = module.saveCustomOnly(h.getLevel().registryAccess());
    h.assertTrue(
        roundtrip.getInt("Schema") == 77
            && roundtrip.getString("FutureInstrument").equals("untouched"),
        "Original unknown data must survive");
    h.assertTrue(!module.valid(), "Unknown instrument remains quarantined");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void editPacketsRejectTrailingData(GameTestHelper h) {
    var bytes =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      var request =
          new com.malevolentgods.modularcraft.network.Messages.Control(
              UUID.randomUUID(),
              0,
              com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
              Parameter.FREQUENCY,
              440);
      com.malevolentgods.modularcraft.network.Messages.Control.CODEC.encode(bytes, request);
      bytes.writeInt(999);
      boolean rejected = false;
      try {
        com.malevolentgods.modularcraft.network.Messages.Control.CODEC.decode(bytes);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      h.assertTrue(rejected, "Trailing edit bytes are malformed, not accepted state");
      h.succeed();
    } finally {
      bytes.release();
    }
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void copiedItemDataCannotChoosePlacedIdentityOrOwner(GameTestHelper h) {
    UUID owner = UUID.randomUUID(), foreign = UUID.randomUUID();
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "FreshOwner"));
    BlockPos floor = h.absolutePos(new BlockPos(1, 0, 1));
    h.getLevel()
        .setBlock(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
    player.setPos(floor.getCenter().add(0, 2, -2));
    BlockPos target = floor.above();
    var copied =
        new com.malevolentgods.modularcraft.domain.ModuleState(
            UUID.randomUUID(),
            foreign,
            ModuleKind.OSCILLATOR,
            h.getLevel().dimension().location().toString(),
            target.getX(),
            target.getY(),
            target.getZ(),
            0,
            ModuleKind.OSCILLATOR.defaults(),
            false,
            false);
    var tag = new net.minecraft.nbt.CompoundTag();
    tag.putString("id", "modularcraft:module");
    tag.putInt("Schema", 1);
    tag.putByteArray(
        "Module", com.malevolentgods.modularcraft.domain.StateCodec.moduleBytes(copied));
    var stack = ModularCraft.MODULES.get(ModuleKind.OSCILLATOR).get().asItem().getDefaultInstance();
    stack.set(
        net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
        net.minecraft.world.item.component.CustomData.of(tag));
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
    var context =
        new net.minecraft.world.item.context.BlockPlaceContext(
            player,
            net.minecraft.world.InteractionHand.MAIN_HAND,
            stack,
            new net.minecraft.world.phys.BlockHitResult(
                floor.getCenter().add(0, .5, 0), net.minecraft.core.Direction.UP, floor, false));
    ((net.minecraft.world.item.BlockItem) stack.getItem()).place(context);
    var placed = (ModuleEntity) h.getLevel().getBlockEntity(target);
    h.assertTrue(placed != null && placed.valid(), "Placement creates a usable module");
    h.assertTrue(
        placed.state().owner().equals(owner) && !placed.state().id().equals(copied.id()),
        "Portable NBT cannot choose identity or owner");
    h.succeed();
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void packagedArtifactIsActuallyLoaded(GameTestHelper h) {
    String origin =
        ModularCraft.class.getProtectionDomain().getCodeSource().getLocation().toString();
    if (Boolean.getBoolean("modularcraft.packaged"))
      h.assertTrue(
          origin.contains("modularcraft-0.1.0.jar"),
          "Runtime must resolve the installable JAR: " + origin);
    System.out.println("MODULARCRAFT_TEST_ARTIFACT " + origin);
    h.succeed();
  }
}
