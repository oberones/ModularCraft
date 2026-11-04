package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.KeyboardInput;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Non-pausing piano with independent mouse/key holds; only accepted server state drives audio. */
public final class KeyboardScreen extends Screen {
  private static final String[] NOTES = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
  };
  private static final String SHORTCUTS = "AWSEDFTGYHUJ";
  private final KeyboardInput input = new KeyboardInput();
  private final UUID session;
  private ModuleState state;
  private KeyboardState accepted;
  private boolean editable, mouseHeld, octavePending, closed, notePending;
  private String status;
  private long sequence, renewed, octaveRevision;
  private int left, top, panelWidth;
  private Piano piano;
  private Button down, up;

  public KeyboardScreen(
      ModuleState state, KeyboardState accepted, boolean editable, String status, UUID session) {
    super(Component.translatable("block.modularcraft.keyboard"));
    this.state = state;
    this.accepted = accepted;
    this.editable = editable;
    this.status = status;
    this.session = session;
  }

  public void performance(KeyboardState value) {
    accepted = value;
  }

  public UUID identity() {
    return state.id();
  }

  public UUID session() {
    return session;
  }

  public void accept(ModuleState value, KeyboardState note, boolean canEdit, String message) {
    boolean rejected = !canEdit || !(message.equals("ready") || message.equals("patch_unloaded"));
    boolean resolved = !octavePending || value.revision() > octaveRevision || rejected;
    state = value;
    accepted = note;
    editable = canEdit;
    status = resolved ? message : "awaiting";
    if (resolved) {
      octavePending = false;
      boolean pending = notePending;
      notePending = false;
      if (rejected) releaseAll(false);
      else if (pending) send(false, false);
    }
    updateButtons();
  }

  private void updateButtons() {
    if (down == null) return;
    down.active = editable && !octavePending && state.value(Parameter.OCTAVE) > 1;
    up.active = editable && !octavePending && state.value(Parameter.OCTAVE) < 7;
    piano.active = editable;
  }

  @Override
  protected void init() {
    if (closed) {
      ClientRuntime.send(state, Messages.Action.INSPECT, Parameter.OCTAVE, 0);
      editable = false;
      status = "awaiting";
    }
    panelWidth = Math.min(420, width - 16);
    left = (width - panelWidth) / 2;
    top = Math.max(4, (height - 236) / 2);
    piano = addRenderableWidget(new Piano(left + 10, top + 65, panelWidth - 20, 86));
    down =
        addRenderableWidget(
            Button.builder(Component.translatable("modularcraft.octave_down"), b -> octave(-1))
                .bounds(left + 10, top + 29, 80, 20)
                .build());
    up =
        addRenderableWidget(
            Button.builder(Component.translatable("modularcraft.octave_up"), b -> octave(1))
                .bounds(left + panelWidth - 90, top + 29, 80, 20)
                .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.local_audio"),
                b -> minecraft.setScreen(new AudioSettingsScreen(this)))
            .bounds(left + 10, top + 180, 150, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + panelWidth - 90, top + 180, 80, 20)
            .build());
    setInitialFocus(piano);
    updateButtons();
  }

  private void octave(int delta) {
    int next = (int) state.value(Parameter.OCTAVE) + delta;
    if (!editable || octavePending || next < 1 || next > 7) return;
    octaveRevision = state.revision();
    ClientRuntime.send(state, Messages.Action.PARAMETER, Parameter.OCTAVE, next);
    octavePending = true;
    status = "awaiting";
    updateButtons();
    setFocused(piano);
  }

  private void send(boolean renew, boolean cleanup) {
    if (minecraft.getConnection() != null)
      PacketDistributor.sendToServer(
          new Messages.Keyboard(
              state.id(),
              state.revision(),
              session,
              sequence++,
              cleanup ? -1 : input.note(),
              renew,
              cleanup));
    renewed = System.nanoTime();
  }

  private void press(int source, int note) {
    if (editable && input.press(source, note)) {
      if (octavePending) notePending = true;
      else send(false, false);
      status = "awaiting";
    }
  }

  private void release(int source) {
    int active = input.source();
    if (input.release(source) && active == source) {
      if (octavePending && input.note() >= 0) notePending = true;
      else send(false, false);
    }
  }

  private void releaseAll(boolean cleanup) {
    boolean held = input.note() >= 0;
    input.clear();
    mouseHeld = false;
    if (held || cleanup) send(false, cleanup);
  }

  @Override
  public void tick() {
    if (!minecraft.isWindowActive()) {
      releaseAll(false);
      return;
    }
    if (!octavePending && input.note() >= 0 && System.nanoTime() - renewed >= 200_000_000L)
      send(true, false);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int note = piano.hit(x, y);
    if (button == 0 && note >= 0 && editable) {
      setFocused(piano);
      mouseHeld = true;
      press(12, note);
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
    if (button == 0 && mouseHeld) {
      int note = piano.hit(x, y);
      if (note < 0) release(12);
      else press(12, note);
      return true;
    }
    return super.mouseDragged(x, y, button, dx, dy);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (button == 0) {
      mouseHeld = false;
      release(12);
    }
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (getFocused() == piano) {
      int note = SHORTCUTS.indexOf(key);
      if (note >= 0) {
        press(note, note);
        return true;
      }
      if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
        octave(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
        return true;
      }
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public boolean keyReleased(int key, int scan, int modifiers) {
    int note = SHORTCUTS.indexOf(key);
    if (note >= 0) {
      release(note);
      return true;
    }
    return super.keyReleased(key, scan, modifiers);
  }

  @Override
  public void removed() {
    releaseAll(true);
    closed = true;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void onClose() {
    minecraft.setScreen(null);
  }

  @Override
  public void renderBackground(GuiGraphics g, int x, int y, float partial) {
    super.renderBackground(g, x, y, partial);
    g.fill(left, top, left + panelWidth, top + 236, 0xf018212a);
    g.drawString(font, title, left + 10, top + 10, 0xffd9edf1);
    g.drawCenteredString(
        font,
        Component.translatable("modularcraft.octave", (int) state.value(Parameter.OCTAVE)),
        width / 2,
        top + 35,
        0xffd9edf1);
    String note = NOTES[accepted.note() % 12] + (accepted.note() / 12 - 1);
    g.drawString(
        font,
        Component.translatable("modularcraft.keyboard_state", note, accepted.gate() ? "1" : "0"),
        left + 10,
        top + 157,
        0xffd9edf1);
    g.drawWordWrap(
        font,
        Component.translatable("modularcraft.error." + status),
        left + 10,
        top + 207,
        panelWidth - 20,
        0xffe2c88c);
  }

  private final class Piano extends AbstractWidget {
    Piano(int x, int y, int width, int height) {
      super(x, y, width, height, Component.translatable("modularcraft.keyboard_help"));
    }

    int hit(double x, double y) {
      return KeyboardInput.hit(x - getX(), y - getY(), getWidth(), getHeight());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int x, int y, float partial) {
      double white = getWidth() / 7.0;
      for (int i = 0; i < 7; i++)
        drawKey(
            g,
            KeyboardInput.WHITE[i],
            getX() + (int) (i * white),
            getX() + (int) ((i + 1) * white),
            getHeight(),
            false);
      for (int i = 0; i < 5; i++)
        drawKey(
            g,
            KeyboardInput.BLACK[i],
            getX() + (int) ((KeyboardInput.BOUNDARY[i] - .3) * white),
            getX() + (int) ((KeyboardInput.BOUNDARY[i] + .3) * white),
            (int) (getHeight() * .62),
            true);
      if (isFocused())
        g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xff65d7e5);
    }

    private void drawKey(GuiGraphics g, int note, int x1, int x2, int height, boolean black) {
      g.fill(x1, getY(), x2, getY() + height, 0xff101820);
      g.fill(
          x1 + 1,
          getY() + 1,
          x2 - 1,
          getY() + height - 1,
          input.held(note) ? 0xff65d7e5 : black ? 0xff202d37 : 0xffe4edea);
      int color = black && !input.held(note) ? 0xffe4edea : 0xff101820;
      g.drawCenteredString(font, NOTES[note], (x1 + x2) / 2, getY() + height - 24, color);
      g.drawCenteredString(
          font, SHORTCUTS.substring(note, note + 1), (x1 + x2) / 2, getY() + height - 12, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, getMessage());
    }
  }
}
