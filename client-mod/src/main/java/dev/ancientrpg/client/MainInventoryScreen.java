package dev.ancientrpg.client;

import dev.ancientrpg.protocol.InventoryCatalog;
import dev.ancientrpg.protocol.InventoryCatalog.Slot;
import dev.ancientrpg.protocol.InventoryWire;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
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
  private int selected = -1, page, bagPage, columns, rows;
  private boolean wide;
  private String search = "", category = "All";
  private EditBox searchBox;
  private Control equipmentTab, bagTab, previousBag, nextBag;
  private Control hoveredNavigation;
  private long navigationSince;
  private long renderedRevision = -1;
  private boolean requestedOpeningState;
  private String localFeedback = "";

  public MainInventoryScreen() {
    super(Component.literal("AncientRPG Character"));
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
    top = y + 64;
    bottom = y + ph - 34;
    equipX = x + 12;
    equipW = wide ? (pw - 30) * 36 / 100 : pw - 24;
    bagX = wide ? equipX + equipW + 12 : x + 12;
    bagW = wide ? (pw - 30) * 36 / 100 : pw - 24;
    detailX = wide ? bagX + bagW + 12 : x + 12;
    detailW = wide ? x + pw - detailX - 12 : pw - 24;
    tiles.clear();
    searchBox = null;
    control(x + pw - 29, y + 12, 18, 18, "X", this::onClose);
    equipmentTab = control(x + 12, y + 38, 79, 18, "Equipment", () -> switchPage(0));
    bagTab = control(x + 95, y + 38, 75, 18, "Main bag", () -> switchPage(1));
    control(
        x + 174,
        y + 38,
        70,
        18,
        "Stats",
        () -> {
          selected = -1;
          switchPage(2);
        });
    previousBag = nextBag = null;
    if (wide || page == 0) equipmentControls();
    if (wide || page == 1) bagControls();
    control(
        x + pw - 75,
        y + ph - 24,
        63,
        17,
        "Refresh",
        () -> {
          localFeedback = "";
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
    rebuildWidgets();
  }

  private Control control(int xx, int yy, int w, int h, String label, Runnable action) {
    return addRenderableWidget(new Control(xx, yy, w, h, label, action));
  }

  private void equipmentControls() {
    int available = bottom - top;
    if (available < 195) {
      int cell = (equipW - 8) / 3, rowHeight = Math.max(20, (available - 23) / 4);
      for (Slot slot : Slot.values())
        tile(
            equipX + (slot.ordinal() % 3) * (cell + 4),
            top + 20 + (slot.ordinal() / 3) * rowHeight,
            cell,
            rowHeight - 3,
            slot.index());
      return;
    }
    int slotWidth = Math.min(62, (equipW - 65) / 2);
    int tileHeight = Math.clamp((available - 116) / 3, 23, 46);
    for (int i = 0; i < 6; i++) {
      int side = i < 3 ? 0 : 1, row = i % 3;
      tile(
          side == 0 ? equipX : equipX + equipW - slotWidth,
          top + 20 + row * (tileHeight + 4),
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
    searchBox = new EditBox(font, bagX, top + 20, bagW, 18, Component.literal("Search main bag"));
    searchBox.setTextColor(IVORY);
    searchBox.setTextColorUneditable(MUTED);
    searchBox.setTextShadow(false);
    searchBox.setMaxLength(64);
    searchBox.setValue(search);
    searchBox.setHint(Component.literal("Find an item..."));
    searchBox.setResponder(
        value -> {
          search = value;
          bagPage = 0;
          updateTiles();
        });
    addRenderableWidget(searchBox);
    control(
        bagX,
        top + 42,
        53,
        17,
        category,
        () -> {
          category =
              category.equals("All") ? "Weapons" : category.equals("Weapons") ? "Supplies" : "All";
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
            "<",
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
            ">",
            () -> {
              bagPage++;
              updateTiles();
            });
  }

  private Tile tile(int xx, int yy, int w, int h, int index) {
    var tile = addRenderableWidget(new Tile(xx, yy, w, h, index));
    tiles.add(tile);
    return tile;
  }

  private List<Integer> visibleBag() {
    var list = new ArrayList<Integer>();
    for (int i = 0; i < InventoryCatalog.BAG_SIZE; i++) {
      var entry = entry(i);
      if (entry.empty()) {
        if (search.isEmpty() && category.equals("All")) list.add(i);
        continue;
      }
      var def = InventoryCatalog.item(entry.item());
      if (!def.title().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
      if (category.equals("Weapons") && !def.weapon()
          || category.equals("Supplies") && def.weapon()) continue;
      list.add(i);
    }
    return list;
  }

  private void updateTiles() {
    var visible = visibleBag();
    int perPage = Math.max(1, columns * rows);
    bagPage = Math.min(bagPage, Math.max(0, (visible.size() - 1) / perPage));
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

  private void select(int index) {
    if (index < 0) return;
    selected = index;
    localFeedback = "";
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
      localFeedback = "";
      client.move(move.source(), move.target());
    } else if (destination >= 0 && !allowed) {
      localFeedback = "Move cancelled. Choose a compatible destination.";
    } else {
      localFeedback = "Move cancelled.";
    }
  }

  private void keyboardTile(int index) {
    if (gesture.carrying()) {
      drop(index);
    } else if (ready() && gesture.begin(client.state(), index, 0, 0, true)) {
      selected = index;
      localFeedback = "Item picked up. Tab to a destination; Enter/Space to place; Esc to cancel.";
    } else {
      select(index);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
    if (event.button() == 1 && gesture.active()) {
      gesture.cancel();
      localFeedback = "Move cancelled.";
      return true;
    }
    Tile tile = tileAt(event.x(), event.y());
    if (event.button() == 0 && tile != null) {
      setFocused(tile);
      if (gesture.keyboard()) drop(tile.index);
      else {
        gesture.cancel();
        selected = tile.index;
        localFeedback = "";
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
    if (gesture.active() && !gesture.valid(state, ready())) {
      gesture.cancel();
      localFeedback = "Inventory changed or unavailable. Move cancelled.";
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
      localFeedback = "Move cancelled.";
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
    text(g, "ANCIENTRPG / CHARACTER", x + 12, y + 11, BRASS, pw - 50);
    text(g, "Main inventory  /  Kept on death", x + 12, y + 24, MUTED, pw - 50);
    g.fill(x + 12, y + 60, x + pw - 12, y + 61, LINE);
    if (wide || page == 0) renderEquipment(g, mouseX, mouseY);
    if (wide || page == 1) {
      text(g, "MAIN BAG", bagX, top + 3, BRASS, bagW);
      long occupied =
          client.state() == null
              ? 0
              : client.state().entries().subList(0, InventoryCatalog.BAG_SIZE).stream()
                  .filter(e -> !e.empty())
                  .count();
      text(g, occupied + " / 36", bagX + bagW - 50, top + 3, MUTED, 50);
      text(g, "Page " + (bagPage + 1), bagX + 29, bottom - 14, MUTED, bagW - 58);
    }
    if (wide || page == 2) renderDetails(g);
    if (wide) {
      g.fill(bagX - 6, top, bagX - 5, bottom, LINE);
      g.fill(detailX - 6, top, detailX - 5, bottom, LINE);
    }
    g.fill(x + 12, bottom + 5, x + pw - 12, bottom + 6, LINE);
    text(
        g,
        localFeedback.isEmpty() ? client.feedback() : localFeedback,
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
    String hint =
        navigation != null
            ? "Hold to change view"
            : target == null
                ? "Release outside slots to cancel"
                : target.index == gesture.source()
                    ? "Return to source to cancel"
                    : valid ? "Place in " + target.label() : "Cannot place here";
    g.nextStratum();
    int anchorX = gesture.keyboard() ? target == null ? x + pw - 42 : target.getRight() : mouseX;
    int anchorY = gesture.keyboard() ? target == null ? y + 46 : target.getY() + 8 : mouseY;
    int xx = Math.clamp(anchorX + 12, 2, width - 22);
    int yy = Math.clamp(anchorY - 8, 2, height - 22);
    g.fill(xx - 2, yy - 2, xx + 20, yy + 20, INK);
    g.outline(xx - 2, yy - 2, 22, 22, BRASS);
    drawItem(g, gesture.carried(), xx, yy);
    if (gesture.carried().quantity() > 1)
      text(g, "" + gesture.carried().quantity(), xx + 4, yy + 10, IVORY, 18);
    g.fill(x + 12, bottom + 8, x + pw - 82, y + ph - 7, INK);
    text(g, hint, x + 12, y + ph - 19, valid ? TEAL : IVORY, pw - 100);
  }

  private void renderEquipment(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    text(g, "EQUIPMENT", equipX, top + 3, BRASS, equipW);
    int left = equipX + Math.min(62, (equipW - 65) / 2) + 4;
    int right = equipX + equipW - Math.min(62, (equipW - 65) / 2) - 4;
    int modelBottom = bottom - 92;
    if (minecraft.player != null && bottom - top >= 195 && modelBottom > top + 50) {
      g.fillGradient(left, top + 19, right, modelBottom, 0xFF233337, INK);
      InventoryScreen.extractEntityInInventoryFollowsMouse(
          g,
          left,
          top + 20,
          right,
          modelBottom,
          Math.max(15, (modelBottom - top - 20) / 2),
          .0625f,
          mouseX,
          mouseY,
          minecraft.player);
    }
  }

  private void renderDetails(GuiGraphicsExtractor g) {
    var selectedEntry = entry(selected);
    var definition = selectedEntry.empty() ? null : InventoryCatalog.item(selectedEntry.item());
    text(
        g,
        definition == null ? "CURRENT CHARACTER" : "ITEM INSPECTION",
        detailX,
        top + 3,
        BRASS,
        detailW);
    int yy = top + 22;
    if (definition == null) {
      if (client.state() != null) {
        yy =
            wrapped(
                g,
                "Health  "
                    + Math.round(client.state().health())
                    + " / "
                    + Math.round(client.state().maxHealth()),
                detailX,
                yy,
                detailW,
                IVORY,
                bottom - 72);
        yy += 10;
        yy =
            wrapped(
                g,
                "Attributes and gear scaling are not active in this inventory preview.",
                detailX,
                yy,
                detailW,
                MUTED,
                bottom - 72);
      }
      wrapped(
          g,
          "Click to inspect. Drag items onto bag or gear slots. Keyboard: Tab, then Enter/Space to pick and place.",
          detailX,
          yy + 12,
          detailW,
          MUTED,
          bottom - 72);
    } else {
      if (bottom - top >= 195) {
        g.fill(detailX, yy, detailX + detailW, yy + 35, PANEL);
        drawItem(g, selectedEntry, detailX + (detailW - 16) / 2, yy + 9);
        yy += 42;
      }
      yy = wrapped(g, definition.title(), detailX, yy, detailW, IVORY, bottom - 72);
      yy =
          wrapped(
              g,
              definition.family()
                  + (definition.weapon()
                      ? definition.locksOffhand() ? " / Offhand locked" : " / One-handed"
                      : ""),
              detailX,
              yy + 4,
              detailW,
              BRASS,
              bottom - 72);
      if (bottom - yy > 120)
        yy = wrapped(g, definition.description(), detailX, yy + 10, detailW, MUTED, bottom - 80);
      text(g, "Kept on death", detailX, Math.min(yy + 9, bottom - 73), TEAL, detailW);
      wrapped(
          g,
          "Drag to a slot. Enter/Space picks up or places; Esc cancels.",
          detailX,
          bottom - 49,
          detailW,
          MUTED,
          bottom);
    }
  }

  private int wrapped(
      GuiGraphicsExtractor g, String value, int xx, int yy, int w, int color, int limit) {
    for (var line : font.split(styled(value), w)) {
      if (yy + 10 > limit) break;
      g.text(font, line, xx, yy, color, false);
      yy += 12;
    }
    return yy;
  }

  private Component styled(String value) {
    return Component.literal(value).withStyle(s -> s.withFont(FACE));
  }

  private void text(GuiGraphicsExtractor g, String value, int xx, int yy, int color, int maxWidth) {
    var clipped = font.substrByWidth(styled(value), Math.max(1, maxWidth));
    g.text(font, styled(clipped.getString()), xx, yy, color, false);
  }

  private void drawItem(GuiGraphicsExtractor g, InventoryWire.Entry entry, int xx, int yy) {
    if (entry.empty()) return;
    var stack = new ItemStack(DemoRegistration.inventoryItems.get(entry.item()), entry.quantity());
    g.item(stack, xx, yy);
  }

  private class Control extends AbstractWidget {
    private final Runnable action;

    Control(int xx, int yy, int w, int h, String title, Runnable action) {
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
      g.fill(getX(), getY(), getRight(), getBottom(), isHoveredOrFocused() ? 0xFF304149 : PANEL);
      g.outline(getX(), getY(), getWidth(), getHeight(), isFocused() ? BRASS : LINE);
      text(
          g,
          getMessage().getString(),
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
              label()
                  + ": "
                  + (value.empty() ? "Empty" : InventoryCatalog.item(value.item()).title())));
    }

    String label() {
      return index >= InventoryCatalog.BAG_SIZE
          ? Slot.values()[index - InventoryCatalog.BAG_SIZE].title()
          : "Bag slot " + (index + 1);
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
      if (gesture.carrying() && index != gesture.source()) {
        boolean compatible = gesture.canDrop(client.state(), index, ready());
        g.outline(getX(), getY(), getWidth(), getHeight(), compatible ? TEAL : LINE);
        if (isHoveredOrFocused()) {
          text(g, compatible ? "+" : "X", getRight() - 9, getY() + 2, compatible ? TEAL : IVORY, 8);
        }
      }
      boolean gear = index >= InventoryCatalog.BAG_SIZE;
      if (gear) {
        String label = Slot.values()[index - InventoryCatalog.BAG_SIZE].title();
        if (index == Slot.OFFHAND.index()) {
          var main = entry(Slot.MAINHAND.index());
          if (!main.empty() && InventoryCatalog.item(main.item()).locksOffhand())
            label = "Offhand / locked";
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
          text(g, "" + value.quantity(), getRight() - 16, getBottom() - 10, IVORY, 16);
      }
      if (isHoveredOrFocused() && !gesture.carrying()) {
        var lines = new ArrayList<Component>();
        lines.add(getMessage());
        if (!value.empty()) lines.add(styled(InventoryCatalog.item(value.item()).description()));
        if (index == Slot.OFFHAND.index()) {
          var mainhand = entry(Slot.MAINHAND.index());
          if (!mainhand.empty() && InventoryCatalog.item(mainhand.item()).locksOffhand())
            lines.add(
                styled("Unavailable with " + InventoryCatalog.item(mainhand.item()).family()));
        }
        lines.add(styled("Kept on death"));
        g.setComponentTooltipForNextFrame(font, lines, mx, my);
      }
    }
  }
}
