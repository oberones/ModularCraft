package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SequencerCodecTest {
  @Test
  void settingsSaveButOnlySnapshotsCarryPerformanceAndPendingEdits() {
    ModuleState module =
        ModuleState.create(
            UUID.randomUUID(), UUID.randomUUID(), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 64, 0);
    Sequencer seq = new Sequencer(SequencerSettings.from(module), 0);
    seq.start(0);
    seq.advance(0);
    module = module.withValue(Parameter.A_STEPS, 7);
    seq.configure(SequencerSettings.from(module), 100);
    var checkpoint = seq.checkpoint(100);
    PatchSnapshot patch =
        new PatchSnapshot(
            module.id(),
            1,
            1,
            100,
            List.of(module),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(module.id(), checkpoint),
            Map.of());
    var decoded = StateCodec.decode(StateCodec.encode(patch));
    Assertions.assertEquals(checkpoint, decoded.sequencers().get(module.id()));
    Sequencer restored =
        new Sequencer(SequencerSettings.from(module), decoded.sequencers().get(module.id()));
    Assertions.assertTrue(restored.pending());
    restored.advance(480);
    restored.advance(6000);
    Assertions.assertEquals(7, restored.active().channels().getFirst().steps());
    WorldState world = new WorldState();
    world.place(module);
    var loaded = StateCodec.load(StateCodec.save(world, List.of()));
    Assertions.assertEquals(module.values(), loaded.state().get(module.id()).values());
    Assertions.assertEquals(
        module.values(), StateCodec.moduleBytes(StateCodec.moduleBytes(module)).values());
    byte[] old = StateCodec.encode(patch);
    old[5] = 4;
    Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(old));
  }

  @Test
  void checkpointIdentityAndTimeMustBelongToTheSnapshot() {
    ModuleState module =
        ModuleState.create(
            UUID.randomUUID(), UUID.randomUUID(), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 64, 0);
    var checkpoint = new Sequencer(SequencerSettings.from(module), 0).checkpoint(0);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new PatchSnapshot(
                module.id(),
                0,
                0,
                0,
                List.of(module),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(UUID.randomUUID(), checkpoint),
                Map.of()));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new PatchSnapshot(
                module.id(),
                0,
                0,
                10,
                List.of(module),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(module.id(), checkpoint),
                Map.of()));
  }

  @Test
  void externalClockCheckpointMustMatchItsCable() {
    UUID owner = UUID.randomUUID();
    ModuleState source =
        ModuleState.create(UUID.randomUUID(), owner, ModuleKind.KEYBOARD, 0, 64, 0);
    ModuleState target =
        ModuleState.create(UUID.randomUUID(), owner, ModuleKind.EUCLIDEAN_SEQUENCER, 1, 64, 0);
    var cable =
        new com.malevolentgods.modularcraft.domain.Cable(
            source.id(),
            com.malevolentgods.modularcraft.domain.Port.GATE_OUT,
            target.id(),
            com.malevolentgods.modularcraft.domain.Port.CLOCK_IN);
    var wrong = new Sequencer(SequencerSettings.from(target), 0).checkpoint(0);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new PatchSnapshot(
                target.id(),
                0,
                0,
                0,
                List.of(source, target),
                List.of(cable),
                Map.of(),
                Map.of(),
                Map.of(target.id(), wrong),
                Map.of()));
  }

  @Test
  void everyTruncationAndInvalidClockCheckpointIsRejected() {
    ModuleState module =
        ModuleState.create(
            UUID.randomUUID(), UUID.randomUUID(), ModuleKind.EUCLIDEAN_SEQUENCER, 0, 64, 0);
    var settings = SequencerSettings.from(module);
    var seq = new Sequencer(settings, 0);
    seq.start(0);
    seq.advance(0);
    var checkpoint = seq.checkpoint(0);
    var patch =
        new PatchSnapshot(
            module.id(),
            0,
            0,
            0,
            List.of(module),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(module.id(), checkpoint),
            Map.of());
    byte[] bytes = StateCodec.encode(patch);
    for (int length = 0; length < bytes.length; length++) {
      byte[] truncated = java.util.Arrays.copyOf(bytes, length);
      Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(truncated));
    }
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new Sequencer.Checkpoint(
                0,
                settings,
                true,
                false,
                false,
                false,
                Long.MAX_VALUE,
                0,
                0,
                480,
                0,
                checkpoint.channels()));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new Sequencer.Checkpoint(
                0, settings, true, false, false, false, 6000, 0, 1, 480, 0, checkpoint.channels()));
  }
}
