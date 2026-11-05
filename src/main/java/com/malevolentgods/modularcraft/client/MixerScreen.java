package com.malevolentgods.modularcraft.client;

import com.malevolentgods.modularcraft.domain.MixerEdits;
import com.malevolentgods.modularcraft.domain.MixerSettings;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.dsp.MixerMeter;
import com.malevolentgods.modularcraft.network.Messages;
import java.util.LinkedHashMap;
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

/** Non-pausing five-strip editor; only acknowledged server edits change the audio graph. */
public final class MixerScreen extends Screen {
  private ModuleState state;
  private boolean editable;
  private String status;
  private MixerEdits edits;
  private final EditBox[] fields = new EditBox[5];
  private final Fader[] faders = new Fader[5];
  private final Button[] mutes = new Button[5], solos = new Button[4];
  private final Map<AbstractWidget, Integer> positions = new LinkedHashMap<>();
  private int left,
      top,
      panelWidth,
      columns,
      cardWidth,
      cardHeight,
      faderHeight,
      contentTop,
      contentBottom,
      scroll,
      maximumScroll;
  private MixerMeter.Reading meters;

  public MixerScreen(ModuleState state, boolean editable, String status) {
    super(Component.translatable("block.modularcraft.mixer"));
    this.state = state;
    this.editable = editable;
    this.status = status;
    edits = new MixerEdits(state, editable);
  }

  public UUID identity() {
    return state.id();
  }

  public void accept(ModuleState accepted, boolean canEdit, String message) {
    if (accepted.revision() < state.revision()) return;
    state = accepted;
    editable = canEdit;
    status = message;
    edits.accept(accepted, canEdit, message);
    boolean rejected = !canEdit || (!message.equals("ready") && !message.equals("patch_unloaded"));
    update(rejected);
  }

  private static Component text(String key) {
    return Component.translatable("modularcraft.mixer." + key);
  }

  private static Component channel(int i) {
    return i == 4 ? text("master") : Component.translatable("modularcraft.mixer.input", i + 1);
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("[.]$", "");
  }

