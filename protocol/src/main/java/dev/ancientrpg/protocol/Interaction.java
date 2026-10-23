package dev.ancientrpg.protocol;

/** Intent only: coordinates identify the target, never the desired resulting state. */
public record Interaction(long sequence, int x, int y, int z) implements ActionIntent {
  public Interaction {
    if (sequence < 0) {
      throw new IllegalArgumentException("Negative sequence");
    }
  }
}
