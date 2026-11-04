package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.Graph;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
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
    final Filter filter = new Filter();
    final Smoother[] parameters = new Smoother[Parameter.values().length];
    final Smoother left = new Smoother(0), right = new Smoother(0);
    Envelope envelope;
    int audio = -1, control = -1, pitch = -1, patch;

    Node(ModuleState module, Envelope.Checkpoint checkpoint) {
      this.module = module;
      for (Parameter p : module.kind().parameters)
        parameters[p.ordinal()] = new Smoother(module.value(p));
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
    if (modules.size() > 64) throw new IllegalArgumentException("active_modules");
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
      for (ModuleState module : patch.modules()) {
        Node node = nodes[indices.get(module.id())];
        node.patch = p;
        if (module.kind() == ModuleKind.ENVELOPE) envelopes.put(module.id(), node.envelope);
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
              patch.sample());
    }
    Map<UUID, KeyboardState> keyboards = new HashMap<>();
    for (PatchSnapshot patch : patches) keyboards.putAll(patch.keyboards());
    for (Cable c : cables) {
      Node n = nodes[indices.get(c.destination())];
      if (c.input() == Port.AUDIO_IN) n.audio = indices.get(c.source());
      else if (c.input() == Port.PITCH_IN) n.pitch = indices.get(c.source());
      else if (c.input() == Port.CUTOFF_IN || c.input() == Port.GAIN_IN)
        n.control = indices.get(c.source());
    }
    for (Node n : nodes)
      if (n.module.kind() == ModuleKind.OSCILLATOR) {
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
        n.oscillator = new UnisonSaw(hz, n.module.value(Parameter.DETUNE), sample);
      }
  }

  /** Transfer continuous state at the audio block boundary, without mutating the old graph. */
  public void adopt(RenderGraph previous) {
    for (Node node : nodes)
      for (Node old : previous.nodes)
        if (node.module.id().equals(old.module.id())) {
          if (node.oscillator != null) node.oscillator.adopt(old.oscillator);
          node.filter.copyFrom(old.filter);
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
          || nodes[i].audio != previous.nodes[i].audio
          || nodes[i].control != previous.nodes[i].control
          || nodes[i].pitch != previous.nodes[i].pitch) return false;
    return true;
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
          case KEYBOARD, EUCLIDEAN_SEQUENCER -> signals[i] = 0;
          case ENVELOPE -> signals[i] = n.envelope.value(sample + frame);
          case FILTER -> {
            double cutoff =
                Filter.cutoff(n.next(Parameter.CUTOFF), control, n.next(Parameter.AMOUNT) / 100);
            double value = n.filter.next(audio, cutoff, n.next(Parameter.RESONANCE));
            signals[i] = n.audio < 0 ? 0 : value;
          }
          case SPEAKER -> {
            signals[i] =
                timelines[n.patch].overloaded()
                    ? 0
                    : audio * n.next(Parameter.VOLUME) / 100 * (n.control < 0 ? 1 : control);
            left += signals[i] * n.left.next();
            right += signals[i] * n.right.next();
          }
        }
      }
      stereo[frame * 2] = (float) left;
      stereo[frame * 2 + 1] = (float) right;
    }
  }
}
