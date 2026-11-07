package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.Graph;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.LfoSettings;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.domain.WorldState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prepared off audio thread. No allocation, I/O, locks or Minecraft objects in process/adopt. Each
 * source is evaluated once per sample and shared by all destinations.
 */
public final class RenderGraph {
  public record Listener(
      String dimension, double x, double y, double z, double yaw, double volume) {}

  private static final class Node {
    final ModuleState module;
    UnisonSaw oscillator;
    PulseOscillator pulse;
    final Filter filter = new Filter();
    final Smoother[] parameters = new Smoother[Parameter.values().length];
    final Smoother left = new Smoother(0), right = new Smoother(0);
    Envelope envelope;
    Lfo lfo;
    Mixer mixer;
    final int[] mixerInputs = {-1, -1, -1, -1};
    final Smoother cutoffCv = new Smoother(0),
        resonanceCv = new Smoother(0),
        gainCv = new Smoother(1);
    final Smoother gainTransition = new Smoother(1);
    double gainFrom, lastGain;
    int audio = -1, control = -1, resonance = -1, pitch = -1, patch;

    Node(ModuleState module, Envelope.Checkpoint checkpoint) {
      this.module = module;
      for (Parameter p : module.kind().parameters)
        parameters[p.ordinal()] = new Smoother(module.value(p));
      if (module.kind() == ModuleKind.MIXER) mixer = new Mixer(module);
      if (checkpoint != null) envelope = new Envelope(checkpoint);
      else envelope = new Envelope();
    }

    double next(Parameter parameter) {
      return parameters[parameter.ordinal()].next();
    }
  }

  private final Node[] nodes;
  private final double[] signals;
  private final ControlTimeline[] timelines;
  private final List<Cable> routing;

  public RenderGraph(List<PatchSnapshot> patches) {
    if (patches.size() > 4) throw new IllegalArgumentException("active_patches");
    List<ModuleState> modules = new ArrayList<>();
    List<Cable> cables = new ArrayList<>();
    Map<UUID, Envelope.Checkpoint> checkpoints = new HashMap<>();
    long sample = 0;
    for (PatchSnapshot patch : patches) {
      modules.addAll(patch.modules());
      cables.addAll(patch.cables());
      checkpoints.putAll(patch.envelopes());
      sample = Math.max(sample, patch.sample());
    }
    if (modules.size() > 4 * WorldState.MAX_PATCH_MODULES)
      throw new IllegalArgumentException("active_modules");
    routing = List.copyOf(cables);
    timelines = new ControlTimeline[patches.size()];
    List<ModuleState> ordered = Graph.compile(modules, cables);
    nodes = new Node[ordered.size()];
    signals = new double[nodes.length];
    Map<UUID, Integer> indices = new HashMap<>();
    for (int i = 0; i < nodes.length; i++) {
      ModuleState m = ordered.get(i);
      nodes[i] = new Node(m, checkpoints.get(m.id()));
      indices.put(m.id(), i);
    }
    for (int p = 0; p < patches.size(); p++) {
      PatchSnapshot patch = patches.get(p);
      Map<UUID, Envelope> envelopes = new HashMap<>();
      Map<UUID, Sequencer> sequencers = new HashMap<>();
      Map<UUID, Lfo> lfos = new HashMap<>();
      for (ModuleState module : patch.modules()) {
        Node node = nodes[indices.get(module.id())];
        node.patch = p;
        if (module.kind() == ModuleKind.LFO) {
          node.lfo = new Lfo(LfoSettings.from(module), patch.lfos().get(module.id()));
          lfos.put(module.id(), node.lfo);
        }
        if (module.kind().hasEnvelope()) envelopes.put(module.id(), node.envelope);
        if (module.kind() == ModuleKind.EUCLIDEAN_SEQUENCER)
          sequencers.put(
              module.id(),
              new Sequencer(SequencerSettings.from(module), patch.sequencers().get(module.id())));
      }
      timelines[p] =
          new ControlTimeline(
              patch.modules(),
              patch.cables(),
              sequencers,
              envelopes,
              patch.baseGates(),
              patch.keyboards(),
              lfos,
              patch.sample());
    }
    Map<UUID, KeyboardState> keyboards = new HashMap<>();
    for (PatchSnapshot patch : patches) keyboards.putAll(patch.keyboards());
    for (Cable c : cables) {
      Node n = nodes[indices.get(c.destination())];
      int mixerInput = MixerSettings.INPUTS.indexOf(c.input());
      if (mixerInput >= 0) n.mixerInputs[mixerInput] = indices.get(c.source());
      if (c.input() == Port.AUDIO_IN) n.audio = indices.get(c.source());
      else if (c.input() == Port.RESONANCE_IN) n.resonance = indices.get(c.source());
      else if (c.input() == Port.PITCH_IN) n.pitch = indices.get(c.source());
      else if (c.input() == Port.CUTOFF_IN || c.input() == Port.GAIN_IN)
        n.control = indices.get(c.source());
    }
    for (Node n : nodes)
      if (n.module.kind().isOscillator()) {
        double hz = n.module.value(Parameter.FREQUENCY);
        if (n.pitch >= 0) {
          ModuleState source = nodes[n.pitch].module;
          KeyboardState key =
              keyboards.getOrDefault(
                  source.id(),
                  new KeyboardState(
                      KeyboardState.note((int) source.value(Parameter.OCTAVE), 0), false));
          hz = KeyboardState.frequency(key.note());
        }
        if (n.module.kind() == ModuleKind.OSCILLATOR)
          n.oscillator = new UnisonSaw(hz, n.module.value(Parameter.DETUNE), sample);
        else n.pulse = new PulseOscillator(hz, n.module.value(Parameter.PULSE_WIDTH) / 100, sample);
      }
  }

