package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.StateCodec;
import com.malevolentgods.modularcraft.domain.WorldState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SpeakerPlaybackTest {
  @Test
  void defaultEnabledIntentSurvivesSaveAndDisabledChoiceIsRemembered() {
    WorldState state = new WorldState();
    ModuleState speaker = AmpAudioTest.module(1, ModuleKind.SPEAKER);
    state.place(speaker);
    state.refreshPlayback();
    Assertions.assertTrue(state.get(speaker.id()).enabled());
    WorldState restored = StateCodec.load(StateCodec.save(state, List.of())).state();
    Assertions.assertFalse(restored.get(speaker.id()).enabled());
    restored.loaded(speaker.id(), true);
    restored.refreshPlayback();
    Assertions.assertTrue(restored.get(speaker.id()).enabled());
    restored.enable(
        speaker.owner(), false, speaker.id(), restored.get(speaker.id()).revision(), false);
    WorldState disabled = StateCodec.load(StateCodec.save(restored, List.of())).state();
    disabled.loaded(speaker.id(), true);
    disabled.refreshPlayback();
    Assertions.assertFalse(disabled.get(speaker.id()).wantsPlayback());
    Assertions.assertFalse(disabled.get(speaker.id()).enabled());
  }

  @Test
  void waitingSpeakersDoNotDisplaceAdmittedPlayback() {
    WorldState state = new WorldState();
    for (int i = 5; i >= 1; i--) state.place(AmpAudioTest.module(i, ModuleKind.SPEAKER));
    state.refreshPlayback();
    UUID waiting = new UUID(1, 5), first = new UUID(1, 1);
    Assertions.assertFalse(state.get(waiting).enabled());
    Assertions.assertTrue(state.get(waiting).wantsPlayback());
    ModuleState active = state.get(first);
    state.enable(active.owner(), false, first, active.revision(), false);
    Assertions.assertTrue(state.get(waiting).enabled());
    state.enable(active.owner(), false, first, state.get(first).revision(), true);
    Assertions.assertFalse(state.get(first).enabled());
    Assertions.assertTrue(state.get(waiting).enabled());
  }

  @Test
  void chunkUnloadResumesButOverloadStaysLatchedUntilExplicitEnable() {
    WorldState state = new WorldState();
    ModuleState speaker = AmpAudioTest.module(1, ModuleKind.SPEAKER);
    state.place(speaker);
    state.refreshPlayback();
    state.loaded(speaker.id(), false);
    state.refreshPlayback();
    Assertions.assertFalse(state.get(speaker.id()).enabled());
    state.loaded(speaker.id(), true);
    state.refreshPlayback();
    Assertions.assertTrue(state.get(speaker.id()).enabled());
    state.suspend(state.component(speaker.id()));
    state.refreshPlayback();
    Assertions.assertFalse(state.get(speaker.id()).enabled());
    Assertions.assertEquals(1, state.get(speaker.id()).value(Parameter.SPEAKER_SUSPENDED));
    WorldState restored = StateCodec.load(StateCodec.save(state, List.of())).state();
    restored.loaded(speaker.id(), true);
    restored.refreshPlayback();
    Assertions.assertFalse(restored.get(speaker.id()).enabled());
    restored.enable(
        speaker.owner(), false, speaker.id(), restored.get(speaker.id()).revision(), true);
    Assertions.assertTrue(restored.get(speaker.id()).enabled());
  }

  @Test
  void redundantEnableKeepsItsAdmittedSlot() {
    WorldState state = new WorldState();
    for (int i = 2; i <= 5; i++) state.place(AmpAudioTest.module(i, ModuleKind.SPEAKER));
    state.refreshPlayback();
    state.place(AmpAudioTest.module(1, ModuleKind.SPEAKER));
    state.refreshPlayback();
    ModuleState playing = state.get(new UUID(1, 5));
    state.enable(playing.owner(), false, playing.id(), playing.revision(), true);
    Assertions.assertTrue(state.get(playing.id()).enabled());
    Assertions.assertFalse(state.get(new UUID(1, 1)).enabled());
  }

  @Test
  void allPatchMembersMustBeLoadedBeforeRememberedPlaybackResumes() {
    WorldState state = new WorldState();
    ModuleState osc = AmpAudioTest.module(1, ModuleKind.OSCILLATOR),
        speaker = AmpAudioTest.module(2, ModuleKind.SPEAKER);
    state.place(osc);
    state.place(speaker);
    state.connect(
        osc.owner(),
        false,
        new com.malevolentgods.modularcraft.domain.Cable(
            osc.id(),
            com.malevolentgods.modularcraft.domain.Port.AUDIO_OUT,
            speaker.id(),
            com.malevolentgods.modularcraft.domain.Port.AUDIO_IN),
        0,
        0);
    state.refreshPlayback();
    Assertions.assertTrue(state.get(speaker.id()).enabled());
    state.loaded(osc.id(), false);
    state.refreshPlayback();
    Assertions.assertFalse(state.get(speaker.id()).enabled());
    Assertions.assertTrue(state.get(speaker.id()).wantsPlayback());
    state.loaded(osc.id(), true);
    state.refreshPlayback();
    Assertions.assertTrue(state.get(speaker.id()).enabled());
  }
}
