package dev.ancientrpg.protocol;

public sealed interface ActionIntent permits Interaction, SkillRequest, InventoryRequest {
  long sequence();
}
