package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.domain.ModuleKind;
import com.malevolentgods.modularcraft.domain.ModuleState;
import com.malevolentgods.modularcraft.domain.PatchSnapshot;
import com.malevolentgods.modularcraft.domain.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SceneTest {
  @Test
  void rejectsDuplicateIdentitiesAndForeignDimensionsBeforeReplacement() {
    PatchSnapshot reference = IntegrationCoreTest.reference(true);
    Assertions.assertDoesNotThrow(() -> Scene.validate(List.of(reference), "minecraft:overworld"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> Scene.validate(List.of(reference, reference), "minecraft:overworld"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> Scene.validate(List.of(reference), "minecraft:the_nether"));
    PatchSnapshot duplicate =
        new PatchSnapshot(
            new UUID(123, 456),
            1,
            1,
            reference.sample(),
            reference.modules(),
            reference.cables(),
            reference.envelopes());
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> Scene.validate(List.of(reference, duplicate), "minecraft:overworld"));
  }

  @Test
  void aggregateWorldAndOwnerLimitsAlsoApplyToInactivePatches() {
    List<PatchSnapshot> patches = new ArrayList<>();
    for (int i = 0; i < 257; i++) {
      ModuleState module = CoreTest.module(i, new UUID(0, i / 64), ModuleKind.OSCILLATOR, i);
      patches.add(new PatchSnapshot(module.id(), 0, 0, 0, List.of(module), List.of(), Map.of()));
    }
    Assertions.assertDoesNotThrow(
        () -> Scene.validate(patches.subList(0, 256), "minecraft:overworld"));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Scene.validate(patches, "minecraft:overworld"));
    List<PatchSnapshot> owner = new ArrayList<>();
    for (int i = 0; i < 65; i++) {
      ModuleState module = CoreTest.module(i, new UUID(0, 1), ModuleKind.OSCILLATOR, i);
      owner.add(new PatchSnapshot(module.id(), 0, 0, 0, List.of(module), List.of(), Map.of()));
    }
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> Scene.validate(owner, "minecraft:overworld"));
  }
}
