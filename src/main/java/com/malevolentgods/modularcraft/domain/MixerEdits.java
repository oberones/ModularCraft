package com.malevolentgods.modularcraft.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * UI-thread-owned bounded live-edit queue. Times are monotonic nanoseconds supplied by the caller.
 */
public final class MixerEdits {
  public record Edit(ModuleState state, Parameter parameter, double value) {}

  private ModuleState state;
  private boolean editable, closed, timeout, sent;
  private final Map<Parameter, Double> pending = new EnumMap<>(Parameter.class);
  private Edit outstanding;
  private long submitted;

  public MixerEdits(ModuleState state, boolean editable) {
    this.state = state;
    this.editable = editable;
  }

  public void offer(Parameter parameter, double value) {
    if (closed || !editable) return;
    if (!MixerSettings.PARAMETERS.contains(parameter))
      throw new IllegalArgumentException("parameter");
    pending.put(parameter, parameter.validate(value));
    timeout = false;
  }

  public double value(Parameter parameter) {
    if (pending.containsKey(parameter)) return pending.get(parameter);
    if (outstanding != null && outstanding.parameter() == parameter) return outstanding.value();
    return state.value(parameter);
  }

  public Edit poll(long now) {
    if (closed || !editable) return null;
    if (outstanding != null) {
      if (now - submitted > 2_000_000_000L) {
        pending.clear();
        outstanding = null;
        timeout = true;
      }
      return null;
    }
    if (sent && now - submitted < 100_000_000L) return null;
    while (!pending.isEmpty()) {
      Parameter parameter = pending.keySet().iterator().next();
      double value = pending.remove(parameter);
      if (value == state.value(parameter)) continue;
      outstanding = new Edit(state, parameter, value);
      submitted = now;
      sent = true;
      return outstanding;
    }
    return null;
  }

  public void accept(ModuleState accepted, boolean canEdit, String status) {
    if (!state.id().equals(accepted.id()) || accepted.revision() < state.revision()) return;
    state = accepted;
    editable = canEdit;
    if (!canEdit || (!status.equals("ready") && !status.equals("patch_unloaded"))) {
      pending.clear();
      outstanding = null;
    } else if (outstanding != null && accepted.revision() > outstanding.state().revision()) {
      if (accepted.value(outstanding.parameter()) != outstanding.value()) pending.clear();
      outstanding = null;
    }
  }

  public boolean timedOut() {
    return timeout;
  }

  public boolean closed() {
    return closed;
  }

  public void close() {
    closed = true;
    pending.clear();
    outstanding = null;
  }
}
