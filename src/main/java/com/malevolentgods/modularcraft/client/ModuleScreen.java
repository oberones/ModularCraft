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
  private Button enabled, gateButton;
  private final Map<Parameter, Button> selectors = new EnumMap<>(Parameter.class);
  private final Map<AbstractWidget, Integer> positions = new java.util.LinkedHashMap<>();
  private boolean scrolling;
  private int scroll, contentBottom, maximumScroll;

  public ModuleScreen(ModuleState state, boolean editable, String status) {
    super(Component.translatable("block.modularcraft." + state.kind().registryName()));
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
          Component.translatable(
              state.suspended()
                  ? "modularcraft.reenable"
                  : state.wantsPlayback() ? "modularcraft.disable" : "modularcraft.enable"));
    selectors.forEach((p, button) -> button.setMessage(choice(p)));
    updateEditing();
    if (!editable) release();
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("[.]$", "");
  }

  private static Component label(Parameter p) {
    return Component.translatable("parameter.modularcraft." + p.name().toLowerCase(Locale.ROOT));
  }

  private static boolean categorical(Parameter p) {
    return p == Parameter.DRONE
        || p == Parameter.LFO_WAVE
        || p == Parameter.LFO_POLARITY
        || p == Parameter.LFO_DIVISION;
  }

  private Component choice(Parameter p, int value) {
    return Component.translatable(
        "modularcraft.lfo." + p.name().toLowerCase(Locale.ROOT) + "." + value);
  }

  private Component choice(Parameter p) {
    return choice(p, (int) state.value(p));
  }

  private void layoutScroll() {
    scroll = Math.clamp(scroll, 0, maximumScroll);
    positions.forEach(
        (widget, y) -> {
          widget.setY(y - scroll);
          widget.visible =
              widget.getY() >= top + headerHeight
                  && widget.getY() + widget.getHeight() <= contentBottom;
        });
  }

  @Override
  protected void init() {
    fields.clear();
    sliders.clear();
    editing.clear();
    enabled = null;
    gateButton = null;
    selectors.clear();
    positions.clear();
    scrolling =
        state.kind().hasEnvelope()
            || state.kind() == ModuleKind.FILTER
            || state.kind() == ModuleKind.LFO;
    compact = note != null && height < 280;
    rowHeight = scrolling && height < 280 ? 32 : compact ? 34 : 38;
    headerHeight = compact ? 26 : 30;
    panelHeight =
        headerHeight
            + state.kind().visibleParameters().size() * rowHeight
            + (note == null ? 0 : compact ? 42 : 48)
            + (scrolling
                ? state.kind().hasEnvelope() ? 76 : 50
                : note == null ? 70 : compact ? 56 : 74);
    panelWidth = Math.min(420, width - 16);
    left = (width - panelWidth) / 2;
    top = Math.max(6, (height - panelHeight) / 2);
    if (scrolling) {
      panelHeight = Math.min(panelHeight, height - 12);
      top = Math.max(6, (height - panelHeight) / 2);
    }
    contentBottom = top + panelHeight - (state.kind().hasEnvelope() ? 76 : 50);
    int row = top + headerHeight;
    for (Parameter p : state.kind().visibleParameters()) {
      if (categorical(p)) {
        Button selector =
            Button.builder(
                    choice(p),
                    button ->
                        apply(p, Integer.toString(((int) state.value(p) + 1) % ((int) p.max + 1))))
                .bounds(left + 10, row + 13, panelWidth - 20, 18)
                .build();
        selector.setTooltip(Tooltip.create(label(p)));
        selectors.put(p, selector);
        addRenderableWidget(selector);
        editing.add(selector);
        row += rowHeight;
        continue;
      }
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
    if (scrolling) {
      maximumScroll = Math.max(0, row - contentBottom);
      for (AbstractWidget widget : editing) positions.put(widget, widget.getY());
      layoutScroll();
      row = contentBottom + 4;
    }
    if (state.kind().hasEnvelope()) {
      Button hold =
          Button.builder(Component.translatable("modularcraft.hold_gate"), button -> hold())
              .bounds(left + 10, row, 190, 20)
              .build();
      gateButton = hold;
      hold.active = editable;
      addRenderableWidget(hold);
      editing.add(hold);
    } else if (state.kind() == ModuleKind.SPEAKER) {
      enabled =
          Button.builder(
                  Component.translatable(
                      state.suspended()
                          ? "modularcraft.reenable"
                          : state.wantsPlayback() ? "modularcraft.disable" : "modularcraft.enable"),
                  button ->
                      ClientRuntime.send(
                          state,
                          Messages.Action.ENABLE,
                          Parameter.VOLUME,
                          state.wantsPlayback() && !state.suspended() ? 0 : 1))
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
            .bounds(
                left + 10,
                row + (scrolling && !state.kind().hasEnvelope() ? 0 : compact ? 8 : 28),
                150,
                20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(
                left + panelWidth - 90,
                row + (scrolling && !state.kind().hasEnvelope() ? 0 : compact ? 8 : 28),
                80,
                20)
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
    if (scrolling && y >= top + headerHeight && y <= contentBottom) {
      scroll -= (int) (vertical * rowHeight);
      layoutScroll();
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
    if (state.kind() == ModuleKind.LFO) {
      long sample = ClientRuntime.visualSample();
      SequencerVisuals.advance(sample);
      var lfo = SequencerVisuals.lfo(state.id());
      if (lfo != null && !status.equals("range") && !status.equals("awaiting"))
        status = !lfo.external() ? "lfo_free" : lfo.waiting(sample) ? "lfo_waiting" : "lfo_sync";
    }
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
        && state.kind().hasEnvelope()
        && (getFocused() == null || getFocused() == gateButton)) {
      hold();
      return true;
    }
    if (scrolling && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) {
      scroll += key == GLFW.GLFW_KEY_PAGE_DOWN ? rowHeight * 2 : -rowHeight * 2;
      layoutScroll();
      return true;
    }
    boolean handled = super.keyPressed(key, scan, modifiers);
    if (scrolling
        && getFocused() instanceof AbstractWidget widget
        && positions.containsKey(widget)) {
      int y = positions.get(widget);
      if (y - scroll < top + headerHeight) scroll = y - top - headerHeight;
      if (y - scroll + widget.getHeight() > contentBottom)
        scroll = y + widget.getHeight() - contentBottom;
      layoutScroll();
    }
    return handled;
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
    if (scrolling && maximumScroll > 0) {
      graphics.drawString(
          font,
          Component.translatable("modularcraft.scroll_help"),
          left + panelWidth - 88,
          top + 10,
          0xffa6c7cf);
      int view = contentBottom - top - headerHeight;
      int thumb = Math.max(12, view * view / (view + maximumScroll));
      int y = top + headerHeight + scroll * (view - thumb) / maximumScroll;
      graphics.fill(
          left + panelWidth - 5,
          top + headerHeight,
          left + panelWidth - 3,
          contentBottom,
          0xff34434e);
      graphics.fill(left + panelWidth - 5, y, left + panelWidth - 3, y + thumb, 0xffa6c7cf);
    }
    int row = top + headerHeight - (scrolling ? scroll : 0);
    if (scrolling)
      graphics.enableScissor(left, top + headerHeight, left + panelWidth, contentBottom);
    for (Parameter p : state.kind().visibleParameters()) {
      graphics.drawString(
          font,
          categorical(p)
              ? label(p).getString()
              : label(p).getString()
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
    if (scrolling) {
      graphics.disableScissor();
      row = contentBottom + 4;
    }
    Component message = Component.translatable("modularcraft.error." + status);
    int statusY =
        Math.min(
            height - 12, row + (scrolling && !state.kind().hasEnvelope() ? 25 : compact ? 34 : 53));
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
