package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.client.LocalSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MidiSettingsTest {
  @TempDir Path directory;

  @Test
  void deviceAndChannelAreLocalOptInAndSurviveAudioPreferenceChanges() {
    Path file = directory.resolve("preferences.properties");
    LocalSettings settings = new LocalSettings(file);
    Assertions.assertEquals("", settings.midiDevice);
    Assertions.assertEquals(-1, settings.midiChannel);
    settings.midiDevice = "controller-key";
    settings.midiName = "Controller";
    settings.midiChannel = 15;
    settings.save();
    LocalSettings restored = new LocalSettings(file);
    Assertions.assertEquals("controller-key", restored.midiDevice);
    Assertions.assertEquals(15, restored.midiChannel);
    restored.volume = .5;
    restored.save();
    Assertions.assertEquals("Controller", new LocalSettings(file).midiName);
  }

  @Test
  void malformedOrOversizedPreferencesNeverEnableMidi() throws IOException {
    Path file = directory.resolve("preferences.properties");
    for (String content :
        new String[] {
          "midiDevice=controller\nmidiChannel=16\n",
          "midiDevice=controller\nmidiChannel=bad\n",
          "midiDevice=" + "x".repeat(257) + "\n",
          "midiDevice=" + "x".repeat(9000) + "\n"
        }) {
      Files.writeString(file, content);
      Assertions.assertEquals("", new LocalSettings(file).midiDevice);
    }
  }
}
