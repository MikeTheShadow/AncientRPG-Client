package dev.ancientrpg.client;

import dev.ancientrpg.protocol.InventoryCatalog;
import dev.ancientrpg.protocol.InventoryWire;

/** A presentation-only gesture bound to one confirmed inventory revision. */
final class InventoryGesture {
  record Move(int source, int target) {}

  private int source = -1;
  private long revision;
  private InventoryWire.Entry carried = InventoryWire.Entry.EMPTY;
  private double pressX, pressY;
  private boolean dragging, keyboard;

  int source() {
    return source;
  }

  boolean active() {
    return source >= 0;
  }

  boolean carrying() {
    return active() && (dragging || keyboard);
  }

  boolean keyboard() {
    return keyboard;
  }

  InventoryWire.Entry carried() {
    return carried;
  }

  boolean begin(InventoryWire.State state, int index, double x, double y, boolean keyboard) {
    cancel();
    if (state == null || index < 0 || index >= InventoryCatalog.SLOT_COUNT) return false;
    var entry = state.entries().get(index);
    if (entry.empty()) return false;
    source = index;
    revision = state.revision();
    carried = entry;
    pressX = x;
    pressY = y;
    this.keyboard = keyboard;
    return true;
  }

  void movePointer(double x, double y) {
    if (active() && Math.hypot(x - pressX, y - pressY) >= 4) dragging = true;
  }

  boolean valid(InventoryWire.State state, boolean ready) {
    return active()
        && ready
        && state != null
        && state.revision() == revision
        && state.entries().get(source).equals(carried);
  }

  boolean canDrop(InventoryWire.State state, int target, boolean ready) {
    if (!valid(state, ready) || target == source || !InventoryCatalog.fits(carried.item(), target))
      return false;
    if (target == InventoryCatalog.Slot.OFFHAND.index()) {
      var main = state.entries().get(InventoryCatalog.Slot.MAINHAND.index());
      if (!main.empty() && InventoryCatalog.item(main.item()).locksOffhand()) return false;
    }
    return true;
  }

  Move release(InventoryWire.State state, int target, boolean ready) {
    Move result = carrying() && canDrop(state, target, ready) ? new Move(source, target) : null;
    cancel();
    return result;
  }

  void cancel() {
    source = -1;
    carried = InventoryWire.Entry.EMPTY;
    dragging = keyboard = false;
  }
}
