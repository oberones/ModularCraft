package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.SequencerSettings;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Four scrollable channel cards. Only accepted server edits change musical state. */
public final class SequencerScreen extends Screen {
  private record Positioned(AbstractWidget widget, int y) {}

  private ModuleState state;
  private boolean editable, awaiting;
  private String status;
  private long submitted;
  private int left, panel, columns, card, contentTop, contentBottom, scroll, top;
  private final Map<Parameter, EditBox> fields = new EnumMap<>(Parameter.class);
  private final List<Positioned> scrolling = new ArrayList<>();
  private final List<AbstractWidget> editing = new ArrayList<>();
  private final Button[] mutes = new Button[4];
  private Button start, stop, redstone;

  public SequencerScreen(ModuleState state, boolean editable, String status) {
    super(Component.translatable("block.modularcraft.euclidean_sequencer"));
    this.state = state;
    this.editable = editable;
    this.status = status;
  }

  public UUID identity() {
    return state.id();
  }

  public void accept(ModuleState module, boolean canEdit, String message) {
    state = module;
    editable = canEdit;
    status = message;
    awaiting = false;
    fields.forEach(
        (p, field) -> {
          if (!field.isFocused()) field.setValue(Integer.toString((int) state.value(p)));
        });
    update();
  }

  private static Component text(String key) {
    return Component.translatable("modularcraft.euclidean." + key);
  }

