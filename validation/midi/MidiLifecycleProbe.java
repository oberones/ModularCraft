package com.malevolentgods.modularcraft.client;

public class MidiLifecycleProbe {
  static void await(MidiInput midi, String wanted) throws Exception {
    long until = System.nanoTime() + 4_000_000_000L;
    while (System.nanoTime() < until) {
      midi.tick();
      if (midi.status().equals(wanted)) return;
      if (midi.status().equals("midi_timeout")) throw new Exception(midi.status());
      Thread.sleep(10);
    }
    throw new Exception("Expected " + wanted + "; got " + midi.status());
  }
  static long threads() {
    return Thread.getAllStackTraces().keySet().stream()
      .filter(t -> t.getName().equals("Java Sound MidiInDevice Thread")).count();
  }
  public static void main(String[] args) throws Exception {
    MidiInput.Port port = new JavaMidiBackend().ports().stream()
      .filter(p -> p.name().equals("IN_ALL")).findFirst().orElseThrow();
    System.out.println("BASELINE native threads=" + threads());
    try (MidiInput midi = new MidiInput()) {
      for (int i = 0; i < 20; i++) {
        midi.target(port.key(), null, 0, true);
        await(midi, "midi_ready");
        long until = System.nanoTime() + 1_100_000_000L;
        while (System.nanoTime() < until) { midi.tick(); Thread.sleep(10); }
        if (!midi.status().equals("midi_ready")) throw new Exception(midi.status());
        midi.target(port.key(), null, 0, false);
        await(midi, "midi_paused");
        // Paused is immediate UI state; allow the pending close/result to finish.
        until = System.nanoTime() + 1_800_000_000L;
        while (System.nanoTime() < until) { midi.tick(); Thread.sleep(10); }
        System.out.println("CYCLE " + (i+1) + " status=" + midi.status() + " nativeThreads=" + threads());
        long children = ProcessHandle.current().children().filter(ProcessHandle::isAlive).count();
        if (threads() != 0 || children != 0) throw new Exception("Retained native threads/children: " + threads() + "/" + children);
        System.out.println("resources returned to baseline; children=" + children);
      }
    } catch (Exception failure) {
      failure.printStackTrace(); System.exit(1);
    }
    System.out.println("PASS 20 lifecycle cycles");
    System.exit(0);
  }
}
