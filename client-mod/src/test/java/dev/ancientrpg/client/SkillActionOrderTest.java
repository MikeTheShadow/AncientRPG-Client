package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkillActionOrderTest {
  @Test
  void terminalSnapshotsCannotResurrectLocksOrRemoveSuccessors() {
    var order = new SkillActionOrder();
    assertTrue(order.accepts(1, 0));
    order.finish(1, 0);
    assertFalse(order.accepts(1, 0));
    assertTrue(order.accepts(1, 1));
    order.finish(1, 2);
    order.finish(1, 0);
    assertFalse(order.accepts(1, 1));
    assertTrue(order.accepts(2, 0));
    order.clear();
    assertTrue(order.accepts(1, 0));
  }
}
