package dev.ancientrpg.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SkillWireTest {
  @Test
  void goldenRequestAndSnapshot() {
    var r = new SkillRequest(1, Skill.CRESCENT_FINISH, false);
    assertEquals("000000000000000104", HexFormat.of().formatHex(SkillWire.encodeRequest(r)));
    assertEquals(r, SkillWire.decodeRequest(SkillWire.encodeRequest(r), false));
    var state =
        new SkillWire.State(-1, SkillWire.Reason.OK, 0, 1, 4, List.of(0L, 0L, 0L, 0L, 0L), null);
    assertEquals(
        "ffffffffffffffff00" + "00".repeat(8) + "0104" + "00".repeat(41),
        HexFormat.of().formatHex(SkillWire.encodeState(state)));
    assertEquals(state, SkillWire.decodeState(SkillWire.encodeState(state)));
  }

  @Test
  void activeStateAndEveryEventRoundTrip() {
    var a = new SkillWire.Action(7, Skill.TWIN_FANG, 12, 0, 0, 1);
    var state =
        new SkillWire.State(9, SkillWire.Reason.OK, 14, 31, 0, List.of(1L, 2L, 3L, 4L, 5L), a);
    assertEquals(101, SkillWire.encodeState(state).length);
    assertEquals(state, SkillWire.decodeState(SkillWire.encodeState(state)));
    for (var phase : SkillWire.Phase.values()) {
      var e =
          new SkillWire.Event(
              14,
              2,
              phase,
              a,
              phase == SkillWire.Phase.HIT ? List.of(new SkillWire.Hit(3, 7, 93)) : List.of());
      assertEquals(e, SkillWire.decodeEvent(SkillWire.encodeEvent(e)));
    }
  }

  @Test
  void malformedLengthsIdsEnumsAndNumericValuesAreRejected() {
    byte[] request = SkillWire.encodeRequest(new SkillRequest(0, Skill.QUICK_CUT, false));
    for (int n : new int[] {0, 8, 10, 1025})
      assertThrows(
          IllegalArgumentException.class,
          () -> SkillWire.decodeRequest(Arrays.copyOf(request, n), false));
    request[8] = 5;
    assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeRequest(request, false));
    request[8] = 0;
    request[0] = (byte) 255;
    assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeRequest(request, false));
    var a = new SkillWire.Action(0, Skill.QUICK_CUT, 0, 0, 0, 1);
    byte[] event =
        SkillWire.encodeEvent(new SkillWire.Event(0, 0, SkillWire.Phase.START, a, List.of()));
    for (int n = 0; n < event.length; n++) {
      byte[] truncated = Arrays.copyOf(event, n);
      assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeEvent(truncated));
    }
    byte[] trailing = Arrays.copyOf(event, event.length + 1);
    assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeEvent(trailing));
    event[12] = (byte) 255;
    assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeEvent(event));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SkillWire.Action(0, Skill.QUICK_CUT, 0, Double.NaN, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new SkillWire.Hit(0, 1000, 100));
    var s =
        new SkillWire.State(-1, SkillWire.Reason.OK, 0, 1, 4, List.of(0L, 0L, 0L, 0L, 0L), null);
    byte[] state = SkillWire.encodeState(s);
    state[59] = 2;
    assertThrows(IllegalArgumentException.class, () -> SkillWire.decodeState(state));
  }
}
