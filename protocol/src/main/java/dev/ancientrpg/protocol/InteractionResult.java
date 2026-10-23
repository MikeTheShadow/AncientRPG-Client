package dev.ancientrpg.protocol;

import java.util.Objects;

public record InteractionResult(long sequence, Status status, boolean active) {
  public enum Status {
    APPLIED,
    DENIED
  }

  public InteractionResult {
    if (sequence < 0) {
      throw new IllegalArgumentException("Negative sequence");
    }
    Objects.requireNonNull(status);
    if (status == Status.DENIED && active) {
      throw new IllegalArgumentException("Denied response must not disclose target state");
    }
  }
}
