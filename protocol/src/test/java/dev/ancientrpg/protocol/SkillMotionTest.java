package dev.ancientrpg.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillMotionTest {
  @Test
  void fiveCurvesAreMonotonicBoundedAndAnimationRestoresNeutral() {
    double[] totals = {.7, 1.1, 2.8, 1.6, 2.2};
    assertEquals(5, Skill.values().length);
    for (Skill s : Skill.values()) {
      assertEquals(0, SkillMotion.distance(s, 0));
      double last = 0;
      for (int t = 0; t <= s.duration() * 10; t++) {
        double d = SkillMotion.distance(s, t / 10.0);
        assertTrue(d >= last - 1e-9);
        last = d;
      }
      assertEquals(totals[s.ordinal()], last, 1e-9);
      assertEquals(0, SkillMotion.step(s, s.duration() + 1));
      assertEquals(SwordAnimation.REST, SwordAnimation.sample(s, 0));
      assertEquals(SwordAnimation.REST, SwordAnimation.sample(s, s.duration()));
      assertNotEquals(
          SwordAnimation.REST, SwordAnimation.sample(s, s == Skill.CRESCENT_FINISH ? 12 : 4));
    }
    assertNotEquals(
        SwordAnimation.sample(Skill.TWIN_FANG, 4), SwordAnimation.sample(Skill.TWIN_FANG, 9));
  }

  @Test
  void v3MotionAndQueuedStateRoundTripAndBoundsAreStrict() {
    var motion = new SkillWire.Motion(1, 64, 2, 1.7, 64, 2, 90, true, false);
    var action = new SkillWire.Action(1, Skill.QUICK_CUT, 10, 1, 0, 0, motion);
    var state =
        new SkillWire.State(
            3,
            SkillWire.Reason.QUEUED,
            17,
            31,
            0,
            List.of(22L, 0L, 0L, 0L, 0L),
            action,
            new SkillWire.Queued(3, Skill.TWIN_FANG));
    assertEquals(164, SkillWire.encodeState(state).length);
    assertEquals(state, SkillWire.decodeState(SkillWire.encodeState(state)));
    byte[] bytes = SkillWire.encodeState(state);
    bytes[153] = 4;
    assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeState(bytes));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SkillWire.Motion(Double.NaN, 0, 0, 0, 0, 0, 0, false, false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SkillWire.State(
                -1,
                SkillWire.Reason.OK,
                0,
                1,
                4,
                List.of(0L, 0L, 0L, 0L, 0L),
                null,
                new SkillWire.Queued(1, Skill.QUICK_CUT)));
  }
}
