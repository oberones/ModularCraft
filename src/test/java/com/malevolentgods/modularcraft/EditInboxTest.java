package com.malevolentgods.modularcraft;

import com.malevolentgods.modularcraft.transport.EditInbox;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EditInboxTest {
  @Test
  void keyReleaseSupersedesOlderPendingPressButAllowsNewPress() {
    EditInbox<String> inbox = new EditInbox<>();
    UUID player = new UUID(0, 1);
    Assertions.assertTrue(inbox.offer(player, "", true, false, "old_press"));
    Assertions.assertTrue(inbox.offer(player, "", false, true, "release"));
    Assertions.assertTrue(inbox.offer(player, "", true, false, "new_press"));
    Assertions.assertEquals("release", inbox.poll());
    Assertions.assertEquals("new_press", inbox.poll());
    Assertions.assertNull(inbox.poll());
  }

  @Test
  void parameterUpdatesCoalesceAndCleanupBypassesFullOrdinaryQueue() {
    EditInbox<Integer> inbox = new EditInbox<>();
    UUID player = new UUID(0, 1);
    for (int i = 0; i < 100; i++)
      Assertions.assertTrue(inbox.offer(player, "frequency", false, false, i));
    Assertions.assertEquals(1, inbox.size());
    Assertions.assertEquals(99, inbox.poll());
    for (int i = 0; i < 64; i++) Assertions.assertTrue(inbox.offer(player, "", false, false, i));
    Assertions.assertFalse(inbox.offer(player, "", false, false, 65));
    Assertions.assertTrue(inbox.offer(player, "", false, true, -1));
    Assertions.assertEquals(-1, inbox.poll());
  }
}
