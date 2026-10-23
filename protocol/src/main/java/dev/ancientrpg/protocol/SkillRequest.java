package dev.ancientrpg.protocol;

import java.util.Objects;

public record SkillRequest(long sequence, Skill skill, boolean unlock) implements ActionIntent {
  public SkillRequest {
    if (sequence < 0) throw new IllegalArgumentException("Negative sequence");
    Objects.requireNonNull(skill);
  }
}
