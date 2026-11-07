package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.Graph;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.LfoSettings;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prepared DAG of digital sources and analytic envelopes. Single owner, allocation-free advance.
 */
public final class ControlTimeline {
  private static final class Node {
    final UUID id;
    final ModuleState module;
    final Lfo lfo;
    final Node[] cv = new Node[4];
    final Sequencer sequencer;
    StepSequencer steps;
    final Envelope envelope;
    boolean base, key;
    Node clock, reset, gate;
    Port clockPort, resetPort, gatePort;

    Node(ModuleState module, Sequencer sequencer, Envelope envelope, Lfo lfo) {
      this.module = module;
      this.lfo = lfo;
      id = module.id();
      this.sequencer = sequencer;
      this.envelope = envelope;
    }

    boolean output(Port port, long sample) {
      if (port == null) return false;
      if (steps != null)
        return port == Port.GATE_OUT
            ? steps.gate(sample)
            : port == Port.CLOCK_OUT && steps.clock(sample);
      if (sequencer == null) return port == Port.GATE_OUT && key;
      return sequencer.output(channel(port), sample);
    }
  }

  private static final Parameter[] VALUES = {
    Parameter.ATTACK, Parameter.DECAY, Parameter.SUSTAIN, Parameter.RELEASE
  };
  private static final Parameter[] DEPTHS = {
    Parameter.ATTACK_DEPTH, Parameter.DECAY_DEPTH, Parameter.SUSTAIN_DEPTH, Parameter.RELEASE_DEPTH
  };
  private final Node[] nodes;
  private final Map<UUID, Node> indices = new HashMap<>();
  private long sample, nextEvent = Long.MAX_VALUE;
  private boolean overloaded;

  public ControlTimeline(
      List<ModuleState> modules,
      List<Cable> cables,
      Map<UUID, Sequencer> sequencers,
      Map<UUID, Envelope> envelopes,
      Map<UUID, Boolean> baseGates,
      Map<UUID, KeyboardState> keyboards,
      long sample) {
    this(
        modules,
        cables,
        sequencers,
        envelopes,
        baseGates,
        keyboards,
        initialLfos(modules, sample),
        sample);
  }

  public static Map<UUID, Lfo> initialLfos(List<ModuleState> modules, long sample) {
    Map<UUID, Lfo> result = new HashMap<>();
    for (ModuleState module : modules)
      if (module.kind() == ModuleKind.LFO)
        result.put(module.id(), new Lfo(LfoSettings.from(module), sample));
    return result;
  }

  public ControlTimeline(
      List<ModuleState> modules,
      List<Cable> cables,
      Map<UUID, Sequencer> sequencers,
      Map<UUID, Envelope> envelopes,
      Map<UUID, Boolean> baseGates,
      Map<UUID, KeyboardState> keyboards,
      Map<UUID, Lfo> lfos,
      long sample) {
    this(modules, cables, sequencers, envelopes, baseGates, keyboards, lfos, Map.of(), sample);
  }

  public ControlTimeline(
      List<ModuleState> modules,
      List<Cable> cables,
      Map<UUID, Sequencer> sequencers,
      Map<UUID, Envelope> envelopes,
      Map<UUID, Boolean> baseGates,
      Map<UUID, KeyboardState> keyboards,
      Map<UUID, Lfo> lfos,
      Map<UUID, StepSequencer> steps,
      long sample) {
    List<ModuleState> ordered = Graph.compile(modules, cables);
    this.sample = sample;
    nodes = new Node[ordered.size()];
    for (int i = 0; i < nodes.length; i++) {
      ModuleState module = ordered.get(i);
      Node node =
          new Node(
              module,
              sequencers.get(module.id()),
              envelopes.get(module.id()),
              lfos.get(module.id()));
      node.steps = steps.get(module.id());
      node.base = baseGates.getOrDefault(module.id(), false);
      node.key = keyboards.containsKey(module.id()) && keyboards.get(module.id()).gate();
      nodes[i] = node;
      indices.put(node.id, node);
    }
    for (Cable cable : cables) {
      Node to = indices.get(cable.destination()), from = indices.get(cable.source());
      switch (cable.input()) {
        case CLOCK_IN -> {
          to.clock = from;
          to.clockPort = cable.output();
        }
        case RESET_IN -> {
          to.reset = from;
          to.resetPort = cable.output();
        }
        case GATE_IN -> {
          to.gate = from;
          to.gatePort = cable.output();
        }
        case ATTACK_IN -> to.cv[0] = from;
        case DECAY_IN -> to.cv[1] = from;
        case SUSTAIN_IN -> to.cv[2] = from;
        case RELEASE_IN -> to.cv[3] = from;
        default -> {}
      }
    }
    findNext();
  }

  public static int channel(Port port) {
    return switch (port) {
      case TRIGGER_A -> 0;
      case TRIGGER_B -> 1;
      case TRIGGER_C -> 2;
      case TRIGGER_D -> 3;
      case CLOCK_OUT -> 4;
      default -> throw new IllegalArgumentException("sequencer_output");
    };
  }

  private static boolean same(Node a, Node b) {
    return a == null ? b == null : b != null && a.id.equals(b.id);
  }

  /** Call only for authoritative cable changes, not snapshot recovery. */
  public void rewire(ControlTimeline previous, long at) {
    rewireAll(previous == null ? List.of() : List.of(previous), at);
  }

