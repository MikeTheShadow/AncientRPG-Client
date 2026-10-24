package dev.ancientrpg.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class CorpseWireTest {
  private static final UUID ID = new UUID(1, 2);

  @Test
  void requestsHaveAnExactBoundedLayout() {
    var r = new CorpseRequest(3, CorpseRequest.TAKE, ID, 4, 5, 6, 7);
    var bytes = CorpseWire.encodeRequest(r);
    assertEquals(
        "00000000000000030100000000000000010000000000000002000000000000000400000000000000050607",
        HexFormat.of().formatHex(bytes));
    assertEquals(r, CorpseWire.decodeRequest(bytes));
    for (int size : new int[] {0, 42, 44, 512})
      assertThrows(IllegalArgumentException.class, () -> CorpseWire.decodeRequest(new byte[size]));
    bytes[8] = 4;
    assertThrows(IllegalArgumentException.class, () -> CorpseWire.decodeRequest(bytes));
    assertThrows(IllegalArgumentException.class, () -> new CorpseRequest(0, 0, ID, 1, 0, 0, 0));
  }

  @Test
  void contentsAreRiskyAndAreNeverIncludedForDeniedAccess() {
    var entries = new ArrayList<>(CorpseWire.EMPTY);
    entries.set(0, new InventoryWire.Entry(11, 8, true));
    var s = new CorpseWire.State(3, ID, 4, 5, CorpseWire.Result.OPEN, 60000, 300000, true, entries);
    var bytes = CorpseWire.encodeState(s);
    assertEquals(230, bytes.length);
    assertEquals(s, CorpseWire.decodeState(bytes));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CorpseWire.State(3, ID, 4, 5, CorpseWire.Result.LOCKED, 1, 2, false, entries));
    bytes[54] = 2;
    assertThrows(IllegalArgumentException.class, () -> CorpseWire.decodeState(bytes));
    entries.set(0, new InventoryWire.Entry(11, 8));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CorpseWire.State(3, ID, 4, 5, CorpseWire.Result.OPEN, 1, 2, true, entries));
  }

  @Test
  void presentationRejectsTruncationTrailingDataAndMalformedText() {
    var body =
        new CorpseWire.Body(
            ID,
            new UUID(3, 4),
            "Player",
            42,
            1,
            65,
            2,
            90,
            1200,
            298800,
            Collections.nCopies(6, InventoryWire.Entry.EMPTY));
    byte[] bytes = CorpseWire.encodeBody(body);
    assertEquals(new CorpseWire.Event(ID, body), CorpseWire.decodeEvent(bytes));
    assertEquals(
        new CorpseWire.Event(ID, null), CorpseWire.decodeEvent(CorpseWire.encodeRemoval(ID)));
    for (int i = 0; i < bytes.length; i++) {
      int length = i;
      assertThrows(
          IllegalArgumentException.class,
          () -> CorpseWire.decodeEvent(Arrays.copyOf(bytes, length)));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> CorpseWire.decodeEvent(Arrays.copyOf(bytes, bytes.length + 1)));
    bytes[35] = (byte) 0xff;
    assertThrows(IllegalArgumentException.class, () -> CorpseWire.decodeEvent(bytes));
    assertThrows(IllegalArgumentException.class, () -> new InventoryWire.Entry(1, 1, true));
  }
}
