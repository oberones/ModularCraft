package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AmpAudioTest {
  private static final UUID OWNER = new UUID(0, 1);

  static ModuleState module(int id, ModuleKind kind) {
    return ModuleState.create(new UUID(1, id), OWNER, kind, id % 4 * 2, 64, id / 4 * 2);
  }

  static PatchSnapshot fourVoices(int selected, int patch) {
    List<ModuleState> modules = new ArrayList<>();
    List<Cable> cables = new ArrayList<>();
    Map<UUID, Envelope.Checkpoint> envelopes = new HashMap<>();
    Map<UUID, Boolean> gates = new HashMap<>();
    ModuleState seq = module(0, ModuleKind.EUCLIDEAN_SEQUENCER);
    ModuleState mixer = module(9, ModuleKind.MIXER);
    ModuleState speaker = module(10, ModuleKind.SPEAKER).runtime(true, true);
    for (int i = 0; i < 4; i++) {
      seq =
          seq.withValue(SequencerSettings.parameter(i, 1), i == selected || selected < 0 ? 16 : 0);
      ModuleState osc =
          module(1 + i, ModuleKind.OSCILLATOR).withValue(Parameter.FREQUENCY, 110 + 110 * i);
      ModuleState amp = module(5 + i, ModuleKind.AMP).withValue(Parameter.RELEASE, .001);
      modules.add(osc);
      modules.add(amp);
      cables.add(
          new Cable(
              seq.id(),
              List.of(Port.TRIGGER_A, Port.TRIGGER_B, Port.TRIGGER_C, Port.TRIGGER_D).get(i),
              amp.id(),
              Port.GATE_IN));
      cables.add(new Cable(osc.id(), Port.AUDIO_OUT, amp.id(), Port.AUDIO_IN));
      cables.add(new Cable(amp.id(), Port.AUDIO_OUT, mixer.id(), MixerSettings.INPUTS.get(i)));
      Envelope envelope = new Envelope();
      envelope.configure(.01, .2, .7, .001, 0);
      envelopes.put(amp.id(), envelope.checkpoint(0));
      gates.put(amp.id(), false);
    }
    modules.add(seq);
    modules.add(mixer);
    modules.add(speaker);
    cables.add(new Cable(mixer.id(), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN));
    Sequencer rhythm = new Sequencer(SequencerSettings.from(seq), 0);
    rhythm.start(0);
    PatchSnapshot result =
        new PatchSnapshot(
            seq.id(),
            0,
            1,
            0,
            modules,
            cables,
            envelopes,
            Map.of(),
            Map.of(seq.id(), rhythm.checkpoint(0)),
            gates);
    if (patch == 0) return result;
    Map<UUID, UUID> ids = new HashMap<>();
    for (ModuleState m : modules)
      ids.put(m.id(), new UUID(patch + 1, m.id().getLeastSignificantBits()));
    List<ModuleState> moved =
        modules.stream()
            .map(
                m ->
                    new ModuleState(
                        ids.get(m.id()),
                        m.owner(),
                        m.kind(),
                        m.dimension(),
                        m.x() + patch % 2 * 8,
                        m.y(),
                        m.z() + patch / 2 * 8,
                        m.revision(),
                        m.values(),
                        true,
                        m.enabled()))
            .toList();
    Map<UUID, Envelope.Checkpoint> movedEnvelopes = new HashMap<>();
    Map<UUID, Boolean> movedGates = new HashMap<>();
    envelopes.forEach((id, value) -> movedEnvelopes.put(ids.get(id), value));
    gates.forEach((id, value) -> movedGates.put(ids.get(id), value));
    return new PatchSnapshot(
        ids.get(seq.id()),
        0,
        1,
        0,
        moved,
        cables.stream()
            .map(
                c ->
                    new Cable(ids.get(c.source()), c.output(), ids.get(c.destination()), c.input()))
            .toList(),
        movedEnvelopes,
        Map.of(),
        Map.of(ids.get(seq.id()), rhythm.checkpoint(0)),
        movedGates);
  }

  @Test
  void allFourChannelsIndependentlyArticulateOneSpeaker() {
    for (int channel = 0; channel < 4; channel++) {
      PatchSnapshot patch = StateCodec.decode(StateCodec.encode(fourVoices(channel, 0)));
      Assertions.assertEquals(11, patch.modules().size());
      Assertions.assertEquals(13, patch.cables().size());
      double[] audio = render(new RenderGraph(List.of(patch)), 7000);
      Assertions.assertTrue(energy(audio, 100, 480) > .00001, "voice " + channel);
      Assertions.assertEquals(0, energy(audio, 1000, 6000), 1e-12);
      Assertions.assertTrue(energy(audio, 6100, 6480) > .00001);
    }
  }

  private static PatchSnapshot simple(
      boolean drone, boolean external, boolean externalHeld, boolean speaker, boolean audio) {
    ModuleState osc = module(1, ModuleKind.OSCILLATOR);
    ModuleState amp = module(2, ModuleKind.AMP).withValue(Parameter.DRONE, drone ? 1 : 0);
    ModuleState env = module(3, ModuleKind.ENVELOPE);
    ModuleState out = module(4, ModuleKind.SPEAKER).runtime(true, true);
    List<Cable> cables = new ArrayList<>();
    if (audio) cables.add(new Cable(osc.id(), Port.AUDIO_OUT, amp.id(), Port.AUDIO_IN));
    if (speaker) cables.add(new Cable(amp.id(), Port.AUDIO_OUT, out.id(), Port.AUDIO_IN));
    if (external) cables.add(new Cable(env.id(), Port.ENVELOPE_OUT, amp.id(), Port.GAIN_IN));
    Envelope envelope = new Envelope();
    envelope.configure(.001, .001, 1, .001, 0);
    envelope.gate(externalHeld, 0);
    return new PatchSnapshot(
        amp.id(),
        0,
        1,
        0,
        speaker ? List.of(osc, amp, env, out) : List.of(osc, amp, env),
        cables,
        Map.of(amp.id(), new Envelope().checkpoint(0), env.id(), envelope.checkpoint(0)));
  }

  @Test
  void precedenceAndDisconnectedInputsAreExplicit() {
    for (boolean[] settings :
        new boolean[][] {
          {false, false, false, true, true},
          {true, true, false, true, true},
          {true, false, false, false, true},
          {true, false, false, true, false}
        }) {
      double[] samples =
          render(
              new RenderGraph(
                  List.of(simple(settings[0], settings[1], settings[2], settings[3], settings[4]))),
              2048);
      Assertions.assertEquals(0, energy(samples, 0, samples.length), 1e-12);
    }
    for (boolean drone : new boolean[] {false, true}) {
      double[] samples =
          render(new RenderGraph(List.of(simple(drone, true, true, true, true))), 2048);
      Assertions.assertTrue(energy(samples, 1000, 2048) > .0001);
    }
    Assertions.assertTrue(
        energy(
                render(new RenderGraph(List.of(simple(true, false, false, true, true))), 2048),
                1000,
                2048)
            > .0001);
  }

  @Test
  void droneAndGainSourceChangesRampAcrossGraphAdoption() {
    RenderGraph before = new RenderGraph(List.of(simple(true, false, false, true, true)));
    render(before, 2048);
    RenderGraph after = new RenderGraph(List.of(simple(false, false, false, true, true)));
    after.adopt(before);
    float[] output = new float[2048];
    after.process(2048, listener(), output, 1024);
    double early = 0, late = 0;
    for (int i = 0; i < 1024; i++) {
      if (i < 100) early += Math.abs(output[i * 2]);
      if (i >= 480) late += Math.abs(output[i * 2]);
    }
    Assertions.assertTrue(early > .001);
    Assertions.assertEquals(0, late, 1e-12);
  }

  @Test
  void fourAmpsFitButThirdSpeakerIsRejected() {
    PatchSnapshot patch = fourVoices(-1, 0);
    WorldState world = new WorldState();
    patch.modules().forEach(world::place);
    for (Cable c : patch.cables())
      world.connect(
          OWNER, false, c, world.get(c.source()).revision(), world.get(c.destination()).revision());
    ModuleState second = module(11, ModuleKind.SPEAKER), third = module(12, ModuleKind.SPEAKER);
    world.place(second);
    world.place(third);
    ModuleState mixer = world.get(module(9, ModuleKind.MIXER).id());
    world.connect(
        OWNER,
        false,
        new Cable(mixer.id(), Port.AUDIO_OUT, second.id(), Port.AUDIO_IN),
        mixer.revision(),
        0);
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            world.connect(
                OWNER,
                false,
                new Cable(mixer.id(), Port.AUDIO_OUT, third.id(), Port.AUDIO_IN),
                world.get(mixer.id()).revision(),
                0));
  }

  @Test
  void fourFullVoicePatchesHaveBoundedDspAndNoSteadyAllocation() {
    List<PatchSnapshot> patches = new ArrayList<>();
    for (int patch = 0; patch < 4; patch++) {
      PatchSnapshot original = fourVoices(-1, patch);
      List<ModuleState> modules =
          new ArrayList<>(
              original.modules().stream()
                  .map(
                      m ->
                          m.kind() == ModuleKind.OSCILLATOR
                              ? m.withValue(Parameter.DETUNE, 100)
                              : m)
                  .toList());
      List<Cable> cables = new ArrayList<>(original.cables());
      for (int voice = 0; voice < 4; voice++) {
        int index = 11 + voice;
        ModuleState filter =
            ModuleState.create(
                    new UUID(patch + 1, index),
                    OWNER,
                    ModuleKind.FILTER,
                    patch % 2 * 8 + index % 4 * 2,
                    64,
                    patch / 2 * 8 + index / 4 * 2)
                .withValue(Parameter.RESONANCE, 25);
        modules.add(filter);
        UUID osc = new UUID(patch + 1, voice + 1), amp = new UUID(patch + 1, voice + 5);
        cables.remove(new Cable(osc, Port.AUDIO_OUT, amp, Port.AUDIO_IN));
        cables.add(new Cable(osc, Port.AUDIO_OUT, filter.id(), Port.AUDIO_IN));
        cables.add(new Cable(filter.id(), Port.AUDIO_OUT, amp, Port.AUDIO_IN));
      }
      ModuleState speaker =
          ModuleState.create(
                  new UUID(patch + 1, 15),
                  OWNER,
                  ModuleKind.SPEAKER,
                  patch % 2 * 8 + 6,
                  64,
                  patch / 2 * 8 + 6)
              .runtime(true, true);
      modules.add(speaker);
      cables.add(new Cable(new UUID(patch + 1, 9), Port.AUDIO_OUT, speaker.id(), Port.AUDIO_IN));
      Assertions.assertEquals(16, modules.size());
      Assertions.assertEquals(18, cables.size());
      patches.add(
          new PatchSnapshot(
              original.id(),
              0,
              1,
              0,
              modules,
              cables,
              original.envelopes(),
              Map.of(),
              original.sequencers(),
              original.baseGates()));
    }
    MeasurementTest.benchmark(patches);
  }

  private static RenderGraph.Listener listener() {
    return new RenderGraph.Listener("minecraft:overworld", 4.5, 64.5, 4.5, 0, 1);
  }

  static double[] render(RenderGraph graph, int count) {
    double[] result = new double[count];
    float[] block = new float[256];
    for (int i = 0; i < count; i += 128) {
      int frames = Math.min(128, count - i);
      graph.process(i, listener(), block, frames);
      for (int j = 0; j < frames; j++) {
        Assertions.assertTrue(Float.isFinite(block[j * 2]));
        result[i + j] = block[j * 2];
      }
    }
    return result;
  }

  private static double energy(double[] samples, int start, int end) {
    double result = 0;
    for (int i = start; i < end; i++) result += samples[i] * samples[i];
    return result;
  }
}
