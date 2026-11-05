package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.OscillatorNote;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Non-pausing server-authoritative editor. Numeric fields retain drafts until explicit Apply. */
public final class ModuleScreen extends Screen {
  private ModuleState state;
  private boolean editable, gate;
  private String status;
  private int left, top, panelWidth, panelHeight, rowHeight, headerHeight;
  private boolean compact;
  private final OscillatorNote note;
  private NoteDropdown noteChoice, octaveChoice;
  private Button setNote;
  private final List<AbstractWidget> editing = new ArrayList<>();
  private long renewed;
  private final Map<Parameter, EditBox> fields = new EnumMap<>(Parameter.class);
  private final Map<Parameter, ValueSlider> sliders = new EnumMap<>(Parameter.class);
  private Button enabled;

  public ModuleScreen(ModuleState state, boolean editable, String status) {
    super(
        Component.translatable(
            "block.modularcraft." + state.kind().name().toLowerCase(Locale.ROOT)));
    this.state = state;
    this.editable = editable;
    this.status = status;
    note =
        state.kind().isOscillator() ? new OscillatorNote(state.value(Parameter.FREQUENCY)) : null;
  }

  public UUID identity() {
    return state.id();
  }

  public void accept(ModuleState accepted, boolean canEdit, String message) {
    state = accepted;
    editable = canEdit;
    status = message;
    if (note != null) note.accept(state.value(Parameter.FREQUENCY), state.revision());
    for (Parameter p : fields.keySet()) {
      EditBox field = fields.get(p);
      if (!field.isFocused()) field.setValue(format(state.value(p)));
      if (!editable) sliders.get(p).dirty = false;
      sliders.get(p).accepted(state.value(p));
    }
    if (enabled != null)
      enabled.setMessage(
          Component.translatable(state.enabled() ? "modularcraft.disable" : "modularcraft.enable"));
    updateEditing();
    if (!editable) release();
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("[.]$", "");
  }

  private static Component label(Parameter p) {
    return Component.translatable("parameter.modularcraft." + p.name().toLowerCase(Locale.ROOT));
  }

