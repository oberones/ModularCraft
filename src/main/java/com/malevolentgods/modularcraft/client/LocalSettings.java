package com.malevolentgods.modularcraft.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/** Client game-thread preferences; never accessed by server or audio threads. */
public final class LocalSettings {
  private final Path file = FMLPaths.CONFIGDIR.get().resolve("modularcraft-listener.properties");
  public boolean muted;
  public double volume = 1;
  public String error = "";

  public LocalSettings() {
    try {
      if (Files.isRegularFile(file) && Files.size(file) <= 8192) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
          properties.load(input);
        }
        muted = Boolean.parseBoolean(properties.getProperty("muted", "false"));
        double parsed = Double.parseDouble(properties.getProperty("volume", "1"));
        if (Double.isFinite(parsed)) volume = Math.clamp(parsed, 0, 1);
      }
    } catch (IOException | IllegalArgumentException failure) {
      error = "settings_read";
    }
  }

  public void save() {
    Properties properties = new Properties();
    properties.setProperty("muted", Boolean.toString(muted));
    properties.setProperty("volume", Double.toString(volume));
    try {
      Files.createDirectories(file.getParent());
      try (OutputStream output = Files.newOutputStream(file)) {
        properties.store(output, "ModularCraft local listener preferences");
      }
      error = "";
    } catch (IOException failure) {
      error = "settings_write";
    }
  }
}
