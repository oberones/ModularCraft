package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.transport.Budgets;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TrafficTest {
  @Test
  void recoveryCannotExceedListenerOrServerTrafficCaps() {
    Budgets b = new Budgets();
    UUID first = new UUID(0, 0);
    Assertions.assertTrue(b.bytes(first, 0, 131072));
    Assertions.assertFalse(b.recovery(first, 0));
    for (int i = 1; i < 16; i++) Assertions.assertTrue(b.bytes(new UUID(0, i), 0, 131072));
    Assertions.assertFalse(b.recovery(new UUID(0, 17), 0));
    Assertions.assertTrue(b.recovery(first, 1_000_000_000L));
    Assertions.assertFalse(b.bytes(first, 1_000_000_000L, 131072));
  }
}