  /**
   * Preserve each source's wiring history even when components split or merge. Preparation only.
   */
  public void rewireAll(List<ControlTimeline> previous, long at) {
    for (Node node : nodes) {
      if (node.sequencer == null && node.lfo == null && node.steps == null) continue;
      Node old = null;
      for (ControlTimeline timeline : previous) {
        old = timeline.indices.get(node.id);
        if (old != null) break;
      }
      if (old == null
          || node.lfo != old.lfo
          || !same(node.clock, old.clock)
          || node.clockPort != old.clockPort
          || !same(node.reset, old.reset)
          || node.resetPort != old.resetPort) {
        if (node.sequencer != null)
          node.sequencer.connect(
              node.clock != null,
              signal(node.clock, node.clockPort, at),
              signal(node.reset, node.resetPort, at),
              at);
        if (node.steps != null)
          node.steps.connect(
              node.clock != null,
              signal(node.clock, node.clockPort, at),
              signal(node.reset, node.resetPort, at),
              at);
        if (node.lfo != null)
          node.lfo.connect(node.clock != null, signal(node.clock, node.clockPort, at), at);
      }
    }
    findNext();
  }

  public void base(UUID id, boolean value) {
    Node node = indices.get(id);
    if (node != null) node.base = value;
  }

  public void keyboard(UUID id, boolean value) {
    Node node = indices.get(id);
    if (node != null) node.key = value;
  }

  private static boolean signal(Node node, Port port, long at) {
    return node != null && node.output(port, at);
  }

  /** Apply simultaneous reset before clock, propagating sources in deterministic DAG order. */
  public int refresh(long at) {
    int transitions = 0;
    for (Node node : nodes) {
      if (node.sequencer != null) {
        long before = node.sequencer.transitions();
        node.sequencer.inputs(
            signal(node.clock, node.clockPort, at), signal(node.reset, node.resetPort, at), at);
        node.sequencer.advance(at);
        transitions += (int) (node.sequencer.transitions() - before);
      }
      if (node.steps != null) {
        long before = node.steps.transitions();
        node.steps.inputs(
            signal(node.clock, node.clockPort, at), signal(node.reset, node.resetPort, at), at);
        node.steps.advance(at);
        transitions += (int) (node.steps.transitions() - before);
      }
      if (node.lfo != null) node.lfo.input(signal(node.clock, node.clockPort, at), at);
      if (node.envelope != null) {
        boolean boundary = node.envelope.nextEvent() <= at;
        boolean heldUpdate = at % 480 == 0 || node.cv[2] == null;
        prepare(node, at, heldUpdate);
        node.envelope.gate(node.base || signal(node.gate, node.gatePort, at), at);
        if (boundary
            || (heldUpdate
                && node.cv[2] != null
                && node.module.value(Parameter.SUSTAIN_DEPTH) != 0)) transitions++;
      }
    }
    sample = at;
    findNext();
    return transitions;
  }

  private void findNext() {
    nextEvent = Long.MAX_VALUE;
    for (Node node : nodes) {
      if (node.steps != null) nextEvent = Math.min(nextEvent, node.steps.nextEvent());
      if (node.sequencer != null) nextEvent = Math.min(nextEvent, node.sequencer.nextEvent());
      if (node.envelope != null) {
        nextEvent = Math.min(nextEvent, node.envelope.nextEvent());
        if (node.cv[2] != null && node.module.value(Parameter.SUSTAIN_DEPTH) != 0)
          nextEvent = Math.min(nextEvent, (sample / 480 + 1) * 480);
      }
    }
  }

  /** Returns consumed transitions; overflow stops this component and never retains a backlog. */
  public int advanceTo(long target, int budget) {
    if (target < sample) return 0;
    int used = 0;
    while (nextEvent <= target) {
      if (used >= budget || nextEvent < sample) {
        overloaded = true;
        suspend(target);
        return Math.max(used, budget + 1);
      }
      used += refresh(nextEvent);
      if (used > budget) {
        overloaded = true;
        suspend(target);
        return used;
      }
    }
    sample = target;
    return used;
  }

  public void suspend(long at) {
    for (Node node : nodes) {
      if (node.sequencer != null) node.sequencer.stop(at);
      if (node.steps != null) node.steps.stop(at);
      if (node.envelope != null) node.envelope.gate(false, at);
    }
    sample = at;
    nextEvent = Long.MAX_VALUE;
  }

  private static double control(Node node, long at) {
    if (node == null) return 0;
    if (node.lfo != null) return node.lfo.value(at);
    return node.envelope == null ? 0 : node.envelope.value(at);
  }

  private static double parameter(Node node, int index, long at) {
    double base = node.module.value(VALUES[index]);
    double amount =
        node.cv[index] == null
            ? 0
            : control(node.cv[index], at) * node.module.value(DEPTHS[index]) / 100;
    return index == 2
        ? Math.clamp(base / 100 + amount, 0, 1)
        : Math.clamp(base * Math.pow(2, 4 * amount), .001, 10);
  }

  private static void prepare(Node node, long at, boolean heldUpdate) {
    node.envelope.prepare(
        parameter(node, 0, at),
        parameter(node, 1, at),
        parameter(node, 2, at),
        parameter(node, 3, at),
        at,
        heldUpdate);
  }

  public void retrigger(UUID id, long at) {
    advanceTo(at, 1024);
    Node node = indices.get(id);
    if (node != null && node.envelope != null) {
      prepare(node, at, false);
      node.envelope.retrigger(at);
      findNext();
    }
  }

  public boolean overloaded() {
    return overloaded;
  }

  public long sample() {
    return sample;
  }
}