  @Override
  protected void init() {
    fields.clear();
    sliders.clear();
    editing.clear();
    enabled = null;
    compact = note != null && height < 280;
    rowHeight = compact ? 34 : 38;
    headerHeight = compact ? 26 : 30;
    panelHeight =
        headerHeight
            + state.kind().parameters.size() * rowHeight
            + (note == null ? 0 : compact ? 42 : 48)
            + (note == null ? 70 : compact ? 56 : 74);
    panelWidth = Math.min(420, width - 16);
    left = (width - panelWidth) / 2;
    top = Math.max(6, (height - panelHeight) / 2);
    int row = top + headerHeight;
    for (Parameter p : state.kind().parameters) {
      EditBox field = new EditBox(font, left + panelWidth - 138, row + 13, 76, 18, label(p));
      field.setMaxLength(16);
      field.setValue(format(state.value(p)));
      field.setEditable(editable);
      fields.put(p, field);
      addRenderableWidget(field);
      ValueSlider slider = new ValueSlider(left + 10, row + 13, panelWidth - 160, 18, p);
      slider.active = editable;
      sliders.put(p, slider);
      addRenderableWidget(slider);
      Button apply =
          Button.builder(
                  Component.translatable("modularcraft.apply"),
                  button -> apply(p, field.getValue()))
              .bounds(left + panelWidth - 58, row + 13, 48, 18)
              .build();
      apply.active = editable;
      addRenderableWidget(apply);
      editing.add(field);
      editing.add(slider);
      editing.add(apply);
      row += rowHeight;
      if (p == Parameter.FREQUENCY && note != null) {
        initNotes(row);
        row += compact ? 42 : 48;
      }
    }
    if (state.kind() == ModuleKind.ENVELOPE) {
      Button hold =
          Button.builder(Component.translatable("modularcraft.hold_gate"), button -> hold())
              .bounds(left + 10, row, 190, 20)
              .build();
      hold.active = editable;
      addRenderableWidget(hold);
      editing.add(hold);
    } else if (state.kind() == ModuleKind.SPEAKER) {
      enabled =
          Button.builder(
                  Component.translatable(
                      state.enabled() ? "modularcraft.disable" : "modularcraft.enable"),
                  button ->
                      ClientRuntime.send(
                          state, Messages.Action.ENABLE, Parameter.VOLUME, state.enabled() ? 0 : 1))
              .bounds(left + 10, row, 190, 20)
              .build();
      enabled.active = editable;
      addRenderableWidget(enabled);
      editing.add(enabled);
    }
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.local_audio"),
                button -> minecraft.setScreen(new AudioSettingsScreen(this)))
            .bounds(left + 10, row + (compact ? 8 : 28), 150, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(left + panelWidth - 90, row + (compact ? 8 : 28), 80, 20)
            .build());
  }

  private void initNotes(int row) {
    int choiceWidth = (panelWidth - 108) / 2;
    noteChoice =
        new NoteDropdown(
            left + 10,
            row,
            choiceWidth,
            height,
            "modularcraft.note.letter",
            12,
            OscillatorNote::name,
            value -> {
              note.select(value, note.octave());
              updateEditing();
            });
    octaveChoice =
        new NoteDropdown(
            left + 16 + choiceWidth,
            row,
            choiceWidth,
            height,
            "modularcraft.note.octave",
            9,
            Integer::toString,
            value -> {
              note.select(note.semitone(), value);
              updateEditing();
            });
    setNote =
        Button.builder(
                Component.translatable("modularcraft.note.set"),
                button -> {
                  if (!editable || !note.valid()) return;
                  double frequency = note.submit(state.revision());
                  ClientRuntime.send(
                      state, Messages.Action.PARAMETER, Parameter.FREQUENCY, frequency);
                  status = "awaiting";
                })
            .bounds(left + panelWidth - 86, row, 76, 18)
            .build();
    setNote.setTooltip(Tooltip.create(Component.translatable("modularcraft.note.help")));
    for (AbstractWidget widget : List.of(noteChoice, octaveChoice, setNote)) {
      editing.add(widget);
      addRenderableWidget(widget);
    }
    updateEditing();
  }

  private void updateEditing() {
    for (AbstractWidget widget : editing) widget.active = editable;
    for (EditBox field : fields.values()) field.setEditable(editable);
    if (noteChoice != null) {
      noteChoice.value(note.semitone());
      octaveChoice.value(note.octave());
      setNote.active = editable && note.valid();
      if (!editable) {
        noteChoice.close();
        octaveChoice.close();
      }
    }
  }

  private NoteDropdown popup() {
    if (noteChoice != null && noteChoice.expanded()) return noteChoice;
    if (octaveChoice != null && octaveChoice.expanded()) return octaveChoice;
    return null;
  }

  @Override
  public boolean mouseClicked(double x, double y, int button) {
    NoteDropdown menu = popup();
    if (menu != null) {
      menu.clickOption(x, y, button);
      return true;
    }
    return super.mouseClicked(x, y, button);
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    NoteDropdown menu = popup();
    if (menu != null) {
      menu.scroll(vertical);
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  private void apply(Parameter p, String text) {
    if (!editable) return;
    try {
      double value = p.validate(Double.parseDouble(text));
      ClientRuntime.send(state, Messages.Action.PARAMETER, p, value);
      status = "awaiting";
    } catch (IllegalArgumentException invalid) {
      status = "range";
    }
  }

  private void hold() {
    if (editable && !gate) {
      gate = true;
      renewed = System.nanoTime();
      ClientRuntime.send(state, Messages.Action.GATE, Parameter.SUSTAIN, 1);
    }
  }

  private void release() {
    if (gate) {
      gate = false;
      ClientRuntime.send(state, Messages.Action.GATE, Parameter.SUSTAIN, 0);
    }
  }

  @Override
  public void tick() {
    if (gate && System.nanoTime() - renewed > 200_000_000L) {
      renewed = System.nanoTime();
      ClientRuntime.send(state, Messages.Action.GATE, Parameter.SUSTAIN, 1);
    }
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    release();
    for (ValueSlider slider : sliders.values()) slider.commit();
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    NoteDropdown menu = popup();
    if (menu != null) {
      if (key == GLFW.GLFW_KEY_TAB) menu.close();
      else {
        menu.keyPressed(key, scan, modifiers);
        return true;
      }
    }
    if (key == GLFW.GLFW_KEY_SPACE
        && state.kind() == ModuleKind.ENVELOPE
        && !(getFocused() instanceof EditBox)) {
      hold();
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public boolean keyReleased(int key, int scan, int modifiers) {
    if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
      release();
    for (ValueSlider slider : sliders.values()) slider.commit();
    return super.keyReleased(key, scan, modifiers);
  }

  @Override
  public void removed() {
    release();
    ClientRuntime.send(state, Messages.Action.CLOSE, Parameter.LEVEL, 0);
  }

  @Override
  public void onClose() {
    minecraft.setScreen(null);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    super.renderBackground(graphics, mouseX, mouseY, partial);
    graphics.fill(
        left, top, left + panelWidth, Math.min(height - 4, top + panelHeight), 0xf018212a);
    graphics.drawString(font, title, left + 10, top + 10, 0xffd9edf1);
    int row = top + headerHeight;
    for (Parameter p : state.kind().parameters) {
      graphics.drawString(
          font,
          label(p).getString()
              + " · "
              + format(state.value(p))
              + " "
              + p.unit
              + " ["
              + format(p.min)
              + "–"
              + format(p.max)
              + "]",
          left + 10,
          row,
          0xffa6c7cf);
      row += rowHeight;
      if (p == Parameter.FREQUENCY && note != null) {
        Component preview =
            note.valid()
                ? Component.translatable(
                    "modularcraft.note.preview",
                    OscillatorNote.name(note.semitone()) + note.octave(),
                    format(note.frequency()))
                : Component.translatable("modularcraft.note.range");
        graphics.drawString(
            font, preview, left + 10, row + 23, note.valid() ? 0xffa6c7cf : 0xffe2c88c);
        row += compact ? 42 : 48;
      }
    }
    Component message = Component.translatable("modularcraft.error." + status);
    int statusY = Math.min(height - 12, row + (compact ? 34 : 53));
    if (note != null)
      graphics.drawWordWrap(font, message, left + 10, statusY, panelWidth - 20, 0xffe2c88c);
    else graphics.drawString(font, message, left + 10, statusY, 0xffe2c88c);
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    super.render(graphics, mouseX, mouseY, partial);
    NoteDropdown menu = popup();
    if (menu != null) menu.renderPopup(graphics, font, mouseX, mouseY);
  }

  private final class ValueSlider extends AbstractSliderButton {
    private final Parameter parameter;
    private boolean dirty;

    ValueSlider(int x, int y, int width, int height, Parameter parameter) {
      super(
          x,
          y,
          width,
          height,
          Component.empty(),
          (state.value(parameter) - parameter.min) / (parameter.max - parameter.min));
      this.parameter = parameter;
      updateMessage();
    }

    @Override
    protected void updateMessage() {
      if (parameter != null)
        setMessage(
            Component.literal(
                format(parameter.min + value * (parameter.max - parameter.min))
                    + " "
                    + parameter.unit));
    }

    @Override
    protected void applyValue() {
      dirty = true;
      fields
          .get(parameter)
          .setValue(format(parameter.min + value * (parameter.max - parameter.min)));
    }

    void commit() {
      if (dirty) {
        dirty = false;
        apply(parameter, fields.get(parameter).getValue());
      }
    }

    void accepted(double accepted) {
      if (!dirty) {
        value = (accepted - parameter.min) / (parameter.max - parameter.min);
        updateMessage();
      }
    }
  }
}
