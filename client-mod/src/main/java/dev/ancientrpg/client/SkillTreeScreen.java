package dev.ancientrpg.client;

import dev.ancientrpg.protocol.Skill;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Custom presentation and selection only; the server owns every unlock. */
public final class SkillTreeScreen extends Screen {
  private static final int INK = 0xFF0C1722,
      PANEL = 0xFF122331,
      TEAL = 0xFF76D8CB,
      GOLD = 0xFFEBC987,
      WHITE = 0xFFE7EFF1,
      MUTED = 0xFF9AAEB9;
  private static final int[] REQUIREMENTS = {0, 1, 1, 2, 4 | 8};
  private static final String[] DAMAGE = {"10", "8 x 3 targets", "16", "7 + 7", "24 x 3 targets"};
  private static final String[] COOLDOWN = {"0.60s", "2.50s", "2.00s", "3.00s", "5.00s"};
  private static final String[] CARRY = {"0.7m", "1.1m", "2.8m", "0.75 + 0.85m", "2.2m"};
  private static final String[] REQUIRES = {
    "Starting skill", "Quick Cut", "Quick Cut", "Sweeping Arc", "Twin Fang + Piercing Thrust"
  };
  private final Card[] nodes = new Card[5];
  private Skill selected = Skill.QUICK_CUT;
  private Card learn;
  private int x, y, panelWidth, panelHeight, graphWidth, detailX, detailWidth;
  private boolean tree;

  public SkillTreeScreen() {
    super(Component.literal("Sword Arts"));
  }

