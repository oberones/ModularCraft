package com.malevolentgods.modularcraft.client;
public class MidiReconnectCapture {
  public static void main(String[] args) throws Exception {
    MidiInput.Port port = new JavaMidiBackend().ports().stream().filter(p -> p.name().equals("IN_ALL")).findFirst().orElseThrow();
    try (MidiInput midi = new MidiInput()) {
      for (int phase = 1; phase <= 2; phase++) {
        midi.target(port.key(), null, 0, true);
        MidiLifecycleProbe.await(midi, "midi_ready");
        System.out.println("PHASE " + phase + " READY: play notes now (35 seconds)");
        int[] notes = {0};
        long end = System.nanoTime() + 35_000_000_000L;
        while (System.nanoTime() < end) {
          midi.tick();
          midi.drain(event -> {
            notes[0]++;
            if (notes[0] <= 8) System.out.println("NOTE " + event);
          });
          Thread.sleep(10);
        }
        System.out.println("PHASE " + phase + " RESULT events=" + notes[0] + " status=" + midi.status());
        midi.target(port.key(), null, 0, false);
        end = System.nanoTime() + 1_800_000_000L;
        while (System.nanoTime() < end) { midi.tick(); Thread.sleep(10); }
        System.out.println("CLOSED children=" + ProcessHandle.current().children().filter(ProcessHandle::isAlive).count());
      }
    }
    System.exit(0);
  }
}
