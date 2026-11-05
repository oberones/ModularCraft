package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.Parameter;
import com.malevolentgods.modularcraft.domain.Port;
import com.malevolentgods.modularcraft.dsp.Filter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LfoContractTest {
  @Test
  void instrumentAndModulationPortsAreAvailable() {
    ModuleKind lfo = ModuleKind.valueOf("LFO");
    Assertions.assertTrue(lfo.ports.contains(Port.CLOCK_IN));
    Assertions.assertEquals(.01, Parameter.valueOf("LFO_FREQUENCY").min);
    Assertions.assertEquals(1, lfo.defaults().get(Parameter.valueOf("LFO_FREQUENCY")));
    Assertions.assertTrue(ModuleKind.FILTER.ports.contains(Port.valueOf("RESONANCE_IN")));
    for (String name : new String[] {"ATTACK", "DECAY", "SUSTAIN", "RELEASE"}) {
      Assertions.assertTrue(ModuleKind.ENVELOPE.ports.contains(Port.valueOf(name + "_IN")));
      Assertions.assertEquals(
          0, ModuleKind.ENVELOPE.defaults().get(Parameter.valueOf(name + "_DEPTH")));
    }
  }

  @Test
  void signedCutoffUsesAvailableRangeAndPreservesLegacySweep() {
    Assertions.assertEquals(10250, Filter.cutoff(500, 1, .5));
    Assertions.assertEquals(2510, Filter.cutoff(5000, -1, .5));
    Assertions.assertEquals(20, Filter.cutoff(5000, -1, 1));
  }
}
