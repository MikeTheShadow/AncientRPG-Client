package dev.ancientrpg.protocol;

public record InventoryRequest(long sequence, long revision, boolean sync, int source, int target)
    implements ActionIntent {
  public InventoryRequest {
    if (sequence < 0
        || revision < 0
        || source < 0
        || source >= InventoryCatalog.SLOT_COUNT
        || target < 0
        || target >= InventoryCatalog.SLOT_COUNT
        || (sync && (source != 0 || target != 0)))
      throw new IllegalArgumentException("Invalid inventory request");
  }
}
