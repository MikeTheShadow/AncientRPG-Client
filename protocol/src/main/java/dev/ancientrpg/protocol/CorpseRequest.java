package dev.ancientrpg.protocol;

import java.util.UUID;

public record CorpseRequest(
    long sequence,
    int operation,
    UUID corpse,
    long revision,
    long inventoryRevision,
    int slot,
    int target)
    implements ActionIntent {
  public static final int OPEN = 0, TAKE = 1, CLOSE = 2, TAKE_ALL = 3;

  public CorpseRequest {
    if (sequence < 0
        || operation < 0
        || operation > 3
        || corpse == null
        || revision < 0
        || inventoryRevision < 0
        || slot < 0
        || slot >= 36
        || target < 0
        || target >= 36) throw new IllegalArgumentException("Corpse request");
    if ((operation == OPEN || operation == CLOSE)
        && (revision != 0 || inventoryRevision != 0 || slot != 0 || target != 0))
      throw new IllegalArgumentException("Corpse control fields");
    if (operation == TAKE_ALL && (slot != 0 || target != 0))
      throw new IllegalArgumentException("Take all fields");
  }
}