  @Override
  protected void init() {
    // Returning from local-audio settings creates a fresh queue; removed() cancelled the old one.
    if (edits.closed()) {
      for (var patch : ClientRuntime.patches())
        for (var module : patch.modules())
          if (module.id().equals(state.id()) && module.revision() >= state.revision())
            state = module;
      edits = new MixerEdits(state, editable);
    }
    positions.clear();
    panelWidth = Math.min(650, width - 16);
    columns = Math.max(1, Math.min(5, panelWidth / 122));
    cardWidth = panelWidth / columns;
    left = (width - panelWidth) / 2;
    int rows = (5 + columns - 1) / columns;
    faderHeight = Math.clamp(height - 196, 32, 96);
    cardHeight = faderHeight + 98;
    int panelHeight = Math.min(height - 12, rows * cardHeight + 86);
    top = Math.max(6, (height - panelHeight) / 2);
    contentTop = top + 30;
    contentBottom = top + panelHeight - 56;
    maximumScroll = Math.max(0, rows * cardHeight - (contentBottom - contentTop));
    for (int i = 0; i < 5; i++) {
      final int index = i;
      int x = left + i % columns * cardWidth, y = contentTop + i / columns * cardHeight;
      Fader fader = new Fader(x + 12, y + 24, i);
      faders[i] = fader;
      add(fader);
      EditBox field =
          new EditBox(font, x + 8, y + faderHeight + 30, cardWidth - 68, 18, channel(i));
      field.setMaxLength(16);
      field.setTooltip(
          Tooltip.create(Component.translatable("modularcraft.mixer.level_help", channel(i))));
      fields[i] = field;
      add(field);
      add(
          Button.builder(Component.translatable("modularcraft.apply"), b -> apply(index))
              .bounds(x + cardWidth - 46, y + faderHeight + 30, 38, 18)
              .build());
      Button mute =
          Button.builder(text("mute_off"), b -> toggle(MixerSettings.mute(index)))
              .bounds(x + 8, y + faderHeight + 52, cardWidth - 16, 18)
              .build();
      mute.setTooltip(
          Tooltip.create(Component.translatable("modularcraft.mixer.mute_help", channel(i))));
      mutes[i] = mute;
      add(mute);
      if (i < 4) {
        Button solo =
            Button.builder(text("solo_off"), b -> toggle(MixerSettings.solo(index)))
                .bounds(x + 8, y + faderHeight + 74, cardWidth - 16, 18)
                .build();
        solo.setTooltip(Tooltip.create(text("solo_help")));
        solos[i] = solo;
        add(solo);
      }
    }
    addRenderableWidget(
        Button.builder(
                Component.translatable("modularcraft.local_audio"),
                b -> minecraft.setScreen(new AudioSettingsScreen(this)))
            .bounds(left + 8, contentBottom + 6, Math.min(150, panelWidth - 104), 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(left + panelWidth - 88, contentBottom + 6, 80, 20)
            .build());
    update(true);
    layout();
  }

  private void add(AbstractWidget widget) {
    positions.put(widget, widget.getY());
    addRenderableWidget(widget);
  }

  private void layout() {
    scroll = Math.clamp(scroll, 0, maximumScroll);
    positions.forEach(
        (widget, original) -> {
          widget.setY(original - scroll);
          widget.visible = widget.getY() >= contentTop && widget.getBottom() <= contentBottom;
        });
  }

  private void update(boolean resetDrafts) {
    positions.keySet().forEach(widget -> widget.active = editable);
    for (int i = 0; i < 5; i++) {
      if (fields[i] == null) continue;
      fields[i].setEditable(editable);
      if (resetDrafts || (!fields[i].isFocused() && !faders[i].dragging))
        fields[i].setValue(format(edits.value(MixerSettings.level(i))));
      if (resetDrafts || !faders[i].dragging)
        faders[i].accepted(edits.value(MixerSettings.level(i)));
      if (resetDrafts) faders[i].dragging = false;
      mutes[i].setMessage(text(edits.value(MixerSettings.mute(i)) == 1 ? "mute_on" : "mute_off"));
      if (i < 4)
        solos[i].setMessage(text(edits.value(MixerSettings.solo(i)) == 1 ? "solo_on" : "solo_off"));
    }
  }

  private void apply(int channel) {
    try {
      edits.offer(MixerSettings.level(channel), Double.parseDouble(fields[channel].getValue()));
      status = "awaiting";
      update(false);
    } catch (IllegalArgumentException invalid) {
      status = "range";
    }
  }

  private void toggle(Parameter parameter) {
    edits.offer(parameter, edits.value(parameter) == 1 ? 0 : 1);
    status = "awaiting";
    update(false);
  }

  @Override
  public void tick() {
    MixerEdits.Edit edit = edits.poll(System.nanoTime());
    if (edit != null)
      ClientRuntime.send(edit.state(), Messages.Action.PARAMETER, edit.parameter(), edit.value());
    if (edits.timedOut()) {
      status = "mixer_timeout";
      update(true);
    }
    meters = ClientRuntime.mixerMeter(state.id());
  }

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (y >= contentTop && y <= contentBottom) {
      scroll -= (int) (vertical * 32);
      layout();
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  @Override
  public boolean mouseReleased(double x, double y, int button) {
    for (Fader fader : faders) fader.dragging = false;
    return super.mouseReleased(x, y, button);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
      scroll += key == GLFW.GLFW_KEY_PAGE_DOWN ? 96 : -96;
      layout();
      return true;
    }
    if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
      for (int i = 0; i < 5; i++)
        if (fields[i].isFocused()) {
          apply(i);
          return true;
        }
    boolean handled = super.keyPressed(key, scan, modifiers);
    if (getFocused() instanceof AbstractWidget widget && positions.containsKey(widget)) {
      int original = positions.get(widget);
      if (original - scroll < contentTop) scroll = original - contentTop;
      if (original - scroll + widget.getHeight() > contentBottom)
        scroll = original + widget.getHeight() - contentBottom;
      layout();
    }
    return handled;
  }

  @Override
  public void removed() {
    edits.close();
    meters = null;
    ClientRuntime.send(state, Messages.Action.CLOSE, MixerSettings.level(4), 0);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
    super.renderBackground(graphics, mouseX, mouseY, partial);
    graphics.fill(left, top, left + panelWidth, contentBottom + 56, 0xf018212a);
    graphics.drawString(font, title, left + 8, top + 10, 0xffd9edf1);
    if (maximumScroll > 0)
      graphics.drawString(
          font,
          Component.translatable("modularcraft.scroll_help"),
          left + panelWidth - 88,
          top + 10,
          0xffa6c7cf);
    graphics.enableScissor(left, contentTop, left + panelWidth, contentBottom);
    for (int i = 0; i < 5; i++) {
      int x = left + i % columns * cardWidth, y = contentTop + i / columns * cardHeight - scroll;
      graphics.fill(
          x + 3, y + 2, x + cardWidth - 3, y + cardHeight - 4, i == 4 ? 0xff30414a : 0xff202d37);
      graphics.drawString(font, channel(i), x + 8, y + 8, 0xffd9edf1);
      graphics.drawString(font, "%", x + cardWidth - 58, y + faderHeight + 35, 0xffa6c7cf);
      int meterX = x + cardWidth - 35;
      graphics.fill(meterX, y + 24, meterX + 12, y + 24 + faderHeight, 0xff101820);
      if (meters != null) {
        double peak = meters.peaks()[i];
        int size = (int) (Math.clamp(peak, 0, 1) * faderHeight);
        graphics.fill(
            meterX,
            y + 24 + faderHeight - size,
            meterX + 12,
            y + 24 + faderHeight,
            peak > .9 ? 0xffffc36a : 0xff91c79a);
        if (meters.overloads()[i])
          graphics.drawString(font, text("overload"), x + cardWidth - 40, y + 8, 0xffff9b8a);
      }
    }
    graphics.disableScissor();
    Component message =
        meters == null && status.equals("ready")
            ? text("inactive")
            : Component.translatable("modularcraft.error." + status);
    graphics.drawWordWrap(font, message, left + 8, contentBottom + 32, panelWidth - 16, 0xffe2c88c);
    if (maximumScroll > 0) {
      int view = contentBottom - contentTop;
      int thumb = Math.max(8, view * view / (view + maximumScroll));
      int y = contentTop + scroll * (view - thumb) / maximumScroll;
      graphics.fill(left + panelWidth - 2, y, left + panelWidth, y + thumb, 0xffa6c7cf);
    }
  }

  private final class Fader extends AbstractSliderButton {
    private final int channel;
    private boolean dragging;

    Fader(int x, int y, int channel) {
      super(
          x,
          y,
          48,
          faderHeight,
          Component.empty(),
          state.value(MixerSettings.level(channel)) / 100);
      this.channel = channel;
      setTooltip(
          Tooltip.create(
              Component.translatable("modularcraft.mixer.level_help", channel(channel))));
      updateMessage();
    }

    void accepted(double percent) {
      value = percent / 100;
      updateMessage();
    }

    private void move(double y) {
      value = Math.clamp(1 - (y - getY() - 5) / (height - 10), 0, 1);
      applyValue();
      updateMessage();
    }

    @Override
    public void onClick(double x, double y) {
      dragging = true;
      move(y);
    }

    @Override
    protected void onDrag(double x, double y, double dx, double dy) {
      move(y);
    }

    @Override
    protected void applyValue() {
      edits.offer(MixerSettings.level(channel), value * 100);
      fields[channel].setValue(format(value * 100));
      status = "awaiting";
    }

    @Override
    protected void updateMessage() {
      setMessage(
          Component.translatable(
              "modularcraft.mixer.level", channel(channel), format(value * 100)));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
      if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_RIGHT) value = Math.min(1, value + .01);
      else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_LEFT)
        value = Math.max(0, value - .01);
      else if (key == GLFW.GLFW_KEY_HOME) value = 0;
      else if (key == GLFW.GLFW_KEY_END) value = 1;
      else return super.keyPressed(key, scan, modifiers);
      applyValue();
      updateMessage();
      return true;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
      int x = getX(), y = getY(), handle = y + 5 + (int) ((1 - value) * (height - 10));
      graphics.fill(x + 20, y, x + 28, y + height, 0xff101820);
      graphics.fill(
          x + 5,
          handle - 4,
          x + 43,
          handle + 4,
          !active ? 0xff53636d : isFocused() || isHovered ? 0xffd9edf1 : 0xff65d7e5);
      graphics.fill(x + 7, handle, x + 41, handle + 1, 0xff34434e);
    }
  }
}
