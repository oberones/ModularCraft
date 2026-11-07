package com.malevolentgods.modularcraft.gametest;

import com.malevolentgods.modularcraft.ModularCraft;
import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import com.malevolentgods.modularcraft.game.ModuleEntity;
import com.malevolentgods.modularcraft.game.ServerRuntime;
import com.malevolentgods.modularcraft.network.Messages;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModularCraft.ID)
@PrefixGameTestTemplate(false)
public final class StepRedstoneGameTests {
  private StepRedstoneGameTests() {}

  private static Parameter mode() {
    return Parameter.valueOf("REDSTONE_MODE");
  }

  private static final class Fixture {
    final GameTestHelper helper;
    final ServerRuntime runtime;
    final ServerPlayer player;
    final ModuleEntity module;
    final GameTestSequence sequence;
    final BlockPos power;

    Fixture(GameTestHelper helper) {
      this.helper = helper;
      runtime = ServerRuntime.get(helper.getLevel().getServer());
      UUID owner = UUID.randomUUID();
      player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(owner, "RedstoneTest"));
      BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
      helper
          .getLevel()
          .setBlock(
              pos,
              ModularCraft.MODULES.get(ModuleKind.STEP_SEQUENCER).get().defaultBlockState(),
              3);
      module = (ModuleEntity) helper.getLevel().getBlockEntity(pos);
      module.initialize(owner);
      player.setPos(pos.getCenter());
      power = pos.above();
      sequence = helper.startSequence();
    }

    void send(Messages.Action action, Parameter parameter, double value) {
      ServerRuntime.enqueue(
          player,
          new Messages.Control(
              module.state().id(), module.state().revision(), action, parameter, value));
    }

    void mode(boolean enabled) {
      send(Messages.Action.PARAMETER, StepRedstoneGameTests.mode(), enabled ? 1 : 0);
    }

    void power(boolean enabled) {
      helper
          .getLevel()
          .setBlock(power, (enabled ? Blocks.REDSTONE_BLOCK : Blocks.AIR).defaultBlockState(), 3);
    }

    Sequencer.Checkpoint checkpoint() {
      return runtime.currentPatch(module.state().id()).steps().get(module.state().id()).clock();
    }

    void after(Runnable assertions) {
      sequence
          .thenIdle(2)
          .thenWaitUntil(() -> helper.assertTrue(runtime.pendingRequests() == 0, "Inbox drained"))
          .thenExecute(assertions);
    }

