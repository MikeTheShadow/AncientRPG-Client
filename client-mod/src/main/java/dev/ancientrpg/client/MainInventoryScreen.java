package dev.ancientrpg.client;

import static dev.ancientrpg.client.ClientText.tr;

import dev.ancientrpg.protocol.InventoryCatalog;
import dev.ancientrpg.protocol.InventoryCatalog.Slot;
import dev.ancientrpg.protocol.InventoryWire;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Provisional native character dossier. The server owns every inventory change. */
public final class MainInventoryScreen extends Screen {
  private static final int INK = 0xFF10171B,
      PANEL = 0xFF1B252B,
      LINE = 0xFF3E4B50,
      IVORY = 0xFFF0E9DB,
      MUTED = 0xFFB6C1C1,
      BRASS = 0xFFD3B477,
      TEAL = 0xFF88CFC1;
  private static final FontDescription FACE =
      new FontDescription.Resource(Identifier.parse("ancientrpg:dossier"));
  private final InventoryClient client = InventoryClient.INSTANCE;
  private final List<Tile> tiles = new ArrayList<>();
  private final InventoryGesture gesture = new InventoryGesture();
  private int x, y, pw, ph, top, bottom, equipX, equipW, bagX, bagW, detailX, detailW;
  private int gearTop, statsHeight;
  private int selected = -1, page, bagPage, columns, rows;
  private int requestedFocus = -1;
  private boolean wide;
  private String search = "";
  private int category;
  private int pendingSource = -1, pendingTarget = -1;
  private EditBox searchBox;
  private Control equipmentTab, bagTab, detailsTab, previousBag, nextBag;
  private Control hoveredNavigation;
  private long navigationSince;
  private long renderedRevision = -1;
  private boolean requestedOpeningState;
  private Component localFeedback = Component.empty();