  /** Transfer continuous state at the audio block boundary, without mutating the old graph. */
  public void adopt(RenderGraph previous) {
    for (Node node : nodes)
      for (Node old : previous.nodes)
        if (node.module.id().equals(old.module.id()) && node.module.kind() == old.module.kind()) {
          if (node.mixer != null) node.mixer.adopt(old.mixer);
          if (node.oscillator != null) node.oscillator.adopt(old.oscillator);
          if (node.pulse != null) node.pulse.adopt(old.pulse);
          node.filter.copyFrom(old.filter);
          node.cutoffCv.reset(old.cutoffCv.current());
          node.resonanceCv.reset(old.resonanceCv.current());
          node.gainCv.copyFrom(old.gainCv);
          node.lastGain = old.lastGain;
          node.gainFrom = old.gainFrom;
          node.gainTransition.copyFrom(old.gainTransition);
          if (node.module.kind() == ModuleKind.AMP) {
            UUID source = node.control < 0 ? null : nodes[node.control].module.id();
            UUID oldSource = old.control < 0 ? null : previous.nodes[old.control].module.id();
            if (!java.util.Objects.equals(source, oldSource)
                || (source == null
                    && node.module.value(Parameter.DRONE) != old.module.value(Parameter.DRONE))) {
              node.gainFrom = old.lastGain;
              node.gainTransition.reset(0);
              node.gainTransition.target(1);
            }
          }
          for (Parameter p : node.module.kind().parameters) {
            node.parameters[p.ordinal()].reset(old.parameters[p.ordinal()].current());
            node.parameters[p.ordinal()].target(node.module.value(p));
          }
          node.left.reset(old.left.current());
          node.right.reset(old.right.current());
          break;
        }
  }

  public boolean sameTopology(RenderGraph previous) {
    if (!routing.equals(previous.routing)) return false;
    if (nodes.length != previous.nodes.length) return false;
    for (int i = 0; i < nodes.length; i++)
      if (!nodes[i].module.id().equals(previous.nodes[i].module.id())
          || nodes[i].module.kind() != previous.nodes[i].module.kind()
          || nodes[i].audio != previous.nodes[i].audio
          || nodes[i].control != previous.nodes[i].control
          || nodes[i].pitch != previous.nodes[i].pitch) return false;
    return true;
  }

  /** UI-thread snapshot of the current graph only; retired crossfade graphs are never exposed. */
  public MixerMeter.Reading meter(UUID id, long now) {
    for (Node node : nodes)
      if (node.module.id().equals(id) && node.mixer != null) return node.mixer.meter.read(now);
    return null;
  }