  @Override
  protected void init() {
    panelWidth = Math.min(680, width - 24);
    panelHeight = Math.min(326, height - 24);
    x = (width - panelWidth) / 2;
    y = (height - panelHeight) / 2;
    tree = panelWidth >= 470;
    graphWidth = tree ? panelWidth * 55 / 100 : panelWidth * 43 / 100;
    detailX = x + graphWidth + 14;
    detailWidth = panelWidth - graphWidth - 28;
    int start = y + 48, row = (panelHeight - 88) / (tree ? 4 : 5);
    int nodeWidth = tree ? (graphWidth - 30) / 2 : graphWidth - 20;
    int nodeHeight = Math.min(38, row - 2);
    for (Skill skill : Skill.values()) {
      int i = skill.ordinal();
      int nx = x + 10, ny = start + row * i;
      if (tree) {
        nx =
            x
                + (i == 0 || i == 4
                    ? (graphWidth - nodeWidth) / 2
                    : i == 2 ? graphWidth - nodeWidth - 10 : 10);
        ny = start + row * (i == 0 ? 0 : i == 1 || i == 2 ? 1 : i == 3 ? 2 : 3);
      }
      nodes[i] =
          addRenderableWidget(
              new Card(
                  nx, ny, nodeWidth, nodeHeight, skill.title(), () -> selected = skill, skill));
    }
    learn =
        addRenderableWidget(
            new Card(
                detailX,
                y + panelHeight - 35,
                detailWidth,
                24,
                "Learn skill",
                () -> SkillClient.INSTANCE.unlock(selected),
                null));
    addRenderableWidget(new Card(x + panelWidth - 28, y + 10, 18, 18, "x", this::onClose, null));
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  private boolean learned(Skill skill) {
    var state = SkillClient.INSTANCE.state();
    return state != null && (state.learned() & skill.bit()) != 0;
  }

  private boolean available(Skill skill) {
    var state = SkillClient.INSTANCE.state();
    return state != null
        && (state.learned() & REQUIREMENTS[skill.ordinal()]) == REQUIREMENTS[skill.ordinal()];
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    var state = SkillClient.INSTANCE.state();
    g.fillGradient(0, 0, width, height, 0xD907101C, 0xF0050B12);
    g.fill(x + 4, y + 5, x + panelWidth + 4, y + panelHeight + 5, 0x66000000);
    g.fill(x, y, x + panelWidth, y + panelHeight, INK);
    g.fill(x, y, x + 3, y + 31, GOLD);
    text(g, "SWORD ARTS", x + 12, y + 11, GOLD);
    text(
        g,
        state == null ? "Synchronizing..." : state.points() + " POINTS AVAILABLE",
        x + 12,
        y + 26,
        MUTED);
    g.fill(x + 10, y + 39, x + panelWidth - 10, y + 40, 0xFF29414E);
    g.fill(detailX - 8, y + 48, detailX - 7, y + panelHeight - 12, 0xFF29414E);
    if (tree) {
      connect(g, 0, 1);
      connect(g, 0, 2);
      connect(g, 1, 3);
      connect(g, 3, 4);
      connect(g, 2, 4);
    }
    int i = selected.ordinal(), dy = y + 49;
    for (var line : font.split(Component.literal(selected.title()), detailWidth)) {
      g.text(font, line, detailX, dy, WHITE, false);
      dy += 10;
    }
    dy += 5;
    if (panelHeight >= 260) {
      for (var line : font.split(Component.literal(selected.description()), detailWidth)) {
        g.text(font, line, detailX, dy, MUTED, false);
        dy += 10;
      }
      dy += 9;
    }
    stat(g, "DAMAGE", DAMAGE[i], dy);
    dy += 16;
    stat(g, "COOLDOWN", COOLDOWN[i], dy);
    dy += 16;
    stat(
        g, "ACTION", String.format(java.util.Locale.ROOT, "%.2fs", selected.duration() / 20.0), dy);
    dy += 16;
    stat(g, "CARRY", CARRY[i], dy);
    dy += 19;
    text(
        g,
        learned(selected) ? "ART LEARNED" : "REQUIRES",
        detailX,
        dy,
        learned(selected) ? TEAL : GOLD);
    if (!learned(selected)) {
      dy += 11;
      for (var line : font.split(Component.literal(REQUIRES[i]), detailWidth)) {
        if (dy + 9 > y + panelHeight - 40) break;
        g.text(font, line, detailX, dy, MUTED, false);
        dy += 10;
      }
    }
    learn.active =
        state != null
            && !learned(selected)
            && available(selected)
            && state.points() > 0
            && !SkillClient.INSTANCE.pending()
            && state.action() == null;
    String action =
        state == null
            ? "Synchronizing"
            : SkillClient.INSTANCE.pending()
                ? "Awaiting server"
                : learned(selected)
                    ? "Learned"
                    : !available(selected)
                        ? "Path locked"
                        : state.action() != null
                            ? "Finish current art"
                            : state.points() == 0 ? "No points" : "Learn / 1 point";
    learn.setMessage(Component.literal(action));
    String feedback = SkillClient.INSTANCE.feedback();
    text(
        g,
        font.plainSubstrByWidth(
            feedback.isEmpty() ? "Select an art to inspect" : feedback, graphWidth - 20),
        x + 10,
        y + panelHeight - 20,
        MUTED);
    super.extractRenderState(g, mouseX, mouseY, partial);
  }

  private void stat(GuiGraphicsExtractor g, String label, String value, int yy) {
    // Small screens stack values beside a shorter label rather than overflowing the panel.
    if (font.width(label) + font.width(value) + 8 > detailWidth) label = label.substring(0, 3);
    text(g, label, detailX, yy, MUTED);
    text(g, value, detailX + detailWidth - font.width(value), yy, WHITE);
  }

  private void connect(GuiGraphicsExtractor g, int from, int to) {
    Card a = nodes[from], b = nodes[to];
    int ax = a.getX() + a.getWidth() / 2, bx = b.getX() + b.getWidth() / 2;
    int ay = a.getBottom(), by = b.getY(), mid = (ay + by) / 2;
    int color = learned(Skill.values()[from]) ? 0xFF438D89 : 0xFF293C49;
    // The long right branch travels outside the card stack into the final node.
    g.fill(ax, ay, ax + 1, mid + 1, color);
    g.fill(Math.min(ax, bx), mid, Math.max(ax, bx) + 1, mid + 1, color);
    g.fill(bx, mid, bx + 1, by, color);
  }

  private void text(GuiGraphicsExtractor g, String value, int xx, int yy, int color) {
    g.text(font, value, xx, yy, color, false);
  }

  private final class Card extends AbstractWidget {
    private final Runnable press;
    private final Skill skill;

    Card(int xx, int yy, int w, int h, String label, Runnable press, Skill skill) {
      super(xx, yy, w, h, Component.literal(label));
      this.press = press;
      this.skill = skill;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
      press.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
      if (active
          && isFocused()
          && (event.key() == 257 || event.key() == 335 || event.key() == 32)) {
        playDownSound(minecraft.getSoundManager());
        press.run();
        return true;
      }
      return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      defaultButtonNarrationText(output);
    }

    @Override
    protected void extractWidgetRenderState(
        GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
      boolean chosen = skill != null && skill == selected;
      int accent = chosen ? GOLD : skill != null && learned(skill) ? TEAL : 0xFF456170;
      int xx = getX(), yy = getY();
      g.fill(xx, yy, getRight(), getBottom(), isHoveredOrFocused() ? 0xFF233C4D : PANEL);
      g.fill(xx, yy, xx + 2, getBottom(), accent);
      if (chosen || isFocused()) {
        g.fill(xx, yy, getRight(), yy + 1, accent);
        g.fill(xx, getBottom() - 1, getRight(), getBottom(), accent);
      }
      String label = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 12);
      text(
          g,
          label,
          xx + 6,
          yy + (skill == null ? (getHeight() - 8) / 2 : 4),
          active ? WHITE : MUTED);
      if (skill != null) {
        String status = learned(skill) ? "LEARNED" : available(skill) ? "1 POINT" : "LOCKED";
        text(g, status, xx + 6, getBottom() - 10, learned(skill) ? TEAL : MUTED);
      }
    }
  }
}