  public MainInventoryScreen() {
    super(tr("inventory.title"));
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  protected void init() {
    pw = Math.max(250, Math.min(900, width - 16));
    ph = Math.max(210, Math.min(480, height - 16));
    x = (width - pw) / 2;
    y = (height - ph) / 2;
    wide = pw >= 590 && ph >= 300;
    top = y + (wide ? 43 : 64);
    bottom = y + ph - 34;
    int available = bottom - top;
    statsHeight = available >= 270 ? 54 : available >= 195 ? 30 : available >= 150 ? 18 : 0;
    gearTop = top + 20 + (statsHeight == 0 ? 0 : statsHeight + 6);
    equipX = x + 12;
    equipW = wide ? (pw - 30) * 36 / 100 : pw - 24;
    bagX = wide ? equipX + equipW + 12 : x + 12;
    bagW = wide ? (pw - 30) * 36 / 100 : pw - 24;
    detailX = wide ? bagX + bagW + 12 : x + 12;
    detailW = wide ? x + pw - detailX - 12 : pw - 24;
    tiles.clear();
    searchBox = null;
    control(x + pw - 29, y + 12, 18, 18, tr("common.close_icon"), this::onClose)
        .setTooltip(Tooltip.create(tr("common.close")));
    equipmentTab = bagTab = detailsTab = null;
    if (!wide) {
      equipmentTab =
          control(x + 12, y + 38, 79, 18, tr("inventory.tab.equipment"), () -> switchPage(0));
      bagTab = control(x + 95, y + 38, 75, 18, tr("inventory.tab.bag"), () -> switchPage(1));
      detailsTab =
          control(x + 174, y + 38, 70, 18, tr("inventory.tab.details"), () -> switchPage(2));
      equipmentTab.navigationPage = 0;
      bagTab.navigationPage = 1;
      detailsTab.navigationPage = 2;
      bagTab.setTooltip(Tooltip.create(tr("inventory.bag_help")));
    }
    previousBag = nextBag = null;
    if (wide || page == 0) equipmentControls();
    if (wide || page == 1) bagControls();
    control(
        x + pw - 75,
        y + ph - 24,
        63,
        17,
        tr("common.refresh"),
        () -> {
          localFeedback = Component.empty();
          client.sync();
        });
    if (!requestedOpeningState && !client.pending()) {
      requestedOpeningState = true;
      client.sync();
    }
    updateTiles();
  }

  private void switchPage(int value) {
    page = value;
    if (!wide) rebuildWidgets();
    // Mouse dispatch focuses the clicked tab after its callback; focus the region next tick.
    requestedFocus = value;
  }

  private void focusRegion(int value) {
    if (value == 1 && searchBox != null) setFocused(searchBox);
    else if (value == 0) {
      for (Tile tile : tiles)
        if (tile.index == Slot.MAINHAND.index()) {
          setFocused(tile);
          break;
        }
    } else if (detailsTab != null) setFocused(detailsTab);
  }

  private Control control(int xx, int yy, int w, int h, Component label, Runnable action) {
    return addRenderableWidget(new Control(xx, yy, w, h, label, action));
  }

  private void equipmentControls() {
    int available = bottom - gearTop;
    if (available < 190) {
      int cell = (equipW - 8) / 3, rowHeight = Math.max(18, available / 4);
      for (Slot slot : Slot.values())
        tile(
            equipX + (slot.ordinal() % 3) * (cell + 4),
            gearTop + (slot.ordinal() / 3) * rowHeight,
            cell,
            rowHeight - 3,
            slot.index());
      return;
    }
    int slotWidth = Math.min(62, (equipW - 65) / 2);
    int tileHeight = Math.clamp((available - 96) / 3, 23, 46);
    for (int i = 0; i < 6; i++) {
      int side = i < 3 ? 0 : 1, row = i % 3;
      tile(
          side == 0 ? equipX : equipX + equipW - slotWidth,
          gearTop + row * (tileHeight + 4),
          slotWidth,
          tileHeight,
          Slot.values()[i].index());
    }
    int yy = bottom - 85, gap = 4, third = (equipW - 2 * gap) / 3;
    for (int i = 0; i < 3; i++)
      tile(equipX + i * (third + gap), yy, third, 25, Slot.values()[i + 6].index());
    int half = (equipW - gap) / 2;
    tile(equipX, yy + 29, half, 26, Slot.MAINHAND.index());
    tile(equipX + half + gap, yy + 29, half, 26, Slot.OFFHAND.index());
    tile(equipX, yy + 59, equipW, 24, Slot.BANDAGE.index());
  }

  private void bagControls() {
    searchBox = new EditBox(font, bagX, top + 20, bagW - 22, 18, tr("inventory.search"));
    searchBox.setTextColor(IVORY);
    searchBox.setTextColorUneditable(MUTED);
    searchBox.setTextShadow(false);
    searchBox.setMaxLength(64);
    searchBox.setValue(search);
    searchBox.setHint(tr("inventory.search_hint"));
    searchBox.setResponder(
        value -> {
          search = value;
          bagPage = 0;
          updateTiles();
        });
    addRenderableWidget(searchBox);
    control(
            bagX + bagW - 18,
            top + 20,
            18,
            18,
            tr("common.close_icon"),
            () -> {
              searchBox.setValue("");
              requestedFocus = 1;
            })
        .setTooltip(Tooltip.create(tr("inventory.clear_search")));
    control(
        bagX,
        top + 42,
        53,
        17,
        categoryLabel(),
        () -> {
          category = (category + 1) % 3;
          bagPage = 0;
          rebuildWidgets();
        });
    int tileSize = wide ? 35 : 40;
    columns = Math.max(3, bagW / (tileSize + 4));
    rows = Math.max(1, (bottom - top - 85) / (tileSize + 4));
    int cell = (bagW - (columns - 1) * 4) / columns;
    for (int i = 0; i < columns * rows; i++) {
      Tile t =
          tile(
              bagX + (i % columns) * (cell + 4),
              top + 64 + (i / columns) * (tileSize + 4),
              cell,
              tileSize,
              -1);
      t.bagOffset = i;
    }
    previousBag =
        control(
            bagX,
            bottom - 18,
            23,
            17,
            tr("common.previous_icon"),
            () -> {
              bagPage = Math.max(0, bagPage - 1);
              updateTiles();
            });
    nextBag =
        control(
            bagX + bagW - 23,
            bottom - 18,
            23,
            17,
            tr("common.next_icon"),
            () -> {
              bagPage++;
              updateTiles();
            });
    previousBag.setTooltip(Tooltip.create(tr("common.previous")));
    nextBag.setTooltip(Tooltip.create(tr("common.next")));
  }

  private Tile tile(int xx, int yy, int w, int h, int index) {
    var tile = addRenderableWidget(new Tile(xx, yy, w, h, index));
    tiles.add(tile);
    return tile;
  }

  private Component categoryLabel() {
    return tr(
        "inventory.category."
            + switch (category) {
              case 1 -> "weapons";
              case 2 -> "supplies";
              default -> "all";
            });
  }

  private List<Integer> visibleBag() {
    var list = new ArrayList<Integer>();
    for (int i = 0; i < InventoryCatalog.BAG_SIZE; i++) {
      var entry = entry(i);
      if (entry.empty()) {
        if (search.isEmpty() && category == 0) list.add(i);
        continue;
      }
      var def = InventoryCatalog.item(entry.item());
      if (!ClientText.item(def)
          .getString()
          .toLowerCase(Locale.ROOT)
          .contains(search.toLowerCase(Locale.ROOT))) continue;
      if (category == 1 && !def.weapon() || category == 2 && def.weapon()) continue;
      list.add(i);
    }
    return list;
  }

  private void updateTiles() {
    var visible = visibleBag();
    int perPage = Math.max(1, columns * rows);
    bagPage = Math.min(bagPage, Math.max(0, (visible.size() - 1) / perPage));
    if (previousBag != null) previousBag.active = bagPage > 0;
    if (nextBag != null) nextBag.active = (bagPage + 1) * perPage < visible.size();
    for (Tile tile : tiles) {
      if (tile.bagOffset >= 0) {
        int i = bagPage * perPage + tile.bagOffset;
        tile.index = i < visible.size() ? visible.get(i) : -1;
        tile.visible = tile.index >= 0;
      }
      tile.updateMessage();
    }
  }

  private InventoryWire.Entry entry(int index) {
    return index < 0 || client.state() == null
        ? InventoryWire.Entry.EMPTY
        : client.state().entries().get(index);
  }

  private Component positionLabel(int index) {
    return index >= InventoryCatalog.BAG_SIZE
        ? ClientText.slot(Slot.values()[index - InventoryCatalog.BAG_SIZE])
        : tr("inventory.bag_slot", index + 1);
  }

  private void select(int index) {
    if (index < 0) return;
    selected = index;
    localFeedback = Component.empty();
    if (!wide && !entry(index).empty()) switchPage(2);
  }

  private boolean ready() {
    return client.connected()
        && client.state() != null
        && !client.pending()
        && !SkillClient.INSTANCE.committed();
  }

  private Tile tileAt(double mouseX, double mouseY) {
    for (Tile tile : tiles)
      if (tile.visible && tile.index >= 0 && tile.isMouseOver(mouseX, mouseY)) return tile;
    return null;
  }

  private void drop(int destination) {
    boolean allowed = gesture.canDrop(client.state(), destination, ready());
    var move = gesture.release(client.state(), destination, ready());
    hoveredNavigation = null;
    if (move != null) {
      localFeedback = Component.empty();
      client.move(move.source(), move.target());
      if (client.pending()) {
        pendingSource = move.source();
        pendingTarget = move.target();
      }
    } else if (destination >= 0 && !allowed) {
      localFeedback = tr("inventory.cancel_invalid");
    } else {
      localFeedback = tr("inventory.cancel");
    }
  }

  private void keyboardTile(int index) {
    if (gesture.carrying()) {
      drop(index);
    } else if (ready() && gesture.begin(client.state(), index, 0, 0, true)) {
      selected = index;
      localFeedback = tr("inventory.pickup");
    } else {
      select(index);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
    if (event.button() == 1 && gesture.active()) {
      gesture.cancel();
      localFeedback = tr("inventory.cancel");
      return true;
    }
    Tile tile = tileAt(event.x(), event.y());
    if (event.button() == 0 && tile != null) {
      setFocused(tile);
      if (gesture.keyboard()) drop(tile.index);
      else {
        gesture.cancel();
        selected = tile.index;
        localFeedback = Component.empty();
        if (ready()) gesture.begin(client.state(), tile.index, event.x(), event.y(), false);
      }
      return true;
    }
    return super.mouseClicked(event, twice);
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
    if (event.button() == 0 && gesture.active() && !gesture.keyboard()) {
      gesture.movePointer(event.x(), event.y());
      return true;
    }
    return super.mouseDragged(event, dx, dy);
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (event.button() == 0 && gesture.active() && !gesture.keyboard()) {
      gesture.movePointer(event.x(), event.y());
      if (gesture.carrying()) {
        Tile tile = tileAt(event.x(), event.y());
        drop(tile == null ? -1 : tile.index);
      } else {
        int index = gesture.source();
        gesture.cancel();
        select(index);
      }
      return true;
    }
    return super.mouseReleased(event);
  }

  @Override
  public void removed() {
    gesture.cancel();
    super.removed();
  }

  @Override
  public void tick() {
    var state = client.state();
    if (requestedFocus >= 0) {
      focusRegion(requestedFocus);
      requestedFocus = -1;
    }
    if (!client.pending()) pendingSource = pendingTarget = -1;
    if (gesture.active() && !gesture.valid(state, ready())) {
      gesture.cancel();
      localFeedback = tr("inventory.changed");
    }
    if (state != null && state.revision() != renderedRevision) {
      renderedRevision = state.revision();
      updateTiles();
    }
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (event.key() == 256 && gesture.active()) {
      gesture.cancel();
      localFeedback = tr("inventory.cancel");
      return true;
    }
    if ((searchBox == null || !searchBox.isFocused())
        && minecraft.options.keyInventory.matches(event)) {
      onClose();
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
    g.fillGradient(0, 0, width, height, 0xB0081015, 0xDB080D10);
    g.fill(x + 3, y + 4, x + pw + 3, y + ph + 4, 0x55000000);
    g.fill(x, y, x + pw, y + ph, INK);
    g.outline(x, y, pw, ph, LINE);
    g.fill(x, y, x + pw, y + 2, BRASS);
    text(g, tr("inventory.heading"), x + 12, y + 11, BRASS, pw - 50);
    text(g, tr("inventory.subtitle"), x + 12, y + 24, MUTED, pw - 50);
    g.fill(x + 12, top - 4, x + pw - 12, top - 3, LINE);
    if (wide || page == 0) renderEquipment(g, mouseX, mouseY);
    if (wide || page == 1) {
      text(g, tr("inventory.bag"), bagX, top + 3, BRASS, bagW);
      long occupied =
          client.state() == null
              ? 0
              : client.state().entries().subList(0, InventoryCatalog.BAG_SIZE).stream()
                  .filter(e -> !e.empty())
                  .count();
      text(
          g,
          tr("inventory.capacity", occupied, InventoryCatalog.BAG_SIZE),
          bagX + bagW - 50,
          top + 3,
          MUTED,
          50);
      text(
          g,
          tr(
              "inventory.page",
              bagPage + 1,
              Math.max(
                  1,
                  (visibleBag().size() + Math.max(1, columns * rows) - 1)
                      / Math.max(1, columns * rows))),
          bagX + 29,
          bottom - 14,
          MUTED,
          bagW - 58);
      if (visibleBag().isEmpty()) {
        int yy =
            wrapped(
                g, tr("inventory.no_matches"), bagX + 8, top + 86, bagW - 16, IVORY, bottom - 30);
        wrapped(
            g, tr("inventory.no_matches_hint"), bagX + 8, yy + 8, bagW - 16, MUTED, bottom - 30);
      }
    }
    if (wide || page == 2) renderDetails(g);
    if (wide) {
      g.fill(bagX - 6, top, bagX - 5, bottom, LINE);
      g.fill(detailX - 6, top, detailX - 5, bottom, LINE);
    }
    g.fill(x + 12, bottom + 5, x + pw - 12, bottom + 6, LINE);
    text(
        g,
        pendingTarget >= 0 && client.pending()
            ? tr("inventory.pending", positionLabel(pendingSource), positionLabel(pendingTarget))
            : localFeedback.getString().isEmpty() ? client.feedback() : localFeedback,
        x + 12,
        y + ph - 19,
        MUTED,
        pw - 100);
    super.extractRenderState(g, mouseX, mouseY, partial);
    renderGesture(g, mouseX, mouseY);
  }

  private void renderGesture(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    if (!gesture.carrying()) {
      hoveredNavigation = null;
      return;
    }
    Control navigation = null;
    for (Control control : new Control[] {equipmentTab, bagTab, previousBag, nextBag}) {
      if (control != null
          && control.active
          && control.isMouseOver(mouseX, mouseY)
          && (control != equipmentTab || !wide && page != 0)
          && (control != bagTab || !wide && page != 1)) navigation = control;
    }
    if (!gesture.keyboard() && navigation != null) {
      if (navigation != hoveredNavigation) {
        hoveredNavigation = navigation;
        navigationSince = System.nanoTime();
      } else if (System.nanoTime() - navigationSince >= 400_000_000L) {
        hoveredNavigation = null;
        navigation.action.run();
      }
    } else hoveredNavigation = null;
    Tile target =
        gesture.keyboard() && getFocused() instanceof Tile tile ? tile : tileAt(mouseX, mouseY);
    boolean valid = target != null && gesture.canDrop(client.state(), target.index, ready());
    Component hint =
        navigation != null
            ? tr("inventory.hover_view")
            : target == null
                ? tr("inventory.cancel_outside")
                : target.index == gesture.source()
                    ? tr("inventory.cancel_source")
                    : valid ? tr("inventory.place", target.label()) : tr("inventory.incompatible");
    g.nextStratum();
    int anchorX = gesture.keyboard() ? target == null ? x + pw - 42 : target.getRight() : mouseX;
    int anchorY = gesture.keyboard() ? target == null ? y + 46 : target.getY() + 8 : mouseY;
    int xx = Math.clamp(anchorX + 12, 2, width - 22);
    int yy = Math.clamp(anchorY - 8, 2, height - 22);
    g.fill(xx - 2, yy - 2, xx + 20, yy + 20, INK);
    g.outline(xx - 2, yy - 2, 22, 22, BRASS);
    drawItem(g, gesture.carried(), xx, yy);
    if (gesture.carried().quantity() > 1)
      text(g, tr("format.number", gesture.carried().quantity()), xx + 4, yy + 10, IVORY, 18);
    g.fill(x + 12, bottom + 8, x + pw - 82, y + ph - 7, INK);
    text(g, hint, x + 12, y + ph - 19, valid ? TEAL : IVORY, pw - 100);
  }

  private void renderEquipment(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    text(g, tr("inventory.equipment"), equipX, top + 3, BRASS, equipW);
    renderStats(g);
    int left = equipX + Math.min(62, (equipW - 65) / 2) + 4;
    int right = equipX + equipW - Math.min(62, (equipW - 65) / 2) - 4;
    int modelBottom = bottom - 92;
    if (minecraft.player != null && bottom - gearTop >= 190 && modelBottom > gearTop + 30) {
      g.fillGradient(left, gearTop, right, modelBottom, 0xFF233337, INK);
      InventoryScreen.extractEntityInInventoryFollowsMouse(
          g,
          left,
          gearTop,
          right,
          modelBottom,
          Math.max(15, (modelBottom - gearTop) / 2),
          .0625f,
          mouseX,
          mouseY,
          minecraft.player);
    }
  }

  private void renderStats(GuiGraphicsExtractor g) {
    var state = client.state();
    Component health =
        state == null
            ? tr("inventory.waiting")
            : tr("inventory.health", Math.round(state.health()), Math.round(state.maxHealth()));
    if (statsHeight == 0) {
      g.fill(equipX + equipW / 2, top, equipX + equipW, top + 15, INK);
      text(g, health, equipX + equipW / 2, top + 3, IVORY, equipW / 2);
      return;
    }
    int yy = top + 18;
    g.fill(equipX, yy, equipX + equipW, yy + statsHeight, PANEL);
    text(g, health, equipX + 6, yy + 3, IVORY, equipW - 12);
    if (statsHeight >= 30) {
      var mainhand = entry(Slot.MAINHAND.index());
      Component family =
          mainhand.empty()
              ? tr("inventory.unarmed")
              : ClientText.family(InventoryCatalog.item(mainhand.item()));
      text(
          g,
          tr("inventory.weapon", family),
          equipX + 6,
          yy + (statsHeight >= 54 ? 23 : 17),
          BRASS,
          equipW - 12);
    }
    if (statsHeight >= 54) {
      int barWidth = equipW - 12;
      g.fill(equipX + 6, yy + 15, equipX + 6 + barWidth, yy + 18, INK);
      if (state != null)
        g.fill(
            equipX + 6,
            yy + 15,
            equipX + 6 + Math.round(barWidth * state.health() / state.maxHealth()),
            yy + 18,
            TEAL);
      text(g, tr("inventory.attributes_pending"), equipX + 6, yy + 39, MUTED, equipW - 12);
    }
  }

  private void renderDetails(GuiGraphicsExtractor g) {
    var selectedEntry = entry(selected);
    var definition = selectedEntry.empty() ? null : InventoryCatalog.item(selectedEntry.item());
    text(g, tr("inventory.inspection"), detailX, top + 3, BRASS, detailW);
    int yy = top + 22;
    if (definition == null) {
      wrapped(g, tr("inventory.help"), detailX, yy, detailW, MUTED, bottom - 72);
    } else {
      if (bottom - top >= 195) {
        g.fill(detailX, yy, detailX + detailW, yy + 35, PANEL);
        drawItem(g, selectedEntry, detailX + (detailW - 16) / 2, yy + 9);
        yy += 42;
      }
      yy = wrapped(g, ClientText.item(definition), detailX, yy, detailW, IVORY, bottom - 72);
      yy =
          wrapped(
              g,
              definition.weapon()
                  ? tr(
                      "inventory.item_family",
                      ClientText.family(definition),
                      tr(
                          definition.locksOffhand()
                              ? "inventory.offhand_locked"
                              : "inventory.one_handed"))
                  : ClientText.family(definition),
              detailX,
              yy + 4,
              detailW,
              BRASS,
              bottom - 72);
      if (bottom - yy > 120)
        yy =
            wrapped(
                g,
                ClientText.description(definition),
                detailX,
                yy + 10,
                detailW,
                MUTED,
                bottom - 80);
      wrapped(g, tr("inventory.item_help"), detailX, bottom - 49, detailW, MUTED, bottom);
    }
  }

  private int wrapped(
      GuiGraphicsExtractor g, Component value, int xx, int yy, int w, int color, int limit) {
    for (var line : font.split(styled(value), w)) {
      if (yy + 10 > limit) break;
      g.text(font, line, xx, yy, color, false);
      yy += 12;
    }
    return yy;
  }

  private Component styled(Component value) {
    return value.copy().withStyle(s -> s.withFont(FACE));
  }

  private void text(
      GuiGraphicsExtractor g, Component value, int xx, int yy, int color, int maxWidth) {
    var lines = font.split(styled(value), Math.max(1, maxWidth));
    if (!lines.isEmpty()) g.text(font, lines.getFirst(), xx, yy, color, false);
  }

  private void drawItem(GuiGraphicsExtractor g, InventoryWire.Entry entry, int xx, int yy) {
    if (entry.empty()) return;
    var stack = new ItemStack(DemoRegistration.inventoryItems.get(entry.item()), entry.quantity());
    g.item(stack, xx, yy);
  }

  private class Control extends AbstractWidget {
    private final Runnable action;
    private int navigationPage = -1;

    Control(int xx, int yy, int w, int h, Component title, Runnable action) {
      super(xx, yy, w, h, styled(title));
      this.action = action;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean twice) {
      if (active) action.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
      if (active
          && isFocused()
          && (event.key() == 257 || event.key() == 335 || event.key() == 32)) {
        action.run();
        return true;
      }
      return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      defaultButtonNarrationText(output);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
      boolean chosen = navigationPage >= 0 && page == navigationPage;
      g.fill(
          getX(),
          getY(),
          getRight(),
          getBottom(),
          chosen ? 0xFF34352C : isHoveredOrFocused() && active ? 0xFF304149 : PANEL);
      g.outline(getX(), getY(), getWidth(), getHeight(), chosen || isFocused() ? BRASS : LINE);
      text(
          g,
          getMessage(),
          getX() + 5,
          getY() + (getHeight() - 9) / 2,
          active ? IVORY : MUTED,
          getWidth() - 9);
    }
  }

  private final class Tile extends AbstractWidget {
    private int index, bagOffset = -1;

    Tile(int xx, int yy, int w, int h, int index) {
      super(xx, yy, w, h, Component.empty());
      this.index = index;
    }

    void updateMessage() {
      var value = entry(index);
      setMessage(
          styled(
              tr(
                  "inventory.slot_contents",
                  label(),
                  value.empty()
                      ? tr("inventory.empty")
                      : ClientText.item(InventoryCatalog.item(value.item())))));
    }

    Component label() {
      return positionLabel(index);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean twice) {
      select(index);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
      if (isFocused() && (event.key() == 257 || event.key() == 335 || event.key() == 32)) {
        keyboardTile(index);
        return true;
      }
      return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
      defaultButtonNarrationText(output);
      output.add(
          net.minecraft.client.gui.narration.NarratedElementType.USAGE,
          tr(gesture.carrying() ? "inventory.keyboard_place" : "inventory.keyboard_pick"));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
      var value = entry(index);
      boolean selectedTile = index == selected;
      g.fill(
          getX(),
          getY(),
          getRight(),
          getBottom(),
          selectedTile ? 0xFF34352C : isHoveredOrFocused() ? 0xFF293A42 : PANEL);
      g.outline(
          getX(), getY(), getWidth(), getHeight(), selectedTile || isFocused() ? BRASS : LINE);
      if (client.pending() && (index == pendingSource || index == pendingTarget))
        g.outline(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, BRASS);
      if (gesture.carrying() && index != gesture.source()) {
        boolean compatible = gesture.canDrop(client.state(), index, ready());
        g.outline(getX(), getY(), getWidth(), getHeight(), compatible ? TEAL : LINE);
        if (isHoveredOrFocused()) {
          text(
              g,
              tr(compatible ? "inventory.drop_valid_icon" : "inventory.drop_invalid_icon"),
              getRight() - 9,
              getY() + 2,
              compatible ? TEAL : IVORY,
              8);
        }
      }
      boolean gear = index >= InventoryCatalog.BAG_SIZE;
      if (gear) {
        Component label = ClientText.slot(Slot.values()[index - InventoryCatalog.BAG_SIZE]);
        if (index == Slot.OFFHAND.index()) {
          var main = entry(Slot.MAINHAND.index());
          if (!main.empty() && InventoryCatalog.item(main.item()).locksOffhand())
            label = tr("inventory.locked_slot");
        }
        text(
            g,
            label,
            getX() + (value.empty() ? 4 : 22),
            getY() + (getHeight() - 9) / 2,
            MUTED,
            getWidth() - (value.empty() ? 8 : 25));
      }
      if (!value.empty()) {
        drawItem(
            g,
            value,
            gear ? getX() + 3 : getX() + (getWidth() - 16) / 2,
            getY() + (getHeight() - 16) / 2);
        if (value.quantity() > 1)
          text(
              g,
              tr("format.number", value.quantity()),
              getRight() - 16,
              getBottom() - 10,
              IVORY,
              16);
      }
      if ((isHovered() || isFocused() && minecraft.getLastInputType().isKeyboard())
          && !gesture.carrying()) {
        var lines = new ArrayList<Component>();
        lines.add(getMessage());
        if (client.pending() && (index == pendingSource || index == pendingTarget))
          lines.add(styled(tr("inventory.pending_slot")));
        if (!value.empty())
          lines.add(styled(ClientText.description(InventoryCatalog.item(value.item()))));
        if (index == Slot.OFFHAND.index()) {
          var mainhand = entry(Slot.MAINHAND.index());
          if (!mainhand.empty() && InventoryCatalog.item(mainhand.item()).locksOffhand())
            lines.add(
                styled(
                    tr(
                        "inventory.unavailable_with",
                        ClientText.family(InventoryCatalog.item(mainhand.item())))));
        }
        g.setComponentTooltipForNextFrame(font, lines, mx, my);
      }
    }
  }
}
