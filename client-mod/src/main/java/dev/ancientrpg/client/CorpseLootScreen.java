package dev.ancientrpg.client;

import static dev.ancientrpg.client.ClientText.tr;
import static dev.ancientrpg.client.InventoryUi.*;

import dev.ancientrpg.protocol.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

/** Two-pane corpse container using the ordinary owner bag; all transfers await server snapshots. */
public final class CorpseLootScreen extends Screen {
  private final CorpseClient client = CorpseClient.INSTANCE;
  private final List<Tile> tiles = new ArrayList<>();
  private int x,
      y,
      w,
      h,
      top,
      bottom,
      pane,
      columns,
      rows,
      perPage,
      bodyPage,
      bagPage,
      source = -1,
      selected = -1;
  private long revision, bagRevision;
  private double pressedX, pressedY;
  private boolean dragged, keyboard;
  private InventoryWire.Entry carried = InventoryWire.Entry.EMPTY;
  private Button takeAll;
  private Component feedback = Component.empty();

  public CorpseLootScreen() {
    super(tr("loot.title"));
  }

  public boolean isPauseScreen() {
    return false;
  }

  protected void init() {
    w = Math.max(250, Math.min(820, width - 16));
    h = Math.max(210, Math.min(440, height - 16));
    x = (width - w) / 2;
    y = (height - h) / 2;
    top = y + 68;
    bottom = y + h - 44;
    pane = (w - 36) / 2;
    int size = Math.clamp(pane / 5, 24, 36);
    columns = Math.max(3, pane / (size + 4));
    rows = Math.max(1, (bottom - top - 24) / (size + 4));
    perPage = columns * rows;
    bodyPage = Math.min(bodyPage, (35) / perPage);
    bagPage = Math.min(bagPage, 35 / perPage);
    tiles.clear();
    for (boolean bag : new boolean[] {false, true})
      for (int i = 0; i < perPage; i++) {
        int xx = x + 12 + (bag ? pane + 12 : 0) + (i % columns) * (size + 4),
            yy = top + (i / columns) * (size + 4);
        var tile = addRenderableWidget(new Tile(xx, yy, size, size, bag, i));
        tiles.add(tile);
      }
    addRenderableWidget(
        Button.builder(tr("common.close_icon"), b -> onClose())
            .bounds(x + w - 29, y + 11, 18, 18)
            .tooltip(Tooltip.create(tr("common.close")))
            .build());
    for (boolean bag : new boolean[] {false, true}) {
      int left = x + 12 + (bag ? pane + 12 : 0);
      var prev =
          addRenderableWidget(
              Button.builder(
                      tr("common.previous_icon"),
                      b -> {
                        if (bag) bagPage--;
                        else bodyPage--;
                        rebuildWidgets();
                      })
                  .bounds(left, bottom - 18, 20, 17)
                  .build());
      prev.active = (bag ? bagPage : bodyPage) > 0;
      var next =
          addRenderableWidget(
              Button.builder(
                      tr("common.next_icon"),
                      b -> {
                        if (bag) bagPage++;
                        else bodyPage++;
                        rebuildWidgets();
                      })
                  .bounds(left + pane - 20, bottom - 18, 20, 17)
                  .build());
      next.active = ((bag ? bagPage : bodyPage) + 1) * perPage < 36;
    }
    addRenderableWidget(
        Button.builder(tr("common.refresh"), b -> client.refresh())
            .bounds(x + 12, y + h - 25, 64, 18)
            .build());
    takeAll =
        addRenderableWidget(
            Button.builder(tr("loot.take_all"), b -> client.takeAll())
                .bounds(x + w - 103, y + h - 25, 91, 18)
                .build());
  }

  private InventoryWire.Entry entry(Tile tile) {
    int i = tile.index();
    if (i >= 36) return InventoryWire.Entry.EMPTY;
    if (tile.bag) {
      var state = InventoryClient.INSTANCE.state();
      return state == null ? InventoryWire.Entry.EMPTY : state.entries().get(i);
    }
    var state = client.state();
    return state == null ? InventoryWire.Entry.EMPTY : state.entries().get(i);
  }