  @Override
  protected void init() {
    fields.clear();
    scrolling.clear();
    editing.clear();
    panel = Math.min(560, width - 16);
    left = (width - panel) / 2;
    columns = panel >= 500 ? 2 : 1;
    card = panel / columns;
    top = Math.max(4, (height - (74 + 4 / columns * 112 + 44)) / 2);
    contentTop = top + 74;
    contentBottom = Math.min(height - 46, contentTop + 4 / columns * 112);
    start = command("start", Messages.Action.SEQUENCER_START, left + 8, top + 23);
    stop = command("stop", Messages.Action.SEQUENCER_STOP, left + 92, top + 23);
    command("reset", Messages.Action.SEQUENCER_RESET, left + 176, top + 23);
    numeric(Parameter.TEMPO, left + 46, top + 47, false);
    redstone =
        Button.builder(
                text("redstone_off"),
                b ->
                    submit(
                        Parameter.REDSTONE_MODE, state.value(Parameter.REDSTONE_MODE) == 0 ? 1 : 0))
            .bounds(left + 142, top + 47, Math.min(160, panel - 150), 18)
            .tooltip(Tooltip.create(text("redstone_help")))
            .build();
    addRenderableWidget(redstone);
    editing.add(redstone);
    for (int channel = 0; channel < 4; channel++) {
      int c = channel,
          x = left + channel % columns * card,
          y = contentTop + channel / columns * 112;
      for (int control = 0; control < 3; control++)
        numeric(
            SequencerSettings.parameter(channel, control),
            x + card - 93,
            y + 24 + control * 23,
            true);
      Button mute =
          Button.builder(
                  text("mute"),
                  button ->
                      submit(
                          SequencerSettings.parameter(c, 3),
                          state.value(SequencerSettings.parameter(c, 3)) == 0 ? 1 : 0))
              .bounds(x + 8, y + 87, 82, 18)
              .build();
      addRenderableWidget(mute);
      editing.add(mute);
      scrolling.add(new Positioned(mute, y + 87));
      mutes[channel] = mute;
    }
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.local_audio"),
                b -> minecraft.setScreen(new AudioSettingsScreen(this)))
            .bounds(left + 8, contentBottom + 22, 114, 20)
            .build());
    addRenderableWidget(
        Button.builder(text("up"), b -> scroll(-112))
            .bounds(left + 126, contentBottom + 22, 34, 20)
            .build());
    addRenderableWidget(
        Button.builder(text("down"), b -> scroll(112))
            .bounds(left + 164, contentBottom + 22, 34, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + panel - 70, contentBottom + 22, 62, 20)
            .build());
    scroll(0);
    update();
  }

  private Button command(String label, Messages.Action action, int x, int y) {
    Button button =
        Button.builder(
                text(label),
                b -> {
                  ClientRuntime.send(state, action, Parameter.TEMPO, 0);
                  awaiting = true;
                  submitted = System.nanoTime();
                  status = "awaiting";
                  update();
                })
            .bounds(x, y, 80, 20)
            .build();
    addRenderableWidget(button);
    editing.add(button);
    return button;
  }

  private void numeric(Parameter parameter, int x, int y, boolean moves) {
    Component label =
        Component.translatable(
            "parameter.modularcraft." + parameter.name().toLowerCase(Locale.ROOT));
    EditBox field = new EditBox(font, x, y, 42, 18, label);
    field.setMaxLength(3);
    field.setValue(Integer.toString((int) state.value(parameter)));
    field.setTooltip(
        Tooltip.create(
            Component.translatable(
                "modularcraft.euclidean.range", label, (int) parameter.min, (int) parameter.max)));
    Button apply =
        Button.builder(
                text("set"),
                b -> {
                  try {
                    submit(parameter, parameter.validate(Double.parseDouble(field.getValue())));
                  } catch (IllegalArgumentException invalid) {
                    status = "range";
                  }
                })
            .bounds(x + 46, y, 38, 18)
            .build();
    fields.put(parameter, field);
    addRenderableWidget(field);
    addRenderableWidget(apply);
    editing.add(field);
    editing.add(apply);
    if (moves) {
      scrolling.add(new Positioned(field, y));
      scrolling.add(new Positioned(apply, y));
    }
  }

  private void submit(Parameter parameter, double value) {
    try {
      state.withValue(parameter, value);
      ClientRuntime.send(state, Messages.Action.PARAMETER, parameter, value);
      awaiting = true;
      submitted = System.nanoTime();
      status = "awaiting";
      update();
    } catch (IllegalArgumentException invalid) {
      status = "sequencer_pattern";
    }
  }

  private void update() {
    for (AbstractWidget widget : editing) widget.active = editable && !awaiting;
    boolean controlled = state.value(Parameter.REDSTONE_MODE) == 1;
    if (start != null) start.active = editable && !awaiting && !controlled;
    if (stop != null) stop.active = editable && !awaiting && !controlled;
    if (redstone != null) redstone.setMessage(text(controlled ? "redstone_on" : "redstone_off"));
    fields.values().forEach(field -> field.setEditable(editable && !awaiting));
    for (int c = 0; c < 4; c++)
      if (mutes[c] != null)
        mutes[c].setMessage(
            text(state.value(SequencerSettings.parameter(c, 3)) == 1 ? "unmute" : "mute"));
  }

  private void scroll(int amount) {
    scroll =
        Math.clamp(
            scroll + amount, 0, Math.max(0, (4 / columns) * 112 - (contentBottom - contentTop)));
    for (Positioned positioned : scrolling) {
      AbstractWidget widget = positioned.widget();
      widget.setY(positioned.y() - scroll);
      widget.visible =
          widget.getY() >= contentTop && widget.getY() + widget.getHeight() <= contentBottom;
    }
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    scroll((int) (-vertical * 28));
    return true;
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
      scroll(key == GLFW.GLFW_KEY_PAGE_DOWN ? 112 : -112);
      return true;
    }
    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
      for (var entry : fields.entrySet())
        if (entry.getValue().isFocused() && editable && !awaiting) {
          try {
            submit(
                entry.getKey(),
                entry.getKey().validate(Double.parseDouble(entry.getValue().getValue())));
          } catch (IllegalArgumentException invalid) {
            status = "range";
          }
          return true;
        }
    }
    return super.keyPressed(key, scan, modifiers);
  }

  @Override
  public void tick() {
    for (var patch : ClientRuntime.patches())
      for (ModuleState module : patch.modules())
        if (module.id().equals(state.id()) && module.revision() > state.revision())
          accept(module, editable && module.loaded(), "ready");
    if (awaiting && System.nanoTime() - submitted > 1_000_000_000L) {
      awaiting = false;
      status = "stale";
      update();
    }
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void removed() {
    ClientRuntime.send(state, Messages.Action.CLOSE, Parameter.TEMPO, 0);
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    super.renderBackground(graphics, mouseX, mouseY, partial);
    graphics.fill(left, top, left + panel, contentBottom + 44, 0xf018212a);
    graphics.drawString(font, title, left + 8, top + 6, 0xffd9edf1);
    graphics.drawString(font, text("bpm"), left + 8, top + 52, 0xffd9edf1);
    SequencerVisuals.advance(ClientRuntime.visualSample());
    Sequencer runtime = SequencerVisuals.get(state.id());
    String mode =
        runtime == null || !runtime.running()
            ? "stopped"
            : runtime.external() ? "external" : "internal";
    graphics.drawString(
        font, text(mode), left + panel - 8 - font.width(text(mode)), top + 6, 0xffd9edf1);
    graphics.enableScissor(left, contentTop, left + panel, contentBottom);
    for (int c = 0; c < 4; c++) {
      int x = left + c % columns * card, y = contentTop + c / columns * 112 - scroll;
      graphics.fill(x + 4, y + 1, x + card - 4, y + 109, 0xff263743);
      graphics.drawString(
          font,
          Component.translatable("modularcraft.euclidean.channel", Character.toString('A' + c)),
          x + 10,
          y + 6,
          SequencerVisuals.COLORS[c]);
      var channel = SequencerSettings.from(state).channels().get(c);
      drawRing(
          graphics,
          x + 49,
          y + 53,
          27,
          channel,
          runtime == null ? -1 : runtime.step(c),
          SequencerVisuals.COLORS[c]);
      for (int control = 0; control < 3; control++)
        graphics.drawString(
            font,
            text(control == 0 ? "steps" : control == 1 ? "hits" : "rotation"),
            x + card - 150,
            y + 29 + control * 23,
            0xffe0e8ee);
      if (runtime != null && runtime.pending())
        graphics.drawString(font, text("pending"), x + 101, y + 91, 0xffffc36a);
    }
    graphics.disableScissor();
    Component message =
        runtime != null && runtime.rejectedClocks() > 0
            ? text("fast_clock")
            : status.equals("ready")
                ? text("legend")
                : Component.translatable("modularcraft.error." + status);
    graphics.drawString(
        font,
        font.plainSubstrByWidth(message.getString(), panel - 16),
        left + 8,
        contentBottom + 8,
        0xffd9edf1);
  }

  public static void drawRing(
      GuiGraphics graphics,
      int cx,
      int cy,
      int radius,
      SequencerSettings.Channel pattern,
      int playhead,
      int color) {
    for (int step = 0; step < pattern.steps(); step++) {
      int x = cx + (int) Math.round(SequencerVisuals.x(pattern.steps(), step) * radius),
          y = cy + (int) Math.round(SequencerVisuals.y(pattern.steps(), step) * radius);
      if (step == playhead) graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xfff2f5f7);
      graphics.fill(x - 2, y - 2, x + 3, y + 3, pattern.muted() ? 0xff7a858c : color);
      if (!pattern.hit(step)) graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xff263743);
    }
    if (pattern.muted()) {
      graphics.fill(cx - 4, cy - 5, cx - 1, cy + 6, 0xffc6cdd1);
      graphics.fill(cx + 2, cy - 5, cx + 5, cy + 6, 0xffc6cdd1);
    }
  }
}
