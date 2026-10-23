package dev.ancientrpg.client;

import dev.ancientrpg.protocol.Skill;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Presentation-only prerequisites; server checks every unlock independently. */
public final class SkillTreeScreen extends Screen {
  private static final int[] REQUIREMENTS = {0, 1, 1, 2, 4 | 8};
  private static final String[] DETAILS = {
    "10 damage · 0.50s action · 0.60s cooldown · Free root",
    "8 damage to 3 · 0.80s action · 2.50s cooldown · Requires Quick Cut",
    "16 damage · 0.70s action · 2.00s cooldown · Requires Quick Cut",
    "7 + 7 damage · 0.90s action · 3.00s cooldown · Requires Sweeping Arc",
    "24 damage to 3 · 1.20s action · 5.00s cooldown · Requires Twin Fang AND Piercing Thrust"
  };
  private final Button[] nodes = new Button[5];
  private int nodeWidth, left, right, center, top;

  public SkillTreeScreen() {
    super(Component.literal("One-Handed Sword"));
  }

  @Override
  protected void init() {
    nodeWidth = Math.min(174, (width - 32) / 2);
    left = width / 2 - nodeWidth - 6;
    right = width / 2 + 6;
    center = (width - nodeWidth) / 2;
    top = Math.max(42, (height - 190) / 2);
    int[] xs = {center, left, right, left, center},
        ys = {top, top + 40, top + 40, top + 80, top + 120};
    for (Skill skill : Skill.values()) {
      int i = skill.ordinal();
      nodes[i] =
          addRenderableWidget(
              Button.builder(
                      Component.literal(skill.title()), b -> SkillClient.INSTANCE.unlock(skill))
                  .bounds(xs[i], ys[i], nodeWidth, 24)
                  .tooltip(
                      Tooltip.create(Component.literal(skill.description() + "\n" + DETAILS[i])))
                  .build());
    }
    addRenderableWidget(
        Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width / 2 - 40, top + 158, 80, 20)
            .build());
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    var state = SkillClient.INSTANCE.state();
    g.fill(0, 0, width, height, 0xE8101924);
    g.text(font, title, width / 2 - font.width(title) / 2, top - 32, 0xFFE8BD69);
    String points =
        state == null
            ? "Waiting for server..."
            : state.points() + " practice points · Each branch node costs 1";
    g.text(font, points, Math.max(8, width / 2 - font.width(points) / 2), top - 18, 0xFF9AB6BC);
    // Orthogonal branch connectors; both branches terminate at Crescent Finish.
    line(g, width / 2, top + 24, width / 2, top + 32);
    line(g, left + nodeWidth / 2, top + 32, right + nodeWidth / 2, top + 32);
    line(g, left + nodeWidth / 2, top + 32, left + nodeWidth / 2, top + 80);
    line(g, right + nodeWidth / 2, top + 32, right + nodeWidth / 2, top + 112);
    line(g, left + nodeWidth / 2, top + 104, left + nodeWidth / 2, top + 112);
    line(g, left + nodeWidth / 2, top + 112, right + nodeWidth / 2, top + 112);
    line(g, width / 2, top + 112, width / 2, top + 120);
    for (Skill skill : Skill.values()) {
      int i = skill.ordinal();
      boolean learned = state != null && (state.learned() & skill.bit()) != 0;
      nodes[i].setMessage(Component.literal(skill.title() + (learned ? " · Learned" : " · 1 pt")));
      nodes[i].active =
          state != null
              && !learned
              && !SkillClient.INSTANCE.pending()
              && state.points() > 0
              && (state.learned() & REQUIREMENTS[i]) == REQUIREMENTS[i];
    }
    g.text(font, SkillClient.INSTANCE.feedback(), 8, height - 13, 0xFFE8BD69);
    super.extractRenderState(g, mouseX, mouseY, partial);
  }

  private void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2) {
    g.fill(
        Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, 0xFF577D82);
  }
}
