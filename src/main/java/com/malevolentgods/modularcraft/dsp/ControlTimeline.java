package com.malevolentgods.modularcraft.dsp;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.Graph;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleState;
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
    final Sequencer sequencer;
    final Envelope envelope;
    boolean base, key;
    Node clock, reset, gate;
    Port clockPort, resetPort, gatePort;

    Node(ModuleState module, Sequencer sequencer, Envelope envelope) {
      id = module.id();
      this.sequencer = sequencer;
      this.envelope = envelope;
    }

    boolean output(Port port, long sample) {
      if (port == null) return false;
      if (sequencer == null) return port == Port.GATE_OUT && key;
      return sequencer.output(channel(port), sample);
    }
  }

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
    List<ModuleState> ordered = Graph.compile(modules, cables);
    this.sample = sample;
    nodes = new Node[ordered.size()];
    for (int i = 0; i < nodes.length; i++) {
      ModuleState module = ordered.get(i);
      Node node = new Node(module, sequencers.get(module.id()), envelopes.get(module.id()));
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
    for (Node node : nodes) {
      if (node.sequencer == null) continue;
      Node old = previous == null ? null : previous.indices.get(node.id);
      if (old == null
          || !same(node.clock, old.clock)
          || node.clockPort != old.clockPort
          || !same(node.reset, old.reset)
          || node.resetPort != old.resetPort)
        node.sequencer.connect(
            node.clock != null,
            signal(node.clock, node.clockPort, at),
            signal(node.reset, node.resetPort, at),
            at);
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
      if (node.envelope != null)
        node.envelope.gate(node.base || signal(node.gate, node.gatePort, at), at);
    }
    sample = at;
    findNext();
    return transitions;
  }

  private void findNext() {
    nextEvent = Long.MAX_VALUE;
    for (Node node : nodes)
      if (node.sequencer != null) nextEvent = Math.min(nextEvent, node.sequencer.nextEvent());
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
      if (node.envelope != null) node.envelope.gate(false, at);
    }
    sample = at;
    nextEvent = Long.MAX_VALUE;
  }

  public boolean overloaded() {
    return overloaded;
  }

  public long sample() {
    return sample;
  }
}
