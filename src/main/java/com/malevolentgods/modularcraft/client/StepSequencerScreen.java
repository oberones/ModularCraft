package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.KeyboardInput;
import com.malevolentgods.modularcraft.domain.KeyboardState;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.StepRecorder;
import com.malevolentgods.modularcraft.domain.StepSequencerSettings;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Non-pausing step-entry editor. Piano presses are persistent edits, never live keyboard events.
 */
public final class StepSequencerScreen extends Screen {
  private static final String SHORTCUTS = "AWSEDFTGYHUJ";
  private static final String[] NOTES = {
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
  };
  private ModuleState state;
  private final StepRecorder recorder = new StepRecorder();
  private final Set<Integer> held = new HashSet<>();
  private final List<AbstractWidget> editing = new ArrayList<>();
  private final Button[] cells = new Button[16];
  private Button record, start, stop, redstone;
  private Piano piano;
  private boolean editable, awaiting, mouseHeld;
  private long submitted, revision;
  private Parameter submittedParameter;
  private double submittedValue;
  private String status;
  private int left, top, panel;

  public StepSequencerScreen(ModuleState state, boolean editable, String status) {
    super(Component.translatable("block.modularcraft.step_sequencer"));
    this.state = state;
    this.editable = editable;
    this.status = status;
  }

  public UUID identity() {
    return state.id();
  }

  private static Component text(String key, Object... args) {
    return Component.translatable("modularcraft.step." + key, args);
  }

  private int length() {
    return (int) state.value(Parameter.SEQUENCE_LENGTH);
  }

  public void accept(ModuleState value, boolean canEdit, String message) {
    boolean accepted =
        awaiting
            && value.revision() > revision
            && canEdit
            && message.equals("ready")
            && (submittedParameter == null || value.value(submittedParameter) == submittedValue);
    recorder.resolve(accepted, (int) value.value(Parameter.SEQUENCE_LENGTH));
    state = value;
    editable = canEdit;
    status = message;
    awaiting = false;
    update();
  }

  private Button button(Component label, int x, int y, int w, Runnable action) {
    Button b =
        Button.builder(label, ignored -> action.run()).bounds(left + x, top + y, w, 18).build();
    addRenderableWidget(b);
    editing.add(b);
    return b;
  }

