package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SkillInputEdgeTest {
  @Test
  void holdingAndScreenTransitionsNeverRepeatOrLeakInput() {
    var edge = new SkillInputEdge();
    assertTrue(edge.update(true, true));
    assertFalse(edge.update(true, true));
    assertFalse(edge.update(false, true));
    assertFalse(edge.update(true, true));
    assertFalse(edge.update(false, false));
    assertFalse(edge.update(false, true));
    assertFalse(edge.update(true, true));
    assertFalse(edge.update(true, false));
    assertTrue(edge.update(true, true));
  }
}
