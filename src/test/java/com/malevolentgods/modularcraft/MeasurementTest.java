package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.Cable;
import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.dsp.Envelope;
import com.malevolentgods.modularcraft.dsp.Filter;
import com.malevolentgods.modularcraft.dsp.Oscillator;
import com.malevolentgods.modularcraft.dsp.Output;
import com.malevolentgods.modularcraft.dsp.RenderGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MeasurementTest {
  @Test
  void sawDcAndAliasResidualAgainstOversampledReference() {
    int count = 65536, oversample = 16;
    for (double requested : new double[] {55, 220, 880, 3520}) {
      int bin = (int) Math.round(requested * count / 48000);
      double hz = bin * 48000.0 / count;
      double[] real = new double[count], imag = new double[count];
      Oscillator saw = new Oscillator();
      for (int warmup = 0; warmup < 128; warmup++) saw.next(hz, .5);
      double sum = 0;
      for (int i = 0; i < count; i++) {
        real[i] = saw.next(hz, .5);
        sum += real[i];
      }
      Assertions.assertTrue(Math.abs(sum / count) < .001);
      fft(real, imag);
      // Independent 16x naive saw, ideal brick-wall downsampling in frequency space.
      // Only non-harmonic residual bins measure aliasing; pass-band coloration is reported
      // separately.
      double[] reference = new double[count * oversample],
          referenceImag = new double[reference.length];
      for (int i = 0; i < reference.length; i++)
        reference[i] = ((i * (double) bin / reference.length) % 1) - .5;
      fft(reference, referenceImag);
      double residual = 0, energy = 0;
      for (int k = 1; k < count / 2; k++) {
        energy +=
            (reference[k] * reference[k] + referenceImag[k] * referenceImag[k])
                / (oversample * (double) oversample);
        if (k % bin != 0) residual += real[k] * real[k] + imag[k] * imag[k];
      }
      double db = 10 * Math.log10(residual / energy);
      System.out.printf(
          java.util.Locale.ROOT, "alias %.6f Hz: %.3f dB; DC %.9f%n", hz, db, sum / count);
      Assertions.assertTrue(db < -35, "Alias residual at " + hz + " = " + db);
    }
  }

  @Test
  void sawFilterBandEnergyMeetsMu009AndExtremesAreFinite() {
    int n = 48000;
    double[] low = new double[n], high = new double[n];
    Oscillator saw = new Oscillator();
    Filter a = new Filter(), b = new Filter();
    for (int i = 0; i < n * 2; i++) {
      double x = saw.next(220, .5), l = a.next(x, 500, 0), h = b.next(x, 8000, 0);
      if (i >= n) {
        low[i - n] = l;
        high[i - n] = h;
      }
    }
    double l = 0, h = 0;
    for (int harmonic = 10; harmonic * 220 <= 8000; harmonic++) {
      l += power(low, harmonic * 220);
      h += power(high, harmonic * 220);
    }
    double db = 10 * Math.log10(l / h);
    System.out.println("MU-009 2-8 kHz attenuation dB=" + db);
    Assertions.assertTrue(db <= -12);
    for (double cutoff : new double[] {20, 20000})
      for (double resonance : new double[] {0, 95}) {
        Filter filter = new Filter();
        Output output = new Output();
        for (int i = 0; i < 48000; i++) {
          double v = output.next(filter.next(i % 2 == 0 ? 1 : -1, cutoff, resonance) * 8);
          Assertions.assertTrue(Double.isFinite(v) && Math.abs(v) <= 1);
        }
      }
  }

  static double power(double[] values, double frequency) {
    double re = 0, im = 0;
    for (int i = 0; i < values.length; i++) {
      double phase = 2 * Math.PI * frequency * i / 48000;
      re += values[i] * Math.cos(phase);
      im += values[i] * Math.sin(phase);
    }
    return re * re + im * im;
  }

  static List<PatchSnapshot> loadFixture() {
    List<PatchSnapshot> patches = new ArrayList<>();
    UUID owner = new UUID(99, 1);
    for (int patch = 0; patch < 4; patch++) {
      List<ModuleState> modules = new ArrayList<>();
      List<Cable> cables = new ArrayList<>();
      Map<UUID, Envelope.Checkpoint> checkpoints = new HashMap<>();
      for (int i = 0; i < 16; i++) {
        ModuleKind kind =
            i == 0
                ? ModuleKind.OSCILLATOR
                : i <= 10 ? ModuleKind.FILTER : i <= 13 ? ModuleKind.ENVELOPE : ModuleKind.SPEAKER;
        ModuleState m = CoreTest.module(patch * 16 + i + 1, owner, kind, 0);
        m =
            new ModuleState(
                m.id(),
                m.owner(),
                kind,
                m.dimension(),
                (patch % 2) * 8 + (i % 4) * 2,
                64,
                (patch / 2) * 8 + (i / 4) * 2,
                0,
                m.values(),
                true,
                kind == ModuleKind.SPEAKER);
        if (kind == ModuleKind.OSCILLATOR) m = m.withValue(Parameter.FREQUENCY, (patch + 1) * 110);
        if (kind == ModuleKind.FILTER)
          m = m.withValue(Parameter.RESONANCE, 25).withValue(Parameter.AMOUNT, 50);
        if (kind == ModuleKind.ENVELOPE) {
          Envelope e = new Envelope();
          e.gate(true, 0);
          checkpoints.put(m.id(), e.checkpoint(96000));
        }
        modules.add(m);
      }
      for (int i = 0; i < 10; i++)
        cables.add(
            new Cable(modules.get(i).id(), Port.AUDIO_OUT, modules.get(i + 1).id(), Port.AUDIO_IN));
      for (int i = 14; i < 16; i++)
        cables.add(
            new Cable(modules.get(10).id(), Port.AUDIO_OUT, modules.get(i).id(), Port.AUDIO_IN));
      List<Integer> destinations = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15);
      for (int i = 0; i < 12; i++) {
        int dest = destinations.get(i);
        cables.add(
            new Cable(
                modules.get(11 + i / 4).id(),
                Port.ENVELOPE_OUT,
                modules.get(dest).id(),
                dest < 14 ? Port.CUTOFF_IN : Port.GAIN_IN));
      }
      patches.add(
          new PatchSnapshot(modules.getFirst().id(), 1, 1, 96000, modules, cables, checkpoints));
    }
    return List.copyOf(patches);
  }

  @Test
  void fixedLoadDspBenchmarkAndNoSteadyAllocation() {
    benchmark(loadFixture());
  }

  @Test
  void maximumUnisonSpreadDspBenchmarkAndNoSteadyAllocation() {
    benchmark(
        loadFixture().stream()
            .map(
                p ->
                    new PatchSnapshot(
                        p.id(),
                        p.revision(),
                        p.sequence(),
                        p.sample(),
                        p.modules().stream()
                            .map(
                                m ->
                                    m.kind() == ModuleKind.OSCILLATOR
                                        ? m.withValue(Parameter.DETUNE, 100)
                                        : m)
                            .toList(),
                        p.cables(),
                        p.envelopes(),
                        p.keyboards()))
            .toList());
  }

  static void benchmark(List<PatchSnapshot> patches) {
    RenderGraph graph = new RenderGraph(patches);
    var listener = new RenderGraph.Listener("minecraft:overworld", 8, 66, 8, 0, 1);
    float[] samples = new float[256];
    for (int i = 0; i < 5000; i++) graph.process(96000 + i * 128L, listener, samples);
    long[] times = new long[10000];
    var bean =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(thread);
    for (int i = 0; i < times.length; i++) {
      long start = System.nanoTime();
      graph.process(96000 + (5000L + i) * 128L, listener, samples);
      times[i] = System.nanoTime() - start;
    }
    long allocation = bean.getThreadAllocatedBytes(thread) - before;
    Arrays.sort(times);
    double p95 = times[9500] / 1e6;
    System.out.println(
        "64 module / 96 cable / 8 speaker DSP p95 ms="
            + p95
            + ", detune="
            + patches.getFirst().modules().stream()
                .filter(m -> m.kind() == ModuleKind.OSCILLATOR)
                .findFirst()
                .orElseThrow()
                .value(Parameter.DETUNE)
            + ", measured allocation bytes="
            + allocation);
    Assertions.assertTrue(
        p95 < 1, "DSP benchmark budget exceeded; host qualification remains separate");
    Assertions.assertTrue(allocation < 4096, "Steady audio processing allocated " + allocation);
  }

  private static void fft(double[] real, double[] imag) {
    int n = real.length;
    for (int i = 1, j = 0; i < n; i++) {
      int bit = n >> 1;
      for (; (j & bit) != 0; bit >>= 1) j ^= bit;
      j ^= bit;
      if (i < j) {
        double t = real[i];
        real[i] = real[j];
        real[j] = t;
        t = imag[i];
        imag[i] = imag[j];
        imag[j] = t;
      }
    }
    for (int length = 2; length <= n; length <<= 1) {
      double a = -2 * Math.PI / length, wr = Math.cos(a), wi = Math.sin(a);
      for (int i = 0; i < n; i += length) {
        double r = 1, im = 0;
        for (int j = 0; j < length / 2; j++) {
          int u = i + j, v = u + length / 2;
          double vr = real[v] * r - imag[v] * im, vi = real[v] * im + imag[v] * r;
          real[v] = real[u] - vr;
          imag[v] = imag[u] - vi;
          real[u] += vr;
          imag[u] += vi;
          double next = r * wr - im * wi;
          im = r * wi + im * wr;
          r = next;
        }
      }
    }
  }
}
