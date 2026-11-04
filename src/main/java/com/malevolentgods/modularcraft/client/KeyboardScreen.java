package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.KeyboardInput;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.game.ModuleBlock;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
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
  private boolean invalidated, compact;
  private MidiInput.Port midiChoice;
  private String status;
  private long sequence, renewed, octaveRevision;
  private int left, top, panelWidth;
  private Piano piano;
  private Button down, up;
  private Button midiDevice, midiChannel, midiRefresh, panic;

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

  /** Client-thread MIDI delivery, also used by the synthetic native acceptance fixture. */
  public void midi(MidiInput.Event event) {
    if (!editable || invalidated || closed || !minecraft.isWindowActive() || !event.supported())
      return;
    int channel = ClientRuntime.PREFERENCES.midiChannel;
    if (channel >= 0 && channel != event.channel()) return;
    if (input.midi(event.command(), event.channel(), event.data1(), event.data2())) changedInput();
  }

  private void changedInput() {
    if (octavePending && input.note() >= 0) notePending = true;
    else {
      notePending = false;
      send(false, false);
    }
  }

  /** Revokes local holds before lifecycle transitions; invalid sessions require reopening. */
  public void suspendInput(boolean invalidate) {
    releaseAll(invalidate);
    midiChoice = null;
    if (invalidate) {
      invalidated = true;
      editable = false;
      status = "keyboard_unavailable";
    }
    ClientRuntime.MIDI.target("", null, -1, false);
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
      if (rejected) suspendInput(false);
      else if (pending) send(false, false);
    }
    updateButtons();
  }

  private void updateButtons() {
    if (down == null) return;
    down.active = editable && !invalidated && !octavePending && state.value(Parameter.OCTAVE) > 1;
    up.active = editable && !invalidated && !octavePending && state.value(Parameter.OCTAVE) < 7;
    piano.active = editable && !invalidated;
    midiDevice.active = midiChannel.active = midiRefresh.active = editable && !invalidated;
    panic.active = editable && !invalidated;
    LocalSettings settings = ClientRuntime.PREFERENCES;
    Component name =
        settings.midiDevice.isEmpty()
            ? Component.translatable("modularcraft.midi.off")
            : Component.literal(settings.midiName);
    Component label = Component.translatable("modularcraft.midi.device", name);
    int selected = midiChoice == null ? -1 : ClientRuntime.MIDI.ports().indexOf(midiChoice);
    if (selected >= 0)
      label =
          Component.translatable(
              "modularcraft.midi.port", name, selected + 1, ClientRuntime.MIDI.ports().size());
    midiDevice.setMessage(
        Component.literal(font.plainSubstrByWidth(label.getString(), midiDevice.getWidth() - 12)));
    midiDevice.setTooltip(Tooltip.create(label));
    midiChannel.setMessage(
        Component.translatable(
            "modularcraft.midi.channel",
            settings.midiChannel < 0
                ? Component.translatable("modularcraft.midi.all")
                : Component.literal(Integer.toString(settings.midiChannel + 1))));
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
    compact = height < 318;
    top = Math.max(0, (height - (compact ? 236 : 310)) / 2);
    piano =
        addRenderableWidget(new Piano(left + 10, y(65, 52), panelWidth - 20, compact ? 48 : 86));
    down =
        addRenderableWidget(
            Button.builder(Component.translatable("modularcraft.octave_down"), b -> octave(-1))
                .bounds(left + 10, y(29, 26), 80, 20)
                .build());
    up =
        addRenderableWidget(
            Button.builder(Component.translatable("modularcraft.octave_up"), b -> octave(1))
                .bounds(left + panelWidth - 90, y(29, 26), 80, 20)
                .build());
    midiDevice =
        addRenderableWidget(
            Button.builder(Component.empty(), b -> cycleMidi())
                .bounds(left + 10, y(176, 119), panelWidth - 110, 20)
                .build());
    midiRefresh =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("modularcraft.midi.refresh"),
                    b -> {
                      clearMidi();
                      midiChoice = null;
                      ClientRuntime.MIDI.target(
                          ClientRuntime.PREFERENCES.midiDevice,
                          null,
                          ClientRuntime.PREFERENCES.midiChannel,
                          editable && minecraft.isWindowActive());
                      ClientRuntime.MIDI.refresh();
                    })
                .bounds(left + panelWidth - 90, y(176, 119), 80, 20)
                .build());
    midiChannel =
        addRenderableWidget(
            Button.builder(
                    Component.empty(),
                    b -> {
                      clearMidi();
                      LocalSettings settings = ClientRuntime.PREFERENCES;
                      settings.midiChannel =
                          settings.midiChannel == 15 ? -1 : settings.midiChannel + 1;
                      settings.save();
                      targetMidi();
                      updateButtons();
                    })
                .bounds(left + 10, y(200, 143), 130, 20)
                .build());
    panic =
        addRenderableWidget(
            Button.builder(
                    Component.translatable("modularcraft.midi.release"),
                    b -> {
                      releaseAll(false);
                      ClientRuntime.MIDI.refresh();
                    })
                .bounds(left + panelWidth - 140, y(200, 143), 130, 20)
                .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.local_audio"),
                b -> minecraft.setScreen(new AudioSettingsScreen(this)))
            .bounds(left + 10, y(250, 190), 150, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + panelWidth - 90, y(250, 190), 80, 20)
            .build());
    setInitialFocus(piano);
    updateButtons();
    targetMidi();
    ClientRuntime.MIDI.refresh();
  }

  private int y(int spacious, int small) {
    return top + (compact ? small : spacious);
  }

  private void targetMidi() {
    boolean active = editable && !invalidated && !closed && minecraft.isWindowActive();
    if (!active) midiChoice = null;
    ClientRuntime.MIDI.target(
        ClientRuntime.PREFERENCES.midiDevice,
        midiChoice,
        ClientRuntime.PREFERENCES.midiChannel,
        active);
  }

  private void clearMidi() {
    if (input.clearMidi()) changedInput();
  }

  private void cycleMidi() {
    clearMidi();
    var ports = ClientRuntime.MIDI.ports();
    LocalSettings settings = ClientRuntime.PREFERENCES;
    int current = midiChoice == null ? -1 : ports.indexOf(midiChoice);
    if (midiChoice == null && !settings.midiDevice.isEmpty())
      for (int index = 0; index < ports.size(); index++)
        if (ports.get(index).key().equals(settings.midiDevice)) {
          current = index;
          break;
        }
    midiChoice = current + 1 < ports.size() ? ports.get(current + 1) : null;
    settings.midiDevice = midiChoice == null ? "" : midiChoice.key();
    settings.midiName = midiChoice == null ? "" : midiChoice.name();
    settings.save();
    targetMidi();
    updateButtons();
  }

  private void octave(int delta) {
    int next = (int) state.value(Parameter.OCTAVE) + delta;
    if (!editable || invalidated || octavePending || next < 1 || next > 7) return;
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
              cleanup,
              !cleanup && input.absolute()));
    renewed = System.nanoTime();
  }

  private void press(int source, int note) {
    if (editable && !invalidated && input.press(source, note)) {
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
    notePending = false;
    if (held || cleanup) send(false, cleanup);
  }

  @Override
  public void tick() {
    BlockPos position = new BlockPos(state.x(), state.y(), state.z());
    if (!invalidated
        && (minecraft.level == null
            || minecraft.player == null
            || !minecraft.level.dimension().location().toString().equals(state.dimension())
            || !minecraft.player.isAlive()
            || minecraft.player.isSpectator()
            || minecraft.player.distanceToSqr(position.getCenter()) > 36
            || !minecraft.level.hasChunkAt(position)
            || !(minecraft.level.getBlockState(position).getBlock() instanceof ModuleBlock block)
            || block.kind != ModuleKind.KEYBOARD)) suspendInput(true);
    targetMidi();
    ClientRuntime.MIDI.tick();
    String reset = ClientRuntime.MIDI.takeReset();
    if (reset != null) {
      if (reset.equals("keyboard_overload")) {
        suspendInput(true);
        status = reset;
      } else clearMidi();
    }
    if (!minecraft.isWindowActive()) {
      releaseAll(false);
      return;
    }
    ClientRuntime.MIDI.drain(this::midi);
    updateButtons();
    if (!octavePending && input.note() >= 0 && System.nanoTime() - renewed >= 200_000_000L)
      send(true, false);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int note = piano.hit(x, y);
    if (button == 0 && note >= 0 && editable && !invalidated) {
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
    suspendInput(true);
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
    g.fill(left, top, left + panelWidth, top + (compact ? 236 : 310), 0xf018212a);
    g.drawString(font, title, left + 10, top + 10, 0xffd9edf1);
    g.drawCenteredString(
        font,
        Component.translatable("modularcraft.octave", (int) state.value(Parameter.OCTAVE)),
        width / 2,
        y(35, 32),
        0xffd9edf1);
    String note = NOTES[accepted.note() % 12] + (accepted.note() / 12 - 1);
    g.drawString(
        font,
        Component.translatable("modularcraft.keyboard_state", note, accepted.gate() ? "1" : "0"),
        left + 10,
        y(157, 105),
        0xffd9edf1);
    g.drawWordWrap(
        font,
        Component.translatable("modularcraft.error." + ClientRuntime.MIDI.status()),
        left + 10,
        y(225, 168),
        panelWidth - 20,
        0xffa6c7cf);
    g.drawWordWrap(
        font,
        Component.translatable("modularcraft.error." + status),
        left + 10,
        y(278, 216),
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