  private boolean ready() {
    return client.state() != null
        && client.state().canLoot()
        && client.remaining() > 0
        && !client.pending()
        && !InventoryClient.INSTANCE.pending()
        && InventoryClient.INSTANCE.state() != null
        && !SkillClient.INSTANCE.committed();
  }

  private Tile at(double x, double y) {
    for (var t : tiles) if (t.visible && t.index() < 36 && t.isMouseOver(x, y)) return t;
    return null;
  }

  private void pick(Tile tile, double mx, double my, boolean keys) {
    if (!ready() || tile.bag || entry(tile).empty()) return;
    source = tile.index();
    selected = source;
    carried = entry(tile);
    revision = client.state().revision();
    bagRevision = InventoryClient.INSTANCE.state().revision();
    pressedX = mx;
    pressedY = my;
    dragged = false;
    keyboard = keys;
    feedback = Component.empty();
  }

  private void cancel() {
    source = -1;
    carried = InventoryWire.Entry.EMPTY;
    dragged = keyboard = false;
  }

  private void drop(Tile tile) {
    if (source >= 0
        && tile != null
        && tile.bag
        && tile.index() < 36
        && ready()
        && revision == client.state().revision()
        && bagRevision == InventoryClient.INSTANCE.state().revision()) {
      client.take(source, tile.index());
      feedback = Component.empty();
    } else feedback = tr("inventory.cancel");
    cancel();
  }

