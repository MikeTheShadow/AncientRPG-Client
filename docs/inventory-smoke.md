# Main Inventory Design Review — 0.4.0-dev / wire v4

This first slice is for in-game review. The shared inventory styling remains
provisional until the user approves it. It includes a server-owned 36-slot main
bag, eleven equipment positions, a separate first-aid slot, persistent ownership,
and one example of each of the eight weapon families.

## Launch

From the coordination workspace, use `python3 test.py` for normal matching builds
and a local playable session. Matching Gradle artifacts have also been prepared
for `python3 test.py --skip-build`. Do not mix older client/server
JARs; exact release, protocol, registry IDs and content must match.

Press the normal **Inventory** binding (E by default). On a matching connection it
opens the native AncientRPG dossier. At larger scaled windows, equipment, bag and
inspection appear together; smaller layouts use Equipment / Main bag / Details tabs.
The smallest layout uses a labelled equipment grid instead of a squeezed model.
Health/max health and current weapon family appear with the equipment. Attribute
scaling is still unavailable; the screen does not invent stat values.

## Review sequence

1. Inspect the player model, typography, slot labels, tab focus, search and category
   filter. Increase GUI scale and resize the window; text/actions must remain reachable.
   Wide layouts show all three regions without navigation tabs. Compact layouts
   show Equipment / Main bag / Details tabs; Main bag selects its view and focuses search.
   Search for an absent item: verify the empty-result explanation and clear-search
   control. Page arrows are disabled at their bounds, and the page count shows
   current/total pages. At a small enough size, verify actual next/previous pages.
2. In Main bag, drag Practice Sword onto **Mainhand**. Drag it back to a bag tile
   to unequip. The carried icon, compatible outlines and destination label provide
   feedback; the server confirms each move. Click to inspect without moving.
   Keyboard: Tab to an item, Enter/Space to pick up, then Tab to the destination
   and Enter/Space to place. **Details** opens item inspection in compact layouts.
   In compact layouts, hold the dragged item over **Equipment** or **Main bag**
   for a moment to switch views. The bag page arrows also support hover navigation.
   Keyboard users can activate tabs and page arrows while carrying an item.
   Release outside slots or onto the source to cancel; Escape or right-click
   cancels a carried item. Escape closes the screen once no item is carried.
   Test occupied-slot swaps, invalid targets and whole-stack bandage movement;
   no client-side split, discard or optimistic ownership change is available.
3. Equip each family: sword, dagger, axe, greatsword, greataxe, hammer, spear, rapier.
   Compare authored model silhouettes, held position and offhand labels. New weapon
   combat skills are not part of this inventory slice.
4. Equip sword mainhand and dagger offhand. Equip a rapier or two-handed weapon;
   the offhand item must return to available bag space and offhand becomes locked.
   A blocked request shows server feedback rather than pretending it succeeded.
5. Assign Field Bandage to **First aid**. This tests item placement only; healing
   is not implemented. The tooltip states this explicitly.
6. Close and reopen the screen. Disconnect/reconnect and restart the backend;
   saved bag/equipment must remain and starter examples must not be granted again.
7. Equip Practice Sword with an empty offhand. Leave the inventory and test the
   existing five sword arts. Try to move equipment during a committed attack;
   the backend must reject it. The shard/switch shortcut remains in hotbar slot 2;
   equipped mainhand is hotbar slot 1. Inventory screen input must not cast skills.
8. With a second matching client, verify visible mainhand/offhand updates without
   access to the other player's bag. Try normal Q/F and vanilla container shortcuts;
   they must not duplicate/drop/swap authoritative items.

Current health/max health come from the server. Attribute formulas and gear scaling
are intentionally shown as unavailable rather than filled with illustrative values.
Temporary world materials, secured storage, new combat abilities and healing use
remain separate reviewed changes.

All client-owned screen/HUD text, item/skill descriptions, feedback, tooltips and
narration resolve through `assets/ancientrpg/lang/en_us.json`; see
[localization](localization.md). The main inventory omits retention labels without
changing persistence or death behavior. A pending move retains confirmed contents
and marks its source/destination until the server replies.

## Evidence still required

The agent executed Gradle builds, codec/model/persistence/Minestom adapter tests,
gesture tests and artifact inspection. A packaged Fabric client on Linux under
Xvfb/software rendering connected to an isolated local-profile server: pointer
equip, keyboard unequip, compact hover-tab equip, outside-drop cancellation and
Escape cancellation passed, with saved revisions checked. Real-screen captures
are recorded in the private coordination workspace. Full visual/input acceptance,
all resolutions, two real clients, death/respawn and Windows remain unverified.
The user's permanent global-style review remains open.