    void running(boolean expected) {
      helper.assertTrue(checkpoint().running() == expected, "Expected running=" + expected);
    }
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID, timeoutTicks = 200)
  public static void stepExternalClockSurvivesRemovalElsewhereInPatch(GameTestHelper h) {
    Fixture f = new Fixture(h);
    BlockPos sourcePos = f.module.getBlockPos().east();
    BlockPos removedPos = f.module.getBlockPos().west();
    h.getLevel()
        .setBlock(
            sourcePos,
            ModularCraft.MODULES.get(ModuleKind.STEP_SEQUENCER).get().defaultBlockState(),
            3);
    h.getLevel()
        .setBlock(
            removedPos,
            ModularCraft.MODULES.get(ModuleKind.OSCILLATOR).get().defaultBlockState(),
            3);
    var source = (ModuleEntity) h.getLevel().getBlockEntity(sourcePos);
    var removed = (ModuleEntity) h.getLevel().getBlockEntity(removedPos);
    source.initialize(f.player.getUUID());
    removed.initialize(f.player.getUUID());
    f.runtime
        .state()
        .connect(
            f.player.getUUID(),
            false,
            new Cable(source.state().id(), Port.CLOCK_OUT, f.module.state().id(), Port.CLOCK_IN),
            source.state().revision(),
            f.module.state().revision());
    f.runtime.attach(f.module);
    f.runtime
        .state()
        .connect(
            f.player.getUUID(),
            false,
            new Cable(f.module.state().id(), Port.CV_OUT, removed.state().id(), Port.PITCH_IN),
            f.module.state().revision(),
            removed.state().revision());
    f.runtime.attach(f.module);
    f.after(
        () -> {
          h.assertTrue(f.checkpoint().external(), "External clock initially connected");
          h.getLevel().setBlock(removedPos, Blocks.AIR.defaultBlockState(), 3);
        });
    f.after(
        () -> {
          var patch = f.runtime.currentPatch(f.module.state().id());
          h.assertTrue(
              f.checkpoint().external(), "Replacement retains external clock after removal");
          h.assertTrue(
              patch.modules().size() == 2 && patch.cables().size() == 1,
              "Unchanged clock cable survives removal");
          h.assertTrue(
              patch.equals(StateCodec.decode(StateCodec.encode(patch))), "Snapshot roundtrip");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID, timeoutTicks = 200)
  public static void stepPowerAndModeSwitchesControlPlaybackWithoutSpeaker(GameTestHelper h) {
    Fixture f = new Fixture(h);
    f.power(true);
    f.after(
        () -> {
          f.running(false);
          f.mode(true);
        });
    f.after(
        () -> {
          f.running(true);
          f.power(false);
        });
    f.after(
        () -> {
          f.running(false);
          h.assertTrue(
              f.checkpoint().clockEnd() == 0
                  && f.checkpoint().channels().stream().allMatch(c -> c.end() == 0),
              "Loss of power clears outputs");
          f.mode(false);
        });
    f.after(
        () -> {
          f.running(false);
          f.send(Messages.Action.SEQUENCER_START, Parameter.TEMPO, 0);
        });
    f.after(
        () -> {
          f.running(true);
          f.mode(true);
        });
    f.after(
        () -> {
          f.running(false);
          f.power(true);
        });
    f.after(
        () -> {
          f.running(true);
          f.mode(false);
        });
    f.after(
        () -> {
          f.running(true);
          f.power(false);
        });
    f.after(
        () -> {
          f.running(true);
          f.send(Messages.Action.SEQUENCER_STOP, Parameter.TEMPO, 0);
        });
    f.after(
        () -> {
          f.running(false);
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID, timeoutTicks = 200)
  public static void stepRedstoneTransportRejectsButtonsButAllowsResetAndRejectsForeignEdits(
      GameTestHelper h) {
    Fixture f = new Fixture(h);
    var guest =
        FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "RedstoneGuest"));
    guest.setPos(f.module.getBlockPos().getCenter());
    long revision = f.module.state().revision();
    ServerRuntime.enqueue(
        guest,
        new Messages.Control(
            f.module.state().id(), revision, Messages.Action.PARAMETER, mode(), 1));
    f.after(
        () -> {
          h.assertTrue(
              f.module.state().revision() == revision && f.module.state().value(mode()) == 0,
              "Foreign mode edit rejected");
          f.mode(true);
        });
    f.after(
        () -> {
          f.send(Messages.Action.SEQUENCER_START, Parameter.TEMPO, 0);
          ServerRuntime.enqueue(
              f.player,
              new Messages.Control(
                  f.module.state().id(), revision, Messages.Action.PARAMETER, mode(), 0));
        });
    f.after(
        () -> {
          f.running(false);
          h.assertTrue(
              f.module.state().value(mode()) == 1 && f.module.state().revision() == revision + 1,
              "Manual start and stale mode edit rejected");
          f.power(true);
        });
    f.after(
        () -> {
          f.running(true);
          f.send(Messages.Action.SEQUENCER_STOP, Parameter.TEMPO, 0);
        });
    f.after(
        () -> {
          f.running(true);
          h.assertTrue(
              f.module.state().revision() == revision + 1,
              "Manual stop rejected without revision change");
          f.send(Messages.Action.SEQUENCER_RESET, Parameter.TEMPO, 0);
        });
    f.after(
        () -> {
          f.running(true);
          h.assertTrue(f.module.state().revision() == revision + 2, "Reset remains accepted");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID, timeoutTicks = 200)
  public static void stepPoweredReloadReconcilesOnlyWhenEntirePatchLoads(GameTestHelper h) {
    Fixture f = new Fixture(h);
    BlockPos pos = f.module.getBlockPos().east();
    h.getLevel()
        .setBlock(
            pos, ModularCraft.MODULES.get(ModuleKind.STEP_SEQUENCER).get().defaultBlockState(), 3);
    var source = (ModuleEntity) h.getLevel().getBlockEntity(pos);
    source.initialize(f.player.getUUID());
    f.runtime
        .state()
        .connect(
            f.player.getUUID(),
            false,
            new Cable(source.state().id(), Port.CLOCK_OUT, f.module.state().id(), Port.CLOCK_IN),
            source.state().revision(),
            f.module.state().revision());
    f.runtime.attach(f.module);
    f.mode(true);
    f.power(true);
    f.after(
        () -> {
          f.running(true);
          h.assertTrue(
              f.checkpoint().external() && f.checkpoint().channels().getFirst().step() == -1,
              "Redstone does not replace external clock");
          var tag = f.module.saveWithFullMetadata(h.getLevel().registryAccess());
          var restored =
              (ModuleEntity)
                  BlockEntity.loadStatic(
                      f.module.getBlockPos(),
                      f.module.getBlockState(),
                      tag,
                      h.getLevel().registryAccess());
          h.assertTrue(
              restored != null && restored.state().value(mode()) == 1,
              "NBT preserves redstone mode");
          f.runtime.unload(source.state().id());
        });
    f.after(
        () -> {
          f.running(false);
          f.runtime.attach(source);
        });
    f.after(
        () -> {
          f.running(true);
          f.runtime.unload(f.module.state().id());
        });
    f.after(
        () -> {
          f.running(false);
          f.power(false);
          f.runtime.attach(f.module);
        });
    f.after(
        () -> {
          f.running(false);
          f.runtime.unload(f.module.state().id());
          f.power(true);
          f.runtime.attach(f.module);
        });
    f.after(
        () -> {
          f.running(true);
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID, timeoutTicks = 300)
  public static void stepPowerResumePreservesExternalClockPositionAndResetStillWorks(
      GameTestHelper h) {
    Fixture f = new Fixture(h);
    BlockPos pos = f.module.getBlockPos().east();
    h.getLevel()
        .setBlock(
            pos, ModularCraft.MODULES.get(ModuleKind.STEP_SEQUENCER).get().defaultBlockState(), 3);
    var source = (ModuleEntity) h.getLevel().getBlockEntity(pos);
    source.initialize(f.player.getUUID());
    f.runtime
        .state()
        .connect(
            f.player.getUUID(),
            false,
            new Cable(source.state().id(), Port.CLOCK_OUT, f.module.state().id(), Port.CLOCK_IN),
            source.state().revision(),
            f.module.state().revision());
    f.runtime.attach(f.module);
    f.mode(true);
    f.power(true);
    f.after(
        () ->
            ServerRuntime.enqueue(
                f.player,
                new Messages.Control(
                    source.state().id(),
                    source.state().revision(),
                    Messages.Action.SEQUENCER_START,
                    Parameter.TEMPO,
                    0)));
    f.sequence.thenWaitUntil(
        () ->
            h.assertTrue(
                f.checkpoint().channels().getFirst().step() >= 0, "External edge received"));
    f.after(
        () ->
            ServerRuntime.enqueue(
                f.player,
                new Messages.Control(
                    source.state().id(),
                    source.state().revision(),
                    Messages.Action.SEQUENCER_STOP,
                    Parameter.TEMPO,
                    0)));
    int[] next = new int[1];
    f.after(
        () -> {
          next[0] = f.checkpoint().channels().getFirst().next();
          f.power(false);
        });
    f.after(
        () -> {
          f.running(false);
          h.assertTrue(
              f.checkpoint().channels().getFirst().next() == next[0],
              "Power loss retains next step");
          f.power(true);
        });
    f.after(
        () -> {
          f.running(true);
          h.assertTrue(
              f.checkpoint().channels().getFirst().next() == next[0],
              "Power return does not reset or manufacture clock edges");
          f.send(Messages.Action.SEQUENCER_RESET, Parameter.TEMPO, 0);
        });
    f.after(
        () -> {
          f.running(true);
          h.assertTrue(
              f.checkpoint().channels().stream().allMatch(c -> c.next() == 0 && c.step() == -1),
              "Reset arms first step while waiting for an external clock");
          h.succeed();
        });
  }

  @GameTest(template = "empty", templateNamespace = ModularCraft.ID, timeoutTicks = 200)
  public static void stepAnyNonzeroStrengthAndSustainedPowerPreserveClockProgress(
      GameTestHelper h) {
    Fixture f = new Fixture(h);
    // Place dust first so its placement update finishes before setting an exact strength.
    h.getLevel().setBlock(f.power, Blocks.REDSTONE_WIRE.defaultBlockState(), 2);
    h.getLevel()
        .setBlock(
            f.power,
            Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, 1),
            2);
    f.mode(true);
    long[] firstClock = new long[1];
    f.after(
        () -> {
          f.running(true);
          firstClock[0] = f.checkpoint().lastClock();
        });
    f.sequence.thenWaitUntil(
        () ->
            h.assertTrue(
                f.checkpoint().lastClock() > firstClock[0]
                    && f.checkpoint().channels().getFirst().step() > 0,
                "Monotonic musical clock advances without reset"));
    f.after(
        () -> {
          f.running(true);
          h.assertTrue(
              f.checkpoint().lastClock() > firstClock[0], "Sustained power advances the clock");
          h.assertTrue(
              f.checkpoint().channels().getFirst().step() > 0,
              "Sustained power does not reset position");
          h.getLevel()
              .setBlock(
                  f.power,
                  Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, 15),
                  2);
        });
    f.after(
        () -> {
          f.running(true);
          f.power(false);
        });
    f.after(
        () -> {
          f.running(false);
          h.succeed();
        });
  }
}