  @Override
  protected void init() {
    editing.clear();
    panel = Math.min(480, width - 16);
    left = (width - panel) / 2;
    top = Math.max(0, (height - 236) / 2);
    start =
        button(
            Component.translatable("modularcraft.euclidean.start"),
            8,
            24,
            102,
            () -> command(Messages.Action.SEQUENCER_START));
    stop =
        button(
            Component.translatable("modularcraft.euclidean.stop"),
            114,
            24,
            48,
            () -> command(Messages.Action.SEQUENCER_STOP));
    button(
        Component.translatable("modularcraft.euclidean.reset"),
        166,
        24,
        48,
        () -> command(Messages.Action.SEQUENCER_RESET));
    redstone =
        button(
            Component.empty(),
            218,
            24,
            panel - 226,
            () -> submit(Parameter.REDSTONE_MODE, 1 - state.value(Parameter.REDSTONE_MODE)));
    numeric(Parameter.SEQUENCE_LENGTH, 8, 48, (panel - 24) / 3);
    numeric(Parameter.TEMPO, 8 + (panel - 24) / 3 + 4, 48, (panel - 24) / 3);
    numeric(Parameter.GATE_TIME, 8 + 2 * ((panel - 24) / 3 + 4), 48, (panel - 24) / 3);
    int cell = (panel - 16) / 8;
    for (int i = 0; i < 16; i++) {
      int index = i;
      cells[i] =
          button(
              Component.empty(),
              8 + i % 8 * cell,
              80 + i / 8 * 23,
              cell - 2,
              () -> {
                recorder.select(index, length());
                update();
              });
    }
    record =
        button(
            Component.empty(),
            8,
            128,
            80,
            () -> {
              recorder.record(!recorder.recording());
              update();
            });
    button(text("rest"), 92, 128, 48, () -> note(-1));
    button(Component.translatable("modularcraft.octave_down"), 144, 128, 70, () -> octave(-1));
    button(
        Component.translatable("modularcraft.octave_up"), 218, 128, panel - 226, () -> octave(1));
    piano = addRenderableWidget(new Piano(left + 8, top + 152, panel - 16, 40));
    editing.add(piano);
    button(
        Component.translatable("modularcraft.local_audio"),
        8,
        198,
        126,
        () -> minecraft.setScreen(new AudioSettingsScreen(this)));
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + panel - 66, top + 198, 58, 18)
            .build());
    setInitialFocus(piano);
    update();
  }

  private void numeric(Parameter p, int x, int y, int w) {
    Component label =
        Component.translatable("parameter.modularcraft." + p.name().toLowerCase(Locale.ROOT));
    EditBox field = new EditBox(font, left + x, top + y + 10, w - 31, 18, label);
    field.setMaxLength(4);
    field.setValue(Integer.toString((int) state.value(p)));
    field.setTooltip(Tooltip.create(label));
    addRenderableWidget(field);
    editing.add(field);
    button(
        text("set"),
        x + w - 29,
        y + 10,
        27,
        () -> {
          try {
            submit(p, p.validate(Double.parseDouble(field.getValue())));
          } catch (IllegalArgumentException invalid) {
            status = "range";
          }
        });
  }

  private void update() {
    recorder.resize(length());
    for (AbstractWidget w : editing) w.active = editable && !awaiting;
    if (record == null) return;
    record.setMessage(text(recorder.recording() ? "recording" : "record"));
    redstone.setMessage(text(state.value(Parameter.REDSTONE_MODE) == 1 ? "power_on" : "power_off"));
    start.active = stop.active = editable && !awaiting && state.value(Parameter.REDSTONE_MODE) == 0;
    for (int i = 0; i < 16; i++) {
      int value = (int) state.value(StepSequencerSettings.NOTES.get(i));
      cells[i].setMessage(
          Component.literal((i + 1) + " ")
              .append(
                  value < 0
                      ? text("rest")
                      : Component.literal(NOTES[value % 12] + (value / 12 - 1))));
      cells[i].setTooltip(Tooltip.create(text("cell", i + 1, cells[i].getMessage())));
      cells[i].active = editable && !awaiting && i < length();
    }
  }

  private void note(int value) {
    if (!editable || awaiting || !recorder.submit(length())) return;
    submit(StepSequencerSettings.NOTES.get(recorder.selected()), value);
  }

  private void octave(int delta) {
    int value = (int) state.value(Parameter.OCTAVE) + delta;
    if (value >= 1 && value <= 7) submit(Parameter.OCTAVE, value);
  }

  private void submit(Parameter p, double value) {
    if (!editable || awaiting) return;
    p.validate(value);
    submittedParameter = p;
    submittedValue = value;
    send(Messages.Action.PARAMETER, p, value);
  }

  private void command(Messages.Action action) {
    submittedParameter = null;
    send(action, Parameter.TEMPO, 0);
  }

  private void send(Messages.Action action, Parameter p, double value) {
    if (!editable || awaiting) return;
    revision = state.revision();
    submitted = System.nanoTime();
    awaiting = true;
    status = "awaiting";
    ClientRuntime.send(state, action, p, value);
    update();
  }

  @Override
  public void tick() {
    if (!minecraft.isWindowActive()) {
      held.clear();
      mouseHeld = false;
      recorder.record(false);
    }
    if (awaiting && System.nanoTime() - submitted > 2_000_000_000L) {
      recorder.record(false);
      recorder.resolve(false, length());
      ClientRuntime.send(state, Messages.Action.INSPECT, Parameter.TEMPO, 0);
      submitted = System.nanoTime();
    }
    update();
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (getFocused() == piano) {
      int note = SHORTCUTS.indexOf(key);
      if (note >= 0) {
        if (held.add(key)) note(KeyboardState.note((int) state.value(Parameter.OCTAVE), note));
        return true;
      }
      if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
        if (held.add(key)) octave(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
        return true;
      }
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public boolean keyReleased(int key, int scan, int modifiers) {
    held.remove(key);
    return super.keyReleased(key, scan, modifiers);
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    int key = piano.hit(x, y);
    if (button == 0 && key >= 0) {
      setFocused(piano);
      if (!mouseHeld) note(KeyboardState.note((int) state.value(Parameter.OCTAVE), key));
      mouseHeld = true;
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    if (button == 0) mouseHeld = false;
    return super.mouseReleased(x, y, button);
  }

  @Override
  public void removed() {
    recorder.record(false);
    held.clear();
    mouseHeld = false;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
    g.drawString(font, title, left + 8, top + 8, 0xffd9edf1);
    int w = (panel - 24) / 3;
    g.drawString(font, text("length"), left + 8, top + 47, 0xffd9edf1);
    g.drawString(font, text("tempo"), left + 12 + w, top + 47, 0xffd9edf1);
    g.drawString(font, text("gate"), left + 16 + 2 * w, top + 47, 0xffd9edf1);
    Button selected = cells[recorder.selected()];
    g.renderOutline(
        selected.getX() - 1, selected.getY() - 1, selected.getWidth() + 2, 20, 0xffffc36a);
    SequencerVisuals.advance(ClientRuntime.visualSample());
    var runtime = SequencerVisuals.steps(state.id());
    int step = runtime == null ? -1 : runtime.step();
    if (step >= 0) {
      Button b = cells[step];
      g.fill(b.getX(), b.getY() + 18, b.getX() + b.getWidth(), b.getY() + 20, 0xff65d7e5);
    }
    Component message =
        text(
            "position",
            recorder.selected() + 1,
            step < 0 ? "—" : step + 1,
            (int) state.value(Parameter.OCTAVE));
    g.drawString(font, message, left + 8, top + 222, 0xffd9edf1);
    Component detail =
        runtime != null && runtime.pending()
            ? Component.translatable("modularcraft.euclidean.pending")
            : Component.translatable("modularcraft.error." + status);
    if (runtime != null && !runtime.pending() && status.equals("ready"))
      detail =
          Component.translatable(
              "modularcraft.euclidean."
                  + (!runtime.running()
                      ? "stopped"
                      : runtime.external() ? "external" : "internal"));
    if (runtime != null && runtime.rejectedClocks() > 0)
      detail = Component.translatable("modularcraft.step.rejected", runtime.rejectedClocks());
    g.drawString(
        font,
        font.plainSubstrByWidth(detail.getString(), panel - 210),
        left + 140,
        top + 203,
        0xffe2c88c);
    record.setTooltip(Tooltip.create(detail));
  }

  private final class Piano extends AbstractWidget {
    Piano(int x, int y, int width, int height) {
      super(x, y, width, height, Component.translatable("modularcraft.keyboard_help"));
    }

    int hit(double x, double y) {
      return KeyboardInput.hit(x - getX(), y - getY(), getWidth(), getHeight());
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
      double w = getWidth() / 7.0;
      for (int i = 0; i < 7; i++)
        g.fill(
            getX() + (int) (i * w),
            getY(),
            getX() + (int) ((i + 1) * w) - 1,
            getY() + height,
            0xffd9edf1);
      for (int boundary : KeyboardInput.BOUNDARY)
        g.fill(
            getX() + (int) ((boundary - .3) * w),
            getY(),
            getX() + (int) ((boundary + .3) * w),
            getY() + (int) (height * .62),
            0xff18212a);
      for (int i = 0; i < 7; i++) {
        int semitone = KeyboardInput.WHITE[i];
        g.drawCenteredString(
            font,
            NOTES[semitone] + " / " + SHORTCUTS.charAt(semitone),
            getX() + (int) ((i + .5) * w),
            getY() + height - 10,
            0xff18212a);
      }
      if (isFocused())
        g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, height + 2, 0xffffc36a);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      output.add(NarratedElementType.TITLE, getMessage());
    }
  }
}
