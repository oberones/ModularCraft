package com.malevolentgods.modularcraft.domain;

import java.util.List;

/** Four mono inputs followed by master. Percent levels are linear gains; mute overrides solo. */
public final class MixerSettings {
  public static final List<Parameter> PARAMETERS =
      List.of(
          Parameter.MIX_1_LEVEL,
          Parameter.MIX_1_MUTE,
          Parameter.MIX_1_SOLO,
          Parameter.MIX_2_LEVEL,
          Parameter.MIX_2_MUTE,
          Parameter.MIX_2_SOLO,
          Parameter.MIX_3_LEVEL,
          Parameter.MIX_3_MUTE,
          Parameter.MIX_3_SOLO,
          Parameter.MIX_4_LEVEL,
          Parameter.MIX_4_MUTE,
          Parameter.MIX_4_SOLO,
          Parameter.MIX_MASTER_LEVEL,
          Parameter.MIX_MASTER_MUTE);
  public static final List<Port> INPUTS =
      List.of(Port.AUDIO_IN_1, Port.AUDIO_IN_2, Port.AUDIO_IN_3, Port.AUDIO_IN_4);

  private MixerSettings() {}

  public static Parameter level(int channel) {
    return PARAMETERS.get(channel * 3);
  }

  public static Parameter mute(int channel) {
    return PARAMETERS.get(channel * 3 + 1);
  }

  public static Parameter solo(int channel) {
    if (channel < 0 || channel >= 4) throw new IllegalArgumentException("channel");
    return PARAMETERS.get(channel * 3 + 2);
  }

  public static double gain(ModuleState state, int channel) {
    boolean anySolo = false;
    for (int i = 0; i < 4; i++) anySolo |= state.value(solo(i)) == 1;
    if (state.value(mute(channel)) == 1
        || (channel < 4 && anySolo && state.value(solo(channel)) == 0)) return 0;
    return state.value(level(channel)) / 100;
  }
}
