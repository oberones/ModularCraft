package com.malevolentgods.modularcraft.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

/** Java Sound port discovery and handles; called only on the MIDI worker. */
final class JavaMidiBackend implements MidiInput.Backend {
  @Override
  // Capability queries borrow unopened provider objects; closing could disrupt an active handle.
  @SuppressWarnings("PMD.CloseResource")
  public List<MidiInput.Port> ports() throws MidiUnavailableException {
    List<MidiInput.Port> ports = new ArrayList<>();
    MidiDevice.Info[] devices = MidiSystem.getMidiDeviceInfo();
    for (int index = 0; index < Math.min(64, devices.length); index++) {
      MidiDevice.Info info = devices[index];
      // Querying capabilities does not open the device; open transfers ownership below.
      MidiDevice device = MidiSystem.getMidiDevice(info);
      if (!(device instanceof Sequencer)
          && !(device instanceof Synthesizer)
          && device.getMaxTransmitters() != 0) ports.add(new Port(info));
    }
    return List.copyOf(ports);
  }

  private record Port(MidiDevice.Info info) implements MidiInput.Port {
    @Override
    public String key() {
      String descriptor =
          info.getName()
              + "\u0000"
              + info.getVendor()
              + "\u0000"
              + info.getDescription()
              + "\u0000"
              + info.getVersion();
      try {
        return HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(descriptor.getBytes(StandardCharsets.UTF_8)));
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("JDK SHA-256 unavailable", impossible);
      }
    }

    @Override
    public String name() {
      String name = info.getName().replaceAll("[\\p{Cntrl}§]", " ");
      return name.substring(0, Math.min(name.length(), 128));
    }

    @Override
    public MidiInput.Connection open(Consumer<MidiInput.Event> sink)
        throws MidiUnavailableException {
      MidiDevice device = MidiSystem.getMidiDevice(info);
      Transmitter transmitter = null;
      Receiver receiver =
          new Receiver() {
            private volatile boolean closed;

            @Override
            public void send(MidiMessage message, long timestamp) {
              if (!closed && message instanceof ShortMessage shortMessage) {
                int command = shortMessage.getCommand();
                int data1 = shortMessage.getData1();
                if (command == 0x80
                    || command == 0x90
                    || command == 0xb0
                        && (data1 == 64 || data1 == 120 || data1 == 121 || data1 == 123))
                  sink.accept(
                      new MidiInput.Event(
                          command, shortMessage.getChannel(), data1, shortMessage.getData2()));
              }
            }

            @Override
            public void close() {
              closed = true;
            }
          };
      try {
        device.open();
        transmitter = device.getTransmitter();
        transmitter.setReceiver(receiver);
        return new Handle(device, transmitter, receiver);
      } catch (MidiUnavailableException | RuntimeException failure) {
        receiver.close();
        try {
          if (transmitter != null) transmitter.close();
        } finally {
          device.close();
        }
        throw failure;
      }
    }
  }

  private record Handle(MidiDevice device, Transmitter transmitter, Receiver receiver)
      implements MidiInput.Connection {
    @Override
    public boolean isOpen() {
      return device.isOpen();
    }

    @Override
    public void close() {
      receiver.close();
      try {
        transmitter.close();
      } finally {
        device.close();
      }
    }
  }
}
