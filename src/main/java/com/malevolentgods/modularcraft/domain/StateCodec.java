package com.malevolentgods.modularcraft.domain;

import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Lfo;
import com.malevolentgods.modularcraft.dsp.Sequencer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit version-two, length-delimited codecs. Unknown world records remain byte-for-byte opaque.
 * Codec operations run off the audio thread. No Java object serialization is used.
 */
public final class StateCodec {
  public static final int MAX_WORLD = 2 * 1024 * 1024, MAX_PATCH = 32768;
  private static final int MAGIC = 0x4d435301;
  private static final int OPAQUE_RECORD = 0x8000, MAX_RECORD = 4096;

  public record Stored(WorldState state, List<byte[]> quarantined) {
    public Stored {
      quarantined = quarantined.stream().map(byte[]::clone).toList();
    }

    @Override
    public List<byte[]> quarantined() {
      return quarantined.stream().map(byte[]::clone).toList();
    }
  }

  private StateCodec() {}

  @FunctionalInterface
  private interface Writer {
    void write(DataOutputStream out) throws IOException;
  }

  private static byte[] write(int limit, Writer writer) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      writer.write(out);
      out.flush();
      if (bytes.size() > limit) throw new IllegalArgumentException("encoded_size");
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("encode", e);
    }
  }

  private static DataInputStream input(byte[] bytes, int limit) {
    if (bytes.length > limit) throw new IllegalArgumentException("payload_size");
    return new DataInputStream(new ByteArrayInputStream(bytes));
  }

  private static int count(DataInputStream in, int max) throws IOException {
    int n = in.readUnsignedShort();
    if (n > max) throw new IllegalArgumentException("count");
    return n;
  }

  private static void end(DataInputStream in) throws IOException {
    if (in.available() != 0) throw new IllegalArgumentException("trailing_data");
  }

  private static void uuid(DataOutputStream out, UUID id) throws IOException {
    out.writeLong(id.getMostSignificantBits());
    out.writeLong(id.getLeastSignificantBits());
  }

  private static UUID uuid(DataInputStream in) throws IOException {
    return new UUID(in.readLong(), in.readLong());
  }

  private static void module(DataOutputStream out, ModuleState m, boolean runtime)
      throws IOException {
    uuid(out, m.id());
    uuid(out, m.owner());
    out.writeUTF(m.kind().name());
    out.writeUTF(m.dimension());
    out.writeInt(m.x());
    out.writeInt(m.y());
    out.writeInt(m.z());
    out.writeLong(m.revision());
    out.writeByte(m.kind().parameters.size());
    for (Parameter p : m.kind().parameters) {
      out.writeUTF(p.name());
      out.writeDouble(m.value(p));
    }
    out.writeBoolean(runtime && m.loaded());
    out.writeBoolean(runtime && m.enabled());
  }

  private static ModuleState module(DataInputStream in, boolean stored, int schema)
      throws IOException {
    UUID id = uuid(in), owner = uuid(in);
    ModuleKind kind = ModuleKind.valueOf(in.readUTF());
    boolean legacySpeaker = stored && schema == 1 && kind == ModuleKind.SPEAKER;
    if (legacySpeaker) kind = ModuleKind.AMP;
    String dim = in.readUTF();
    int x = in.readInt(), y = in.readInt(), z = in.readInt();
    long revision = in.readLong();
    int n = in.readUnsignedByte();
    if (n > kind.parameters.size()) throw new IllegalArgumentException("parameter_count");
    Map<Parameter, Double> values = new EnumMap<>(Parameter.class);
    for (int i = 0; i < n; i++)
      if (values.put(Parameter.valueOf(in.readUTF()), in.readDouble()) != null)
        throw new IllegalArgumentException("duplicate_parameter");
    if (legacySpeaker) {
      if (!values.keySet().equals(java.util.Set.of(Parameter.VOLUME)))
        throw new IllegalArgumentException("legacy_speaker_parameters");
      double level = Parameter.VOLUME.validate(values.remove(Parameter.VOLUME));
      values.putAll(ModuleKind.AMP.defaults());
      values.put(Parameter.AMP_LEVEL, level);
      values.put(Parameter.DRONE, 1.0);
    }
    // Migrate only exact legacy stored parameter sets. Network v10 must be complete.
    if (stored && kind == ModuleKind.EUCLIDEAN_SEQUENCER) {
      var legacy = java.util.EnumSet.copyOf(kind.parameters);
      legacy.remove(Parameter.REDSTONE_MODE);
      if (values.keySet().equals(legacy)) values.put(Parameter.REDSTONE_MODE, 0.0);
    }
    if (stored
        && kind == ModuleKind.OSCILLATOR
        && values.keySet().equals(java.util.Set.of(Parameter.FREQUENCY, Parameter.LEVEL)))
      values.put(Parameter.DETUNE, 0.0);
    if (stored
        && ((kind == ModuleKind.FILTER
                && values
                    .keySet()
                    .equals(
                        java.util.Set.of(Parameter.CUTOFF, Parameter.RESONANCE, Parameter.AMOUNT)))
            || (kind == ModuleKind.ENVELOPE
                && values
                    .keySet()
                    .equals(
                        java.util.Set.of(
                            Parameter.ATTACK,
                            Parameter.DECAY,
                            Parameter.SUSTAIN,
                            Parameter.RELEASE)))))
      for (Parameter parameter : kind.parameters) values.putIfAbsent(parameter, parameter.initial);
    return new ModuleState(
        id, owner, kind, dim, x, y, z, revision, values, in.readBoolean(), in.readBoolean());
  }

  private static void cable(DataOutputStream out, Cable c) throws IOException {
    uuid(out, c.source());
    out.writeUTF(c.output().name());
    uuid(out, c.destination());
    out.writeUTF(c.input().name());
  }

  private static Cable cable(DataInputStream in) throws IOException {
    return new Cable(uuid(in), Port.valueOf(in.readUTF()), uuid(in), Port.valueOf(in.readUTF()));
  }

  public static byte[] moduleBytes(ModuleState module) {
    return write(
        4096,
        out -> {
          out.writeInt(MAGIC);
          out.writeShort(2);
          module(out, module, false);
        });
  }

  public static ModuleState moduleBytes(byte[] bytes) {
    int schema = bytes.length >= 6 && java.nio.ByteBuffer.wrap(bytes).getInt() == MAGIC ? 2 : 1;
    return moduleBytes(bytes, schema);
  }

  public static ModuleState moduleBytes(byte[] bytes, int schema) {
    if (schema != 1 && schema != 2) throw new IllegalArgumentException("schema");
    try (DataInputStream in = input(bytes, 4096)) {
      if (schema == 2 && (in.readInt() != MAGIC || in.readUnsignedShort() != 2))
        throw new IllegalArgumentException("schema");
      ModuleState m = module(in, true, schema);
      end(in);
      return m;
    } catch (IOException e) {
      throw new IllegalArgumentException("module_bytes", e);
    }
  }

  public static byte[] save(WorldState state, List<byte[]> quarantined) {
    List<byte[]> records = new ArrayList<>();
    for (ModuleState m : state.modules())
      records.add(
          write(
              4096,
              out -> {
                out.writeByte(1);
                module(out, m, false);
              }));
    for (Cable c : state.cables())
      records.add(
          write(
              4096,
              out -> {
                out.writeByte(2);
                cable(out, c);
              }));
    // Mark quarantine in the unused high bit of its length prefix. This preserves both the
    // original bytes/identity and the existing record/world size limits, without nesting.
    int liveRecords = records.size();
    records.addAll(quarantined);
    if (records.size() > 1024) throw new IllegalArgumentException("record_count");
    return write(
        MAX_WORLD,
        out -> {
          out.writeInt(MAGIC);
          out.writeShort(2);
          out.writeShort(records.size());
          for (int i = 0; i < records.size(); i++) {
            byte[] record = records.get(i);
            if (record.length > MAX_RECORD) throw new IllegalArgumentException("record_size");
            out.writeShort(record.length | (i >= liveRecords ? OPAQUE_RECORD : 0));
            out.write(record);
          }
        });
  }

  public static Stored load(byte[] bytes) {
    List<ModuleState> modules = new ArrayList<>();
    List<Cable> links = new ArrayList<>();
    List<byte[]> bad = new ArrayList<>();
    Map<UUID, byte[]> rawModules = new HashMap<>();
    Map<Cable, byte[]> rawCables = new HashMap<>();
    try (DataInputStream in = input(bytes, MAX_WORLD)) {
      if (in.readInt() != MAGIC) throw new IllegalArgumentException("unsupported_world_schema");
      int schema = in.readUnsignedShort();
      if (schema != 1 && schema != 2)
        throw new IllegalArgumentException("unsupported_world_schema");
      int n = count(in, 1024);
      for (int i = 0; i < n; i++) {
        int framedLength = in.readUnsignedShort();
        boolean opaque = schema == 2 && (framedLength & OPAQUE_RECORD) != 0;
        int length = opaque ? framedLength & ~OPAQUE_RECORD : framedLength;
        if (length > MAX_RECORD) throw new IllegalArgumentException("record_size");
        byte[] raw = in.readNBytes(length);
        if (raw.length != length) throw new IllegalArgumentException("truncated_record");
        if (opaque) {
          bad.add(raw);
          continue;
        }
        try (DataInputStream record = input(raw, MAX_RECORD)) {
          int type = record.readUnsignedByte();
          if (type == 1) {
            ModuleState m = module(record, true, schema);
            end(record);
            if (rawModules.containsKey(m.id()))
              throw new IllegalArgumentException("duplicate_identity");
            modules.add(m);
            rawModules.put(m.id(), raw);
          } else if (type == 2) {
            Cable c = cable(record);
            end(record);
            if (rawCables.containsKey(c)) throw new IllegalArgumentException("duplicate_cable");
            links.add(c);
            rawCables.put(c, raw);
          } else bad.add(raw);
        } catch (IOException | IllegalArgumentException invalid) {
          bad.add(raw);
        }
      }
      end(in);
    } catch (IOException e) {
      throw new IllegalArgumentException("world_bytes", e);
    }
    // Quarantine an entire connected component on a missing/invalid endpoint or graph rule.
    List<ModuleState> valid = new ArrayList<>();
    List<Cable> validLinks = new ArrayList<>();
    java.util.Set<UUID> seen = new java.util.HashSet<>();
    for (ModuleState root : modules)
      if (!seen.contains(root.id())) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        ids.add(root.id());
        boolean changed;
        do {
          changed = false;
          for (Cable c : links)
            if (ids.contains(c.source()) || ids.contains(c.destination())) {
              changed |= ids.add(c.source());
              changed |= ids.add(c.destination());
            }
        } while (changed);
        seen.addAll(ids);
        List<ModuleState> part = modules.stream().filter(m -> ids.contains(m.id())).toList();
        List<Cable> edges = links.stream().filter(c -> ids.contains(c.source())).toList();
        try {
          Map<UUID, ModuleState> map = new LinkedHashMap<>();
          part.forEach(m -> map.put(m.id(), m.runtime(false, false)));
          WorldState.validate(map, edges);
          valid.addAll(part);
          validLinks.addAll(edges);
        } catch (IllegalArgumentException invalid) {
          part.forEach(m -> bad.add(rawModules.get(m.id())));
          edges.forEach(c -> bad.add(rawCables.get(c)));
        }
      }
    for (Cable c : links) if (!seen.contains(c.source())) bad.add(rawCables.get(c));
    WorldState state = new WorldState();
    state.restore(valid, validLinks);
    return new Stored(state, bad);
  }

  public static byte[] encode(PatchSnapshot p) {
    return write(
        MAX_PATCH,
        out -> {
          out.writeInt(MAGIC);
          out.writeShort(10);
          uuid(out, p.id());
          out.writeLong(p.revision());
          out.writeLong(p.sequence());
          out.writeLong(p.sample());
          out.writeShort(p.modules().size());
          for (ModuleState m : p.modules()) module(out, m, true);
          out.writeShort(p.cables().size());
          for (Cable c : p.cables()) cable(out, c);
          out.writeShort(p.envelopes().size());
          for (var entry :
              p.envelopes().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            uuid(out, entry.getKey());
            var c = entry.getValue();
            out.writeUTF(c.stage().name());
            out.writeLong(c.start());
            out.writeDouble(c.from());
            out.writeDouble(c.target());
            out.writeLong(c.duration());
            out.writeBoolean(c.gate());
            out.writeDouble(c.attack());
            out.writeDouble(c.decay());
            out.writeDouble(c.sustain());
            out.writeDouble(c.release());
          }
          out.writeShort(p.keyboards().size());
          for (var entry :
              p.keyboards().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            uuid(out, entry.getKey());
            out.writeByte(entry.getValue().note());
            out.writeBoolean(entry.getValue().gate());
          }
          out.writeShort(p.sequencers().size());
          for (var entry :
              p.sequencers().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            uuid(out, entry.getKey());
            sequencer(out, entry.getValue());
          }
          out.writeShort(p.lfos().size());
          for (var entry :
              p.lfos().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            uuid(out, entry.getKey());
            var c = entry.getValue();
            out.writeLong(c.anchor());
            out.writeDouble(c.phase());
            out.writeDouble(c.fromRate());
            out.writeDouble(c.rate());
            out.writeBoolean(c.external());
            out.writeBoolean(c.high());
            out.writeLong(c.lastClock());
            out.writeLong(c.interval());
            out.writeByte(c.pulse());
            out.writeLong(c.transition());
            out.writeDouble(c.fromValue());
          }
          out.writeShort(p.baseGates().size());
          for (var entry :
              p.baseGates().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            uuid(out, entry.getKey());
            out.writeBoolean(entry.getValue());
          }
        });
  }

  private static void sequencer(DataOutputStream out, Sequencer.Checkpoint state)
      throws IOException {
    out.writeShort(state.active().tempo());
    for (var channel : state.active().channels()) {
      out.writeByte(channel.steps());
      out.writeByte(channel.hits());
      out.writeByte(channel.rotation());
      out.writeBoolean(channel.muted());
    }
    out.writeBoolean(state.running());
    out.writeBoolean(state.external());
    out.writeBoolean(state.clockHigh());
    out.writeBoolean(state.resetHigh());
    out.writeLong(state.nextClock());
    out.writeInt(state.remainder());
    out.writeLong(state.lastClock());
    out.writeLong(state.clockEnd());
    out.writeInt(state.rejectedClocks());
    for (var channel : state.channels()) {
      out.writeByte(channel.next());
      out.writeByte(channel.step());
      out.writeLong(channel.end());
    }
  }

  private static Sequencer.Checkpoint sequencer(DataInputStream in, long sample)
      throws IOException {
    int tempo = in.readUnsignedShort();
    List<SequencerSettings.Channel> channels = new ArrayList<>();
    for (int i = 0; i < 4; i++)
      channels.add(
          new SequencerSettings.Channel(
              in.readUnsignedByte(),
              in.readUnsignedByte(),
              in.readUnsignedByte(),
              in.readBoolean()));
    SequencerSettings active = new SequencerSettings(tempo, channels);
    boolean running = in.readBoolean(),
        external = in.readBoolean(),
        clock = in.readBoolean(),
        reset = in.readBoolean();
    long nextClock = in.readLong();
    int remainder = in.readInt();
    long lastClock = in.readLong(), clockEnd = in.readLong();
    int rejected = in.readInt();
    List<Sequencer.ChannelState> positions = new ArrayList<>();
    for (int i = 0; i < 4; i++)
      positions.add(
          new Sequencer.ChannelState(in.readUnsignedByte(), in.readByte(), in.readLong()));
    return new Sequencer.Checkpoint(
        sample, active, running, external, clock, reset, nextClock, remainder, lastClock, clockEnd,
        rejected, positions);
  }

  public static PatchSnapshot decode(byte[] bytes) {
    try (DataInputStream in = input(bytes, MAX_PATCH)) {
      if (in.readInt() != MAGIC || in.readUnsignedShort() != 10)
        throw new IllegalArgumentException("protocol");
      UUID id = uuid(in);
      long revision = in.readLong(), sequence = in.readLong(), sample = in.readLong();
      List<ModuleState> modules = new ArrayList<>();
      int n = count(in, 16);
      for (int i = 0; i < n; i++) modules.add(module(in, false, 2));
      List<Cable> cables = new ArrayList<>();
      n = count(in, 24);
      for (int i = 0; i < n; i++) cables.add(cable(in));
      Map<UUID, Envelope.Checkpoint> envelopes = new HashMap<>();
      n = count(in, 16);
      for (int i = 0; i < n; i++) {
        UUID module = uuid(in);
        var c =
            new Envelope.Checkpoint(
                Envelope.Stage.valueOf(in.readUTF()),
                in.readLong(),
                in.readDouble(),
                in.readDouble(),
                in.readLong(),
                in.readBoolean(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble());
        if (envelopes.put(module, c) != null)
          throw new IllegalArgumentException("duplicate_envelope");
      }
      Map<UUID, KeyboardState> keyboards = new HashMap<>();
      n = count(in, 16);
      for (int i = 0; i < n; i++)
        if (keyboards.put(uuid(in), new KeyboardState(in.readUnsignedByte(), in.readBoolean()))
            != null) throw new IllegalArgumentException("duplicate_keyboard");
      Map<UUID, Sequencer.Checkpoint> sequencers = new HashMap<>();
      n = count(in, 16);
      for (int i = 0; i < n; i++)
        if (sequencers.put(uuid(in), sequencer(in, sample)) != null)
          throw new IllegalArgumentException("duplicate_sequencer");
      Map<UUID, Lfo.Checkpoint> lfos = new HashMap<>();
      n = count(in, 16);
      for (int i = 0; i < n; i++) {
        UUID module = uuid(in);
        Lfo.Checkpoint c =
            new Lfo.Checkpoint(
                sample,
                in.readLong(),
                in.readDouble(),
                in.readDouble(),
                in.readDouble(),
                in.readBoolean(),
                in.readBoolean(),
                in.readLong(),
                in.readLong(),
                in.readUnsignedByte(),
                in.readLong(),
                in.readDouble());
        if (lfos.put(module, c) != null) throw new IllegalArgumentException("duplicate_lfo");
      }
      Map<UUID, Boolean> baseGates = new HashMap<>();
      n = count(in, 16);
      for (int i = 0; i < n; i++)
        if (baseGates.put(uuid(in), in.readBoolean()) != null)
          throw new IllegalArgumentException("duplicate_gate");
      end(in);
      return new PatchSnapshot(
          id,
          revision,
          sequence,
          sample,
          modules,
          cables,
          envelopes,
          keyboards,
          sequencers,
          baseGates,
          lfos);
    } catch (IOException e) {
      throw new IllegalArgumentException("snapshot_bytes", e);
    }
  }
}