  public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
    if (e.button() == 1 && source >= 0) {
      cancel();
      feedback = tr("inventory.cancel");
      return true;
    }
    var tile = at(e.x(), e.y());
    if (e.button() == 0 && tile != null) {
      setFocused(tile);
      if (keyboard) drop(tile);
      else {
        cancel();
        pick(tile, e.x(), e.y(), false);
      }
      return true;
    }
    return super.mouseClicked(e, twice);
  }

  public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
    if (e.button() == 0 && source >= 0 && !keyboard) {
      if (Math.hypot(e.x() - pressedX, e.y() - pressedY) >= 4) dragged = true;
      return true;
    }
    return super.mouseDragged(e, dx, dy);
  }

  public boolean mouseReleased(MouseButtonEvent e) {
    if (e.button() == 0 && source >= 0 && !keyboard) {
      if (Math.hypot(e.x() - pressedX, e.y() - pressedY) >= 4) dragged = true;
      if (dragged) drop(at(e.x(), e.y()));
      else cancel();
      return true;
    }
    return super.mouseReleased(e);
  }

  public boolean keyPressed(KeyEvent e) {
    if (e.key() == 256 && source >= 0) {
      cancel();
      return true;
    }
    if (minecraft.options.keyInventory.matches(e)) {
      onClose();
      return true;
    }
    return super.keyPressed(e);
  }

  public void tick() {
    if (client.expired()) {
      onClose();
      return;
    }
    takeAll.active = ready() && client.state().entries().stream().anyMatch(e -> !e.empty());
    if (source >= 0
        && (!ready()
            || revision != client.state().revision()
            || bagRevision != InventoryClient.INSTANCE.state().revision())) {
      cancel();
      feedback = tr("inventory.changed");
    }
  }

  public void onClose() {
    client.close();
    super.onClose();
  }

  public void removed() {
    cancel();
    super.removed();
  }

  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
    g.fillGradient(0, 0, width, height, 0xB0081015, 0xDB080D10);
    g.fill(x, y, x + w, y + h, INK);
    g.outline(x, y, w, h, LINE);
    g.fill(x, y, x + w, y + 2, BRASS);
    text(g, client.title(), x + 12, y + 12, BRASS, w - 55);
    var state = client.state();
    Component timer =
        state == null
            ? tr("inventory.synchronizing")
            : tr(
                "loot.timers",
                (client.remaining() + 999) / 1000,
                (client.lockRemaining() + 999) / 1000);
    text(g, timer, x + 12, y + 29, MUTED, w - 24);
    text(g, tr("loot.help"), x + 12, y + 44, MUTED, w - 24);
    text(g, tr("loot.contents"), x + 12, top - 13, BRASS, pane);
    text(g, tr("inventory.bag"), x + 24 + pane, top - 13, BRASS, pane);
    g.fill(x + pane + 18, top - 15, x + pane + 19, bottom, LINE);
    text(
        g,
        tr("inventory.page", bodyPage + 1, (36 + perPage - 1) / perPage),
        x + 36,
        bottom - 13,
        MUTED,
        pane - 48);
    text(
        g,
        tr("inventory.page", bagPage + 1, (36 + perPage - 1) / perPage),
        x + 48 + pane,
        bottom - 13,
        MUTED,
        pane - 48);
    text(
        g,
        feedback.getString().isEmpty() ? client.feedback() : feedback,
        x + 82,
        y + h - 19,
        MUTED,
        w - 194);
    super.extractRenderState(g, mx, my, partial);
    if (source >= 0 && (dragged || keyboard)) {
      int xx = keyboard && getFocused() instanceof Tile t ? t.getRight() + 4 : mx + 12,
          yy = keyboard && getFocused() instanceof Tile t ? t.getY() : my - 8;
      xx = Math.clamp(xx, 2, width - 22);
      yy = Math.clamp(yy, 2, height - 22);
      g.nextStratum();
      g.fill(xx - 2, yy - 2, xx + 20, yy + 20, INK);
      item(g, carried, xx, yy);
      risk(g, carried, xx - 3, yy - 3, 24, 24);
    }
  }

  private final class Tile extends AbstractWidget {
    final boolean bag;
    final int offset;

    Tile(int x, int y, int w, int h, boolean bag, int offset) {
      super(x, y, w, h, Component.empty());
      this.bag = bag;
      this.offset = offset;
    }

    int index() {
      return (bag ? bagPage : bodyPage) * perPage + offset;
    }

    public boolean keyPressed(KeyEvent e) {
      if (isFocused() && (e.key() == 257 || e.key() == 335 || e.key() == 32)) {
        if (source >= 0) drop(this);
        else pick(this, 0, 0, true);
        return true;
      }
      return false;
    }

    protected void updateWidgetNarration(NarrationElementOutput out) {
      var value = entry(this);
      setMessage(
          tr(
              "inventory.slot_contents",
              tr("inventory.bag_slot", index() + 1),
              value.empty()
                  ? tr("inventory.empty")
                  : ClientText.item(InventoryCatalog.item(value.item()))));
      defaultButtonNarrationText(out);
    }

    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
      visible = index() < 36;
      if (!visible) return;
      var value = entry(this);
      g.fill(getX(), getY(), getRight(), getBottom(), isHoveredOrFocused() ? 0xFF304149 : PANEL);
      g.outline(
          getX(),
          getY(),
          getWidth(),
          getHeight(),
          isFocused() || !bag && selected == index() ? BRASS : LINE);
      item(g, value, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
      risk(g, value, getX(), getY(), getWidth(), getHeight());
      if (value.quantity() > 1)
        text(
            g, tr("format.number", value.quantity()), getRight() - 16, getBottom() - 10, IVORY, 16);
      if ((isHovered() || isFocused() && minecraft.getLastInputType().isKeyboard()) && source < 0) {
        var lines = new ArrayList<Component>();
        lines.add(
            value.empty()
                ? tr("inventory.empty")
                : ClientText.item(InventoryCatalog.item(value.item())));
        if (value.risky()) lines.add(tr("inventory.risky"));
        if (!bag) lines.add(tr("loot.pick_hint"));
        g.setComponentTooltipForNextFrame(font, lines, mx, my);
      }
    }
  }
}
