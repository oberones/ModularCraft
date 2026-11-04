package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.KeyboardInput;
import com.malevolentgods.modularcraft.domain.KeyboardPerformance;
import com.malevolentgods.modularcraft.domain.KeyboardSession;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.Envelope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KeyboardTest {
  @Test
  void sessionsRejectReorderingAndPreserveTapSpacingWithinOneTick() {
    KeyboardSession session = new KeyboardSession(UUID.randomUUID());
    Assertions.assertTrue(session.accept(1));
    Assertions.assertFalse(session.accept(1));
    Assertions.assertFalse(session.accept(0));
    Assertions.assertTrue(session.accept(2));
    long down = session.schedule(1920, 1_000_000_000L);
    long up = session.schedule(1920, 1_010_000_000L);
    Assertions.assertEquals(480, up - down);
  }

  @Test
  void heldInputsUseLastNotePriorityWithoutRepeatingOrLosingOtherSources() {
    KeyboardInput input = new KeyboardInput();
    Assertions.assertTrue(input.press(1, 0));
    Assertions.assertFalse(input.press(1, 0));
    input.press(2, 4);
    Assertions.assertEquals(4, input.note());
    input.press(3, 4);
    input.release(3);
    Assertions.assertEquals(4, input.note());
    input.release(2);
    Assertions.assertEquals(0, input.note());
    input.clear();
    Assertions.assertEquals(-1, input.note());
  }

  @Test
  void pianoGeometryGivesBlackKeysPriorityAndReleasesOutside() {
    Assertions.assertEquals(1, KeyboardInput.hit(25, 10, 210, 80));
    Assertions.assertEquals(0, KeyboardInput.hit(25, 70, 210, 80));
    Assertions.assertEquals(11, KeyboardInput.hit(209, 70, 210, 80));
    Assertions.assertEquals(-1, KeyboardInput.hit(210, 10, 210, 80));
    Assertions.assertEquals(-1, KeyboardInput.hit(10, -1, 210, 80));
  }

  @Test
  void pitchOctavesAndPortsHaveExplicitContracts() {
    Assertions.assertEquals(440, KeyboardState.frequency(69), 1e-10);
    Assertions.assertEquals(0, KeyboardState.volts(60), 1e-10);
    Assertions.assertEquals(1, KeyboardState.volts(72), 1e-10);
    Assertions.assertEquals(24, KeyboardState.note(1, 0));
    Assertions.assertEquals(107, KeyboardState.note(7, 11));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Parameter.OCTAVE.validate(1.5));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Parameter.OCTAVE.validate(8));
    Assertions.assertTrue(Port.CV_OUT.connects(Port.PITCH_IN));
    Assertions.assertTrue(Port.GATE_OUT.connects(Port.GATE_IN));
    Assertions.assertFalse(Port.CV_OUT.connects(Port.CUTOFF_IN));
    Assertions.assertFalse(Port.GATE_OUT.connects(Port.PITCH_IN));
    Assertions.assertFalse(Port.ENVELOPE_OUT.connects(Port.GATE_IN));
  }

  @Test
  void leaseRejectsCompetitorsAndRetainsReleasedPitch() {
    KeyboardPerformance keyboard = new KeyboardPerformance(4);
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    keyboard.play(a, 9, 4, 0);
    Assertions.assertEquals(new KeyboardState(69, true), keyboard.state());
    Assertions.assertThrows(IllegalArgumentException.class, () -> keyboard.play(b, 0, 4, 1));
    keyboard.transpose(5);
    Assertions.assertEquals(81, keyboard.state().note());
    keyboard.release(b);
    Assertions.assertTrue(keyboard.state().gate());
    keyboard.release(a);
    Assertions.assertEquals(new KeyboardState(81, false), keyboard.state());
    keyboard.play(b, 0, 4, 10);
    Assertions.assertTrue(keyboard.expire(750_000_011L));
    Assertions.assertFalse(keyboard.state().gate());
  }

  @Test
  void retriggerStartsFromCurrentLevelAndKeepsReleaseContinuous() {
    Envelope env = new Envelope();
    env.gate(true, 0);
    double before = env.value(240);
    env.retrigger(240);
    Assertions.assertEquals(before, env.value(240), 1e-12);
    Assertions.assertEquals(Envelope.Stage.ATTACK, env.checkpoint(240).stage());
    env.gate(false, 480);
    Assertions.assertTrue(env.value(481) > 0);
    Assertions.assertEquals(0, env.value(20000));
  }

  @Test
  void keyboardSnapshotAndWorldRoundtripKeepOnlyDurableOctave() {
    UUID owner = UUID.randomUUID();
    ModuleState module =
        ModuleState.create(UUID.randomUUID(), owner, ModuleKind.KEYBOARD, 0, 0, 0)
            .withValue(Parameter.OCTAVE, 5);
    PatchSnapshot snapshot =
        new PatchSnapshot(
            module.id(),
            0,
            1,
            200,
            List.of(module),
            List.of(),
            Map.of(),
            Map.of(module.id(), new KeyboardState(81, true)));
    Assertions.assertEquals(snapshot, StateCodec.decode(StateCodec.encode(snapshot)));
    WorldState world = new WorldState();
    world.place(module);
    WorldState restored = StateCodec.load(StateCodec.save(world, List.of())).state();
    Assertions.assertEquals(5, restored.get(module.id()).value(Parameter.OCTAVE));
    Assertions.assertFalse(restored.get(module.id()).loaded());
    Assertions.assertThrows(IllegalArgumentException.class, () -> new KeyboardState(108, true));
  }
}
