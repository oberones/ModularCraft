package com.malevolentgods.modularcraft.domain;

import java.util.List;
import java.util.Map;

/** Immutable prepared monophonic pattern; -1 denotes a rest, all other notes are MIDI pitches. */
public record StepSequencerSettings(int tempo, int length, int gateMillis, List<Integer> notes) {
  public static final List<Parameter> NOTES =
      List.of(
          Parameter.STEP_1,
          Parameter.STEP_2,
          Parameter.STEP_3,
          Parameter.STEP_4,
          Parameter.STEP_5,
          Parameter.STEP_6,
          Parameter.STEP_7,
          Parameter.STEP_8,
          Parameter.STEP_9,
          Parameter.STEP_10,
          Parameter.STEP_11,
          Parameter.STEP_12,
          Parameter.STEP_13,
          Parameter.STEP_14,
          Parameter.STEP_15,
          Parameter.STEP_16);
  public static final List<Parameter> PARAMETERS =
      java.util.stream.Stream.concat(
              java.util.stream.Stream.of(
                  Parameter.TEMPO,
                  Parameter.SEQUENCE_LENGTH,
                  Parameter.GATE_TIME,
                  Parameter.OCTAVE,
                  Parameter.REDSTONE_MODE),
              NOTES.stream())
          .toList();

  public StepSequencerSettings {
    Parameter.TEMPO.validate(tempo);
    Parameter.SEQUENCE_LENGTH.validate(length);
    Parameter.GATE_TIME.validate(gateMillis);
    notes = List.copyOf(notes);
    if (notes.size() != 16) throw new IllegalArgumentException("step_notes");
    for (int note : notes) Parameter.STEP_1.validate(note);
  }

  public static StepSequencerSettings from(ModuleState state) {
    return from(state.values());
  }

  public static StepSequencerSettings from(Map<Parameter, Double> values) {
    return new StepSequencerSettings(
        (int) Parameter.TEMPO.validate(values.get(Parameter.TEMPO)),
        (int) Parameter.SEQUENCE_LENGTH.validate(values.get(Parameter.SEQUENCE_LENGTH)),
        (int) Parameter.GATE_TIME.validate(values.get(Parameter.GATE_TIME)),
        NOTES.stream().map(p -> (int) p.validate(values.get(p))).toList());
  }

  public SequencerSettings clockSettings() {
    var channel = new SequencerSettings.Channel(length, 0, 0, false);
    return new SequencerSettings(tempo, List.of(channel, channel, channel, channel));
  }
}
