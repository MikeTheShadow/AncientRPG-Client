package dev.ancientrpg.client;

import static org.junit.jupiter.api.Assertions.*;

import dev.ancientrpg.protocol.InventoryCatalog;
import dev.ancientrpg.protocol.InventoryCatalog.Slot;
import dev.ancientrpg.protocol.InventoryWire;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class InventoryGestureTest {
  @Test
  void clickInspectsWithoutMovingAndSmallPointerJitterDoesNotDrag() {
    var gesture = new InventoryGesture();
    var state = state(4, 2);
    assertTrue(gesture.begin(state, 2, 10, 10, false));
    gesture.movePointer(12, 11);
    assertFalse(gesture.carrying());
    assertNull(gesture.release(state, Slot.MAINHAND.index(), true));
    assertFalse(gesture.active());
  }

  @Test
  void completedDragRequestsOneMoveWithoutChangingTheConfirmedSnapshot() {
    var gesture = new InventoryGesture();
    var state = state(4, 17);
    assertTrue(gesture.begin(state, 17, 10, 10, false));
    gesture.movePointer(20, 20);
    assertTrue(gesture.carrying());
    assertEquals(
        new InventoryGesture.Move(17, Slot.MAINHAND.index()),
        gesture.release(state, Slot.MAINHAND.index(), true));
    assertEquals(1, state.entries().get(17).item());
    assertTrue(state.entries().get(Slot.MAINHAND.index()).empty());
    assertNull(gesture.release(state, Slot.MAINHAND.index(), true));
  }

  @Test
  void keyboardCanPickUpEquipmentAndPlaceItInAnOccupiedBagSlotForServerValidation() {
    var gesture = new InventoryGesture();
    var state = state(4, Slot.MAINHAND.index());
    assertTrue(gesture.begin(state, Slot.MAINHAND.index(), 0, 0, true));
    assertTrue(gesture.carrying());
    assertTrue(gesture.keyboard());
    // A non-empty destination is still a request for the server's existing atomic swap rules.
    assertEquals(
        new InventoryGesture.Move(Slot.MAINHAND.index(), 8), gesture.release(state, 8, true));
  }

  @Test
  void outsideSourceAndIncompatibleDropsCancelWithoutRequests() {
    for (int target : new int[] {-1, 48, 2, Slot.HEAD.index(), Slot.BANDAGE.index()}) {
      var gesture = new InventoryGesture();
      var state = state(4, 2);
      gesture.begin(state, 2, 0, 0, true);
      assertNull(gesture.release(state, target, true));
      assertFalse(gesture.active());
    }
  }

  @Test
  void changedRevisionOrPendingActionInvalidatesGesture() {
    var gesture = new InventoryGesture();
    gesture.begin(state(4, 2), 2, 0, 0, true);
    assertNull(gesture.release(state(5, 2), 3, true));
    gesture.begin(state(4, 2), 2, 0, 0, true);
    assertNull(gesture.release(state(4, 2), 3, false));
    gesture.begin(state(4, 2), 2, 0, 0, true);
    assertNull(gesture.release(state(4, 3), 3, true));
  }

  @Test
  void explicitCancelAndEmptyOrMissingSourcesCannotMoveAnything() {
    var gesture = new InventoryGesture();
    var state = state(4, 2);
    assertFalse(gesture.begin(null, 2, 0, 0, true));
    assertFalse(gesture.begin(state, -1, 0, 0, true));
    assertFalse(gesture.begin(state, 48, 0, 0, true));
    assertFalse(gesture.begin(state, 3, 0, 0, true));
    gesture.begin(state, 2, 0, 0, true);
    gesture.cancel();
    assertNull(gesture.release(state, 3, true));
  }

  @Test
  void offhandHighlightRespectsConfirmedMainhandLock() {
    var gesture = new InventoryGesture();
    var initial = state(4, 2);
    var entries = new ArrayList<>(initial.entries());
    int lockingWeapon =
        InventoryCatalog.ITEMS.stream()
            .filter(item -> item.locksOffhand())
            .findFirst()
            .orElseThrow()
            .id();
    entries.set(Slot.MAINHAND.index(), new InventoryWire.Entry(lockingWeapon, 1));
    var state = new InventoryWire.State(-1, 4, InventoryWire.Result.SYNC, 100, 100, entries);
    gesture.begin(state, 2, 0, 0, true);
    assertFalse(gesture.canDrop(state, Slot.OFFHAND.index(), true));
    assertNull(gesture.release(state, Slot.OFFHAND.index(), true));
  }

  private static InventoryWire.State state(long revision, int swordPosition) {
    var entries =
        new ArrayList<>(
            Collections.nCopies(InventoryCatalog.SLOT_COUNT, InventoryWire.Entry.EMPTY));
    entries.set(swordPosition, new InventoryWire.Entry(1, 1));
    entries.set(8, new InventoryWire.Entry(2, 1));
    return new InventoryWire.State(-1, revision, InventoryWire.Result.SYNC, 100, 100, entries);
  }
}
