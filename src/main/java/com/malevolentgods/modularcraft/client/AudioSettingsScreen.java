package com.malevolentgods.modularcraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AudioSettingsScreen extends Screen {
  private final Screen parent;

  public AudioSettingsScreen(Screen parent) {
    super(Component.translatable("modularcraft.local_audio"));
    this.parent = parent;
  }

  @Override
  protected void init() {
    int x = width / 2 - 120, y = height / 2 - 60;
    addRenderableWidget(
        Button.builder(
                Component.translatable(
                    ClientRuntime.PREFERENCES.muted ? "modularcraft.unmute" : "modularcraft.mute"),
                button -> {
                  ClientRuntime.toggleMute();
                  button.setMessage(
                      Component.translatable(
                          ClientRuntime.PREFERENCES.muted
                              ? "modularcraft.unmute"
                              : "modularcraft.mute"));
                })
            .bounds(x, y, 240, 20)
            .build());
    addRenderableWidget(
        new AbstractSliderButton(
            x, y + 30, 240, 20, Component.empty(), ClientRuntime.PREFERENCES.volume) {
          {
            updateMessage();
          }

          @Override
          protected void updateMessage() {
            setMessage(
                Component.translatable("modularcraft.local_volume", Math.round(value * 100)));
          }

          @Override
          protected void applyValue() {
            ClientRuntime.PREFERENCES.volume = value;
          }
        });
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.recover"),
                button -> ClientRuntime.recover("resync"))
            .bounds(x, y + 60, 240, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(x, y + 120, 240, 20)
            .build());
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {
    super.renderBackground(graphics, x, y, partial);
    graphics.fill(width / 2 - 140, height / 2 - 95, width / 2 + 140, height / 2 + 90, 0xf018212a);
    graphics.drawCenteredString(font, title, width / 2, height / 2 - 80, 0xffd9edf1);
    graphics.drawCenteredString(
        font,
        Component.translatable("modularcraft.volume_help"),
        width / 2,
        height / 2 + 32,
        0xffa6c7cf);
    graphics.drawCenteredString(
        font,
        Component.translatable("modularcraft.error." + ClientRuntime.diagnostic()),
        width / 2,
        height / 2 + 46,
        0xffe2c88c);
  }

  @Override
  public void removed() {
    ClientRuntime.PREFERENCES.save();
  }

  @Override
  public void onClose() {
    minecraft.setScreen(parent instanceof ModuleScreen ? null : parent);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
