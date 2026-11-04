package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Non-pausing server-authoritative editor. Numeric fields retain drafts until explicit Apply. */
public final class ModuleScreen extends Screen {
  private ModuleState state;
  private boolean editable, gate;
  private String status;
  private int left, top, panelWidth;
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
  }

  public UUID identity() {
    return state.id();
  }

  public void accept(ModuleState accepted, boolean canEdit, String message) {
    state = accepted;
    editable = canEdit;
    status = message;
    for (Parameter p : fields.keySet()) {
      EditBox field = fields.get(p);
      if (!field.isFocused()) field.setValue(format(state.value(p)));
      sliders.get(p).accepted(state.value(p));
    }
    if (enabled != null)
      enabled.setMessage(
          Component.translatable(state.enabled() ? "modularcraft.disable" : "modularcraft.enable"));
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
    panelWidth = Math.min(420, width - 16);
    left = (width - panelWidth) / 2;
    top = Math.max(6, (height - (state.kind().parameters.size() * 38 + 100)) / 2);
    int row = top + 30;
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
      row += 38;
    }
    if (state.kind() == ModuleKind.ENVELOPE) {
      Button hold =
          Button.builder(Component.translatable("modularcraft.hold_gate"), button -> hold())
              .bounds(left + 10, row, 190, 20)
              .build();
      hold.active = editable;
      addRenderableWidget(hold);
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
    }
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.local_audio"),
                button -> minecraft.setScreen(new AudioSettingsScreen(this)))
            .bounds(left + 10, row + 28, 150, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(left + panelWidth - 90, row + 28, 80, 20)
            .build());
  }

  private void apply(Parameter p, String text) {
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
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    graphics.fill(
        left,
        top,
        left + panelWidth,
        Math.min(height - 4, top + state.kind().parameters.size() * 38 + 100),
        0xf018212a);
    graphics.drawString(font, title, left + 10, top + 10, 0xffd9edf1);
    int row = top + 30;
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
      row += 38;
    }
    graphics.drawString(
        font,
        Component.translatable("modularcraft.error." + status),
        left + 10,
        Math.min(height - 12, row + 53),
        0xffe2c88c);
    super.render(graphics, mouseX, mouseY, partial);
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
