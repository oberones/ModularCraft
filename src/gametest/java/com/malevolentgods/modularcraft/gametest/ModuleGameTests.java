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
  public static void detuneControlPersistsAndRejectsInvalidStaleAndForeignEdits(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "UnisonOwner"));
    BlockPos position = h.absolutePos(new BlockPos(1, 1, 1));
    h.getLevel()
        .setBlock(
            position, ModularCraft.MODULES.get(ModuleKind.OSCILLATOR).get().defaultBlockState(), 3);
    var module = (ModuleEntity) h.getLevel().getBlockEntity(position);
    module.initialize(owner);
    player.setPos(position.getCenter());
    h.assertTrue(module.state().value(Parameter.DETUNE) == 0, "Single saw by default");
    long revision = module.state().revision();
    var request =
        new com.malevolentgods.modularcraft.network.Messages.Control(
            module.state().id(),
            revision,
            com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
            Parameter.DETUNE,
            25.125);
    var bytes =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      com.malevolentgods.modularcraft.network.Messages.Control.CODEC.encode(bytes, request);
      var decoded = com.malevolentgods.modularcraft.network.Messages.Control.CODEC.decode(bytes);
      h.assertTrue(decoded.equals(request), "Detune edit packet roundtrips");
      com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(player, decoded);
    } finally {
      bytes.release();
    }
    h.runAfterDelay(
        3,
        () -> {
          h.assertTrue(module.state().value(Parameter.DETUNE) == 25.125, "Server accepts spread");
          var saved = module.saveWithFullMetadata(h.getLevel().registryAccess());
          var restored =
              (ModuleEntity)
                  BlockEntity.loadStatic(
                      position, module.getBlockState(), saved, h.getLevel().registryAccess());
          h.assertTrue(
              restored != null && restored.state().value(Parameter.DETUNE) == 25.125,
              "Spread survives block NBT reload");
          var guest =
              FakePlayerFactory.get(
                  h.getLevel(), new GameProfile(UUID.randomUUID(), "UnisonGuest"));
          guest.setPos(position.getCenter());
          for (var actor : new net.minecraft.server.level.ServerPlayer[] {player, guest})
            com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
                actor,
                new com.malevolentgods.modularcraft.network.Messages.Control(
                    module.state().id(),
                    module.state().revision(),
                    com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
                    Parameter.DETUNE,
                    actor == player ? 101 : 50));
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Control(
                  module.state().id(),
                  revision,
                  com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
                  Parameter.DETUNE,
                  75));
        });
    h.runAfterDelay(
        6,
        () -> {
          h.assertTrue(
              module.state().value(Parameter.DETUNE) == 25.125
                  && module.state().revision() == revision + 1,
              "Invalid, stale and foreign edits preserve accepted spread/revision");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void midiOrderedPitchesAndClosedSessionCannotReviveIt(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "MidiKeyboard"));
    BlockPos position = h.absolutePos(new BlockPos(1, 1, 1));
    h.getLevel()
        .setBlock(
            position, ModularCraft.MODULES.get(ModuleKind.KEYBOARD).get().defaultBlockState(), 3);
    var module = (ModuleEntity) h.getLevel().getBlockEntity(position);
    module.initialize(owner);
    player.setPos(position.getCenter());
    var runtime = com.malevolentgods.modularcraft.game.ServerRuntime.get(h.getLevel().getServer());
    runtime.inspect(player, module, true);
    UUID session = runtime.keyboardSession(player);
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            module.state().id(), module.state().revision(), session, 0, 127, false, false, true));
    h.runAfterDelay(
        3,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          // Fake players are absent from PlayerList, so lease cleanup runs in the same tick.
          h.assertTrue(
              note.note() == 127 && !note.gate(), "MIDI 127 accepted; offline lease released");
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Control(
                  module.state().id(),
                  module.state().revision(),
                  com.malevolentgods.modularcraft.network.Messages.Action.PARAMETER,
                  Parameter.OCTAVE,
                  6));
        });
    h.runAfterDelay(
        6,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          h.assertTrue(
              note.note() == 127 && !note.gate() && module.state().value(Parameter.OCTAVE) == 6,
              "Released pitch retained across octave edit");
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(), module.state().revision(), session, 1, 9, false, false));
        });
    h.runAfterDelay(
        9,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          h.assertTrue(note.note() == 93 && !note.gate(), "GUI pitch still uses selected octave");
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(),
                  module.state().revision(),
                  session,
                  2,
                  0,
                  false,
                  false,
                  true));
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(), module.state().revision(), session, 3, -1, false, false));
        });
    h.runAfterDelay(
        12,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          h.assertTrue(note.note() == 0 && !note.gate(), "MIDI zero and release retain order");
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(), module.state().revision(), session, 4, -1, false, true));
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(),
                  module.state().revision(),
                  session,
                  5,
                  69,
                  false,
                  false,
                  true));
        });
    h.runAfterDelay(
        15,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          h.assertTrue(note.note() == 0 && !note.gate(), "Closed MIDI session cannot revive sound");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void midiPacketsCarryAbsolutePitchesAndRejectMalformedModes(GameTestHelper h) {
    var bytes =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      for (int note : new int[] {0, 127}) {
        var packet =
            new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                UUID.randomUUID(), 0, UUID.randomUUID(), 0, note, false, false, true);
        bytes.clear();
        com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.encode(bytes, packet);
        h.assertTrue(bytes.readableBytes() == 52, "Bounded v3 keyboard packet");
        h.assertTrue(
            packet.equals(
                com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.decode(bytes)),
            "Absolute pitch roundtrip");
        bytes.clear();
        com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.encode(bytes, packet);
        bytes.setByte(51, 2);
        boolean rejected = false;
        try {
          com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.decode(bytes);
        } catch (IllegalArgumentException expected) {
          rejected = true;
        }
        h.assertTrue(rejected, "Malformed pitch mode rejected");
      }
      h.succeed();
    } finally {
      bytes.release();
    }
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void keyboardRejectsOldSessionsReorderedNotesAndGuests(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "OrderedKeyboard"));
    BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
    h.getLevel()
        .setBlock(pos, ModularCraft.MODULES.get(ModuleKind.KEYBOARD).get().defaultBlockState(), 3);
    var module = (ModuleEntity) h.getLevel().getBlockEntity(pos);
    module.initialize(owner);
    player.setPos(pos.getCenter());
    var runtime = com.malevolentgods.modularcraft.game.ServerRuntime.get(h.getLevel().getServer());
    runtime.inspect(player, module, true);
    UUID old = runtime.keyboardSession(player);
    runtime.inspect(player, module, true);
    UUID current = runtime.keyboardSession(player);
    long revision = module.state().revision();
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            module.state().id(), revision, old, 99, 9, false, false));
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            module.state().id(), revision, current, 2, 4, false, false));
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            module.state().id(), revision, current, 1, 1, false, false));
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            module.state().id(), revision, current, 3, -1, false, false));
    var guest =
        FakePlayerFactory.get(
            h.getLevel(), new GameProfile(UUID.randomUUID(), "UntrustedKeyboard"));
    guest.setPos(pos.getCenter());
    runtime.inspect(guest, module, true);
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        guest,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            module.state().id(), revision, runtime.keyboardSession(guest), 0, 10, false, false));
    h.runAfterDelay(
        3,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          h.assertTrue(note.note() == 64 && !note.gate(), "Only ordered authorized E4 survives");
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(), revision, current, 4, -1, false, true));
          com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
              player,
              new com.malevolentgods.modularcraft.network.Messages.Keyboard(
                  module.state().id(), revision, current, 5, 2, false, false));
        });
    h.runAfterDelay(
        6,
        () -> {
          var note = runtime.currentPatch(module.state().id()).keyboards().get(module.state().id());
          h.assertTrue(note.note() == 64 && !note.gate(), "Closed session cannot revive a note");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void keyboardPortsPersistenceAndOrderedRelease(GameTestHelper h) {
    UUID owner = UUID.randomUUID();
    var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "KeyboardOwner"));
    BlockPos position = h.absolutePos(new BlockPos(1, 1, 1));
    h.getLevel()
        .setBlock(
            position, ModularCraft.MODULES.get(ModuleKind.KEYBOARD).get().defaultBlockState(), 3);
    var keyboard = (ModuleEntity) h.getLevel().getBlockEntity(position);
    keyboard.initialize(owner);
    player.setPos(position.getCenter());
    h.assertTrue(keyboard.editable(player), "Owner may play keyboard");
    h.assertTrue(keyboard.state().value(Parameter.OCTAVE) == 4, "Default octave is four");
    var guest =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "KeyboardGuest"));
    guest.setPos(position.getCenter());
    h.assertTrue(!keyboard.editable(guest), "Guest cannot play keyboard");
    var block =
        (com.malevolentgods.modularcraft.game.ModuleBlock) keyboard.getBlockState().getBlock();
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            new net.minecraft.world.phys.Vec3(
                position.getX() + .75, position.getY() + .125, position.getZ()),
            net.minecraft.core.Direction.NORTH,
            position,
            false);
    h.assertTrue(
        block.portAt(keyboard.getBlockState(), hit)
            == com.malevolentgods.modularcraft.domain.Port.CV_OUT,
        "CV jack matches lower-front artwork");
    var tag = keyboard.saveWithFullMetadata(h.getLevel().registryAccess());
    var restored =
        (ModuleEntity)
            BlockEntity.loadStatic(
                position, keyboard.getBlockState(), tag, h.getLevel().registryAccess());
    h.assertTrue(
        restored != null && restored.state().kind() == ModuleKind.KEYBOARD,
        "Keyboard survives block-entity save");
    var runtime = com.malevolentgods.modularcraft.game.ServerRuntime.get(h.getLevel().getServer());
    runtime.inspect(player, keyboard, true);
    UUID session = runtime.keyboardSession(player);
    long revision = keyboard.state().revision();
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            keyboard.state().id(), revision, session, 0, 9, false, false));
    com.malevolentgods.modularcraft.game.ServerRuntime.enqueue(
        player,
        new com.malevolentgods.modularcraft.network.Messages.Keyboard(
            keyboard.state().id(), revision, session, 1, -1, false, false));
    h.runAfterDelay(
        3,
        () -> {
          var note =
              runtime.currentPatch(keyboard.state().id()).keyboards().get(keyboard.state().id());
          h.assertTrue(note.note() == 69 && !note.gate(), "Quick tap retains A4 but releases gate");
          runtime.unload(keyboard.state().id());
          note = runtime.currentPatch(keyboard.state().id()).keyboards().get(keyboard.state().id());
          h.assertTrue(note.note() == 60 && !note.gate(), "Unload clears transient note");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID)
  public static void keyboardPacketsAreBoundedAndRoundtrip(GameTestHelper h) {
    var bytes =
        new net.minecraft.network.RegistryFriendlyByteBuf(
            io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
    try {
      var packet =
          new com.malevolentgods.modularcraft.network.Messages.Keyboard(
              UUID.randomUUID(), 3, UUID.randomUUID(), 4, 11, false, false);
      com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.encode(bytes, packet);
      h.assertTrue(
          com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC
              .decode(bytes)
              .equals(packet),
          "Keyboard packet roundtrip");
      bytes.clear();
      com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.encode(bytes, packet);
      bytes.writeByte(0);
      boolean rejected = false;
      try {
        com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.decode(bytes);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      h.assertTrue(rejected, "Trailing note data rejected");
      bytes.clear();
      com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.encode(bytes, packet);
      bytes.setByte(48, 12);
      rejected = false;
      try {
        com.malevolentgods.modularcraft.network.Messages.Keyboard.CODEC.decode(bytes);
      } catch (IllegalArgumentException expected) {
        rejected = true;
      }
      h.assertTrue(rejected, "Out-of-range note rejected");
      h.succeed();
    } finally {
      bytes.release();
    }
  }

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