  /** Writes 128 interleaved stereo float frames before shared DC blocking/limiting. */
  public void process(long sample, Listener listener, float[] stereo) {
    process(sample, listener, stereo, 128);
  }

  public void process(long sample, Listener listener, float[] stereo, int frames) {
    for (Node n : nodes)
      if (n.module.kind() == ModuleKind.SPEAKER) {
        double dx = n.module.x() + .5 - listener.x,
            dy = n.module.y() + .5 - listener.y,
            dz = n.module.z() + .5 - listener.z;
        double yaw = Math.toRadians(listener.yaw);
        double pan =
            Output.pan(
                -Math.cos(yaw) * dx - Math.sin(yaw) * dz, -Math.sin(yaw) * dx + Math.cos(yaw) * dz);
        double gain =
            n.module.enabled() && n.module.dimension().equals(listener.dimension)
                ? Output.distance(Math.sqrt(dx * dx + dy * dy + dz * dz)) * listener.volume
                : 0;
        n.left.target(gain * Math.sqrt((1 - pan) * .5));
        n.right.target(gain * Math.sqrt((1 + pan) * .5));
      }
    for (int frame = 0; frame < frames; frame++) {
      for (ControlTimeline timeline : timelines) timeline.advanceTo(sample + frame, 1024);
      double left = 0, right = 0;
      for (int i = 0; i < nodes.length; i++) {
        Node n = nodes[i];
        double audio = n.audio < 0 ? 0 : signals[n.audio];
        double control = n.control < 0 ? 0 : signals[n.control];
        switch (n.module.kind()) {
          case OSCILLATOR -> signals[i] = n.oscillator.next(n.next(Parameter.LEVEL) / 100);
          case SQUARE_OSCILLATOR -> signals[i] = n.pulse.next(n.next(Parameter.LEVEL) / 100);
          case MIXER -> signals[i] = n.mixer.next(signals, n.mixerInputs);
          case LFO -> signals[i] = n.lfo.value(sample + frame);
          case KEYBOARD, EUCLIDEAN_SEQUENCER -> signals[i] = 0;
          case ENVELOPE -> signals[i] = n.envelope.value(sample + frame);
          case FILTER -> {
            if (n.control >= 0
                && nodes[n.control].envelope != null
                && nodes[n.control].module.kind() == ModuleKind.ENVELOPE) n.cutoffCv.reset(control);
            else n.cutoffCv.target(control);
            n.resonanceCv.target(n.resonance < 0 ? 0 : signals[n.resonance]);
            double cutoff =
                Filter.cutoff(
                    n.next(Parameter.CUTOFF), n.cutoffCv.next(), n.next(Parameter.AMOUNT) / 100);
            double resonance =
                Math.clamp(
                    n.next(Parameter.RESONANCE)
                        + n.resonanceCv.next() * n.next(Parameter.RESONANCE_DEPTH) / 100 * 95,
                    0,
                    95);
            double value = n.filter.next(audio, cutoff, resonance);
            signals[i] = n.audio < 0 ? 0 : value;
          }
          case AMP -> {
            if (n.control >= 0 && nodes[n.control].module.kind() == ModuleKind.ENVELOPE)
              n.gainCv.reset(Math.clamp(control, 0, 1));
            else if (n.control >= 0) n.gainCv.target(Math.clamp(control, 0, 1));
            else
              n.gainCv.reset(
                  n.module.value(Parameter.DRONE) == 1 ? 1 : n.envelope.value(sample + frame));
            double mix = n.gainTransition.next();
            n.lastGain = n.gainFrom * (1 - mix) + n.gainCv.next() * mix;
            signals[i] = audio * n.next(Parameter.AMP_LEVEL) / 100 * n.lastGain;
          }
          case SPEAKER -> {
            signals[i] =
                timelines[n.patch].overloaded() ? 0 : audio * n.next(Parameter.VOLUME) / 100;
            left += signals[i] * n.left.next();
            right += signals[i] * n.right.next();
          }
        }
      }
      stereo[frame * 2] = (float) left;
      stereo[frame * 2 + 1] = (float) right;
    }
    long now = System.nanoTime();
    for (Node node : nodes) if (node.mixer != null) node.mixer.meter.publish(now);
  }
}
