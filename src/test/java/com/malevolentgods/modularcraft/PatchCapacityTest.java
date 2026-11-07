package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.Envelope;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PatchCapacityTest {
  private static final UUID OWNER = new UUID(0, 1);

  private static List<ModuleState> modules(int count, ModuleKind kind) {
    List<ModuleState> result = new ArrayList<>();
    for (int i = 0; i < count; i++)
      result.add(ModuleState.create(new UUID(1, i), OWNER, kind, i % 5 * 2, 64, i / 5 * 2));
    return result;
  }

  private static Cable cable(List<ModuleState> modules, int from, int to, Port input) {
    return new Cable(modules.get(from).id(), Port.AUDIO_OUT, modules.get(to).id(), input);
  }

  private static void connect(WorldState world, Cable cable) {
    world.connect(
        OWNER,
        false,
        cable,
        world.get(cable.source()).revision(),
        world.get(cable.destination()).revision());
  }

  @Test
  void growsThroughTwentyThenRejectsTwentyOneWithoutMutation() {
    WorldState world = new WorldState();
    List<ModuleState> modules = modules(21, ModuleKind.FILTER);
    modules.forEach(world::place);
    for (int i = 1; i < 20; i++) {
      connect(world, cable(modules, i - 1, i, Port.AUDIO_IN));
      Assertions.assertEquals(i + 1, world.component(modules.getFirst().id()).size());
    }
    List<ModuleState> before = world.modules();
    List<Cable> links = world.cables();
    long revision = world.revision();
    var failure =
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> connect(world, cable(modules, 19, 20, Port.AUDIO_IN)));
    Assertions.assertEquals("patch_modules:20", failure.getMessage());
    Assertions.assertEquals(before, world.modules());
    Assertions.assertEquals(links, world.cables());
    Assertions.assertEquals(revision, world.revision());
    Cable bridge = cable(modules, 9, 10, Port.AUDIO_IN);
    world.disconnect(
        OWNER,
        false,
        bridge,
        world.get(bridge.source()).revision(),
        world.get(bridge.destination()).revision());
    Assertions.assertEquals(10, world.component(modules.getFirst().id()).size());
    connect(world, bridge);
    Assertions.assertEquals(20, world.component(modules.getFirst().id()).size());
  }

  @Test
  void twentyModuleWorldAndSnapshotRoundtrip() {
    WorldState world = new WorldState();
    List<ModuleState> modules = modules(20, ModuleKind.FILTER);
    modules.forEach(world::place);
    for (int i = 1; i < 20; i++) connect(world, cable(modules, i - 1, i, Port.AUDIO_IN));
    var loaded = StateCodec.load(StateCodec.save(world, List.of()));
    Assertions.assertTrue(loaded.quarantined().isEmpty());
    Assertions.assertEquals(20, loaded.state().component(modules.getFirst().id()).size());
    Assertions.assertEquals(world.cables(), loaded.state().cables());
    for (ModuleState module : world.modules())
      Assertions.assertEquals(module.runtime(false, false), loaded.state().get(module.id()));
    PatchSnapshot snapshot =
        new PatchSnapshot(
            modules.getFirst().id(),
            world.revision(),
            1,
            0,
            world.modules(),
            world.cables(),
            Map.of());
    Assertions.assertEquals(snapshot, StateCodec.decode(StateCodec.encode(snapshot)));
  }

  @Test
  void twentyCheckpointEntriesRoundtripWithinByteBudget() {
    for (ModuleKind kind :
        List.of(
            ModuleKind.AMP,
            ModuleKind.ENVELOPE,
            ModuleKind.KEYBOARD,
            ModuleKind.EUCLIDEAN_SEQUENCER,
            ModuleKind.LFO)) {
      List<ModuleState> modules = modules(20, kind);
      Map<UUID, Envelope.Checkpoint> envelopes = new HashMap<>();
      Map<UUID, KeyboardState> keyboards = new HashMap<>();
      for (ModuleState module : modules) {
        if (kind.hasEnvelope()) envelopes.put(module.id(), new Envelope().checkpoint(0));
        if (kind == ModuleKind.KEYBOARD) keyboards.put(module.id(), new KeyboardState(60, false));
      }
      PatchSnapshot snapshot =
          new PatchSnapshot(
              modules.getFirst().id(), 0, 0, 0, modules, List.of(), envelopes, keyboards);
      byte[] bytes = StateCodec.encode(snapshot);
      Assertions.assertTrue(bytes.length <= StateCodec.MAX_PATCH);
      Assertions.assertEquals(snapshot, StateCodec.decode(bytes));
      System.out.println(
          "PATCH_CAPACITY snapshot kind=" + kind + " modules=20 bytes=" + bytes.length);
    }
  }

  @Test
  void refusesOldProtocolAndOversizedCollectionsBeforeReadingEntries() {
    PatchSnapshot empty = new PatchSnapshot(OWNER, 0, 0, 0, List.of(), List.of(), Map.of());
    byte[] encoded = StateCodec.encode(empty);
    Assertions.assertEquals(11, ByteBuffer.wrap(encoded).getShort(4));
    byte[] old = encoded.clone();
    ByteBuffer.wrap(old).putShort(4, (short) 10);
    Assertions.assertEquals(
        "protocol",
        Assertions.assertThrows(IllegalArgumentException.class, () -> StateCodec.decode(old))
            .getMessage());
    // Empty snapshot: header, then module/cable/envelope/keyboard/sequencer/LFO/base-gate counts.
    for (int collection = 0; collection < 7; collection++) {
      byte[] malformed = encoded.clone();
      ByteBuffer.wrap(malformed).putShort(46 + collection * 2, (short) (collection == 1 ? 25 : 21));
      Assertions.assertEquals(
          "count",
          Assertions.assertThrows(
                  IllegalArgumentException.class, () -> StateCodec.decode(malformed))
              .getMessage());
    }
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            new PatchSnapshot(OWNER, 0, 0, 0, modules(21, ModuleKind.FILTER), List.of(), Map.of()));
  }

  @Test
  void twentyModulesStillHaveTwentyFourCableLimit() {
    WorldState world = new WorldState();
    List<ModuleState> modules = modules(20, ModuleKind.MIXER);
    modules.forEach(world::place);
    for (int i = 1; i < 20; i++) connect(world, cable(modules, i - 1, i, Port.AUDIO_IN_1));
    for (int i = 1; i <= 5; i++) connect(world, cable(modules, i - 1, i, Port.AUDIO_IN_2));
    Assertions.assertEquals(24, world.cables().size());
    Assertions.assertEquals(
        "patch_cables:24",
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> connect(world, cable(modules, 5, 6, Port.AUDIO_IN_2)))
            .getMessage());
    Assertions.assertEquals(24, world.cables().size());
  }
}
