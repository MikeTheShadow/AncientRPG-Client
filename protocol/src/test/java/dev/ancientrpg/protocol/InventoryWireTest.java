package dev.ancientrpg.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class InventoryWireTest {
  @Test
  void requestHasExactGoldenLayoutAndStrictBounds() {
    var request = new InventoryRequest(1, 2, false, 7, 45);
    assertEquals(
        "0000000000000001000000000000000201072d",
        HexFormat.of().formatHex(InventoryWire.encodeRequest(request)));
    assertEquals(request, InventoryWire.decodeRequest(InventoryWire.encodeRequest(request)));
    for (int size : new int[] {0, 18, 20, 1000})
      assertThrows(
          IllegalArgumentException.class, () -> InventoryWire.decodeRequest(new byte[size]));
    var bytes = InventoryWire.encodeRequest(request);
    bytes[16] = 2;
    assertThrows(IllegalArgumentException.class, () -> InventoryWire.decodeRequest(bytes));
    assertThrows(IllegalArgumentException.class, () -> new InventoryRequest(0, 0, true, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new InventoryRequest(0, 0, false, 48, 0));
  }

  @Test
  void snapshotRejectsInvalidItemsHealthCountsAndHandConflicts() {
    var entries = new ArrayList<>(Collections.nCopies(48, InventoryWire.Entry.EMPTY));
    entries.set(0, new InventoryWire.Entry(8, 1));
    var state = new InventoryWire.State(3, 4, InventoryWire.Result.APPLIED, 15, 20, entries);
    byte[] bytes = InventoryWire.encodeState(state);
    assertEquals(217, bytes.length);
    assertEquals(state, InventoryWire.decodeState(bytes));
    assertThrows(
        IllegalArgumentException.class, () -> InventoryWire.decodeState(Arrays.copyOf(bytes, 218)));
    assertThrows(IllegalArgumentException.class, () -> new InventoryWire.Entry(8, 2));
    assertThrows(IllegalArgumentException.class, () -> new InventoryWire.Entry(11, 1));
    assertThrows(IllegalArgumentException.class, () -> new InventoryWire.Entry(0, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InventoryWire.State(-1, 0, InventoryWire.Result.SYNC, Float.NaN, 20, entries));
    entries.set(InventoryCatalog.Slot.MAINHAND.index(), new InventoryWire.Entry(8, 1));
    entries.set(InventoryCatalog.Slot.OFFHAND.index(), new InventoryWire.Entry(1, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new InventoryWire.State(-1, 0, InventoryWire.Result.SYNC, 20, 20, entries));
  }

  @Test
  void allNewRuntimeIdsParticipateInCompatibility() {
    int[] ids = {1539, 1540, 1541, 1542, 1543, 1544, 1545, 1546};
    var original = PublicContent.identity(776, 1196, 32366, 32367, 1537, 1538, ids);
    for (int i = 0; i < ids.length; i++) {
      int[] changed = ids.clone();
      changed[i]++;
      assertNotEquals(
          original.manifest(),
          PublicContent.identity(776, 1196, 32366, 32367, 1537, 1538, changed).manifest());
    }
  }
}
