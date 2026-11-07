package com.malevolentgods.modularcraft.gametest.client;

import com.malevolentgods.modularcraft.client.StepSequencerScreen;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/** Synthetic accepted views exercise real widgets; absent module IDs cannot mutate the fixture. */
public final class StepReviewProbe {
  private static final List<String> FAILURES = new ArrayList<>();
  private static StepSequencerScreen timeoutScreen;
  private static ModuleState timeoutState;
  private static long timeoutStarted;

  private StepReviewProbe() {}

  public static void begin() {
    FAILURES.clear();
    recordingViews();
    rejectedViews();
    fields();
    timeoutState = module();
    timeoutScreen = screen(timeoutState);
    press(timeoutScreen, "Record");
    note(timeoutScreen);
    timeoutStarted = System.nanoTime();
    finish();
  }

  public static void timeout() {
    check(System.nanoTime() - timeoutStarted > 2_000_000_000L, "timeout elapsed");
    timeoutScreen.tick();
    check(!button(timeoutScreen, "Record").active, "timeout waits for inspection");
    timeoutScreen.accept(timeoutState.withValue(Parameter.STEP_1, 60), true, "ready");
    check(
        button(timeoutScreen, "Record").getMessage().getString().equals("Record"),
        "late acknowledgement does not resume timed-out recording");
    check(button(screen(timeoutState), "Record").active, "fresh inspection enables editor");
    timeoutScreen = null;
    finish();
  }

  private static ModuleState module() {
    return ModuleState.create(
            UUID.randomUUID(), UUID.randomUUID(), ModuleKind.STEP_SEQUENCER, 0, 64, 0)
        .withValue(Parameter.SEQUENCE_LENGTH, 2);
  }

  private static StepSequencerScreen screen(ModuleState state) {
    var screen = new StepSequencerScreen(state, true, "ready");
    screen.init(Minecraft.getInstance(), 480, 270);
    return screen;
  }

  private static void recordingViews() {
    var original = module();
    var submitted = original.bump();
    var screen = screen(submitted);
    press(screen, "Record");
    note(screen);
    screen.accept(submitted, true, "ready");
    check(!button(screen, "Rest").active, "unchanged ready view retains pending write");
    screen.accept(original, true, "ready");
    check(!button(screen, "Rest").active, "older ready view retains pending write");
    var first = submitted.withValue(Parameter.STEP_1, 60);
    screen.accept(first, true, "ready");
    screen.accept(first, true, "ready");
    check(
        button(screen, "Record").getMessage().getString().equals("Recording"),
        "duplicate acknowledgement does not advance twice");
    note(screen);
    screen.accept(first.withValue(Parameter.STEP_2, 60), true, "ready");
    check(
        button(screen, "Record").getMessage().getString().equals("Record"),
        "next note reaches final cell after unrelated views");
  }

  private static void rejectedViews() {
    for (String status : List.of("stale", "permission", "ready")) {
      var original = module();
      var screen = screen(original);
      press(screen, "Record");
      note(screen);
      boolean permission = !status.equals("permission");
      var rejected = status.equals("ready") ? original.withValue(Parameter.STEP_1, 67) : original;
      screen.accept(rejected, permission, status);
      check(button(screen, "Rest").active == permission, "rejection unlock/permission: " + status);
      screen.accept(rejected, true, "ready");
      note(screen);
      var accepted = rejected.withValue(Parameter.STEP_1, 60);
      screen.accept(accepted, true, "ready");
      check(
          button(screen, "Record").getMessage().getString().equals("Recording"),
          "rejection preserves selected cell: " + status);
      note(screen);
      screen.accept(accepted.withValue(Parameter.STEP_2, 60), true, "ready");
      check(
          button(screen, "Record").getMessage().getString().equals("Record"),
          "recording recovers after rejection: " + status);
    }
  }

  private static void fields() {
    var original = module();
    var screen = screen(original);
    var changed =
        original
            .withValue(Parameter.SEQUENCE_LENGTH, 8)
            .withValue(Parameter.TEMPO, 137)
            .withValue(Parameter.GATE_TIME, 250);
    screen.accept(changed, true, "ready");
    check(values(screen).equals(List.of("8", "137", "250")), "all unfocused fields refresh");
    var fields = fields(screen);
    for (int i = 0; i < fields.size(); i++) {
      screen.setFocused(fields.get(i));
      fields.get(i).setValue("123");
      screen.accept(changed.bump(), true, "ready");
      check(fields.get(i).getValue().equals("123"), "focused draft survives: " + i);
      screen.setFocused(null);
      screen.accept(changed.bump(), true, "ready");
    }
    screen.resize(Minecraft.getInstance(), 380, 240);
    screen.accept(original, true, "ready");
    check(values(screen).equals(List.of("2", "120", "100")), "fields refresh after resize");
  }

  private static List<EditBox> fields(StepSequencerScreen screen) {
    return screen.children().stream()
        .filter(EditBox.class::isInstance)
        .map(EditBox.class::cast)
        .toList();
  }

  private static List<String> values(StepSequencerScreen screen) {
    return fields(screen).stream().map(EditBox::getValue).toList();
  }

  private static Button button(StepSequencerScreen screen, String prefix) {
    return screen.children().stream()
        .filter(Button.class::isInstance)
        .map(Button.class::cast)
        .filter(b -> b.getMessage().getString().startsWith(prefix))
        .findFirst()
        .orElseThrow();
  }

  private static void press(StepSequencerScreen screen, String prefix) {
    var button = button(screen, prefix);
    if (button.active) button.onPress();
  }

  private static void note(StepSequencerScreen screen) {
    screen.setFocused(
        screen.children().stream()
            .filter(
                w ->
                    w instanceof AbstractWidget widget
                        && widget.getMessage().getString().startsWith("Piano:"))
            .findFirst()
            .orElseThrow());
    screen.keyPressed(GLFW.GLFW_KEY_A, 0, 0);
    screen.keyReleased(GLFW.GLFW_KEY_A, 0, 0);
  }

  private static void check(boolean condition, String description) {
    System.out.println(
        "MODULARCRAFT_QA step review " + description + (condition ? " PASS" : " FAIL"));
    if (!condition) FAILURES.add(description);
  }

  private static void finish() {
    if (!FAILURES.isEmpty())
      throw new IllegalStateException("Step review regressions: " + FAILURES);
  }
}
