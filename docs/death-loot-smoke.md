# Death and Loot — 0.5.0-dev / wire v5

Implementation approved 2026-10-24. Linux checks used the packaged Fabric mod,
pinned API/loader and isolated loopback local server with a synthetic test identity.
They do not replace authenticated two-player acceptance or Windows execution.

Verified in the real Minecraft 26.2 client:

- Genuine skull-faced Block of Death loads, and walking into it opens native death.
- Buckle/turn/flop progresses into a resting player body with the visible skin.
- Respawn restores full health at the emerald town; rear third person remains active.
- Main bag retains safe ore and gear. Field ore uses a separate red-bordered stack.
- Right-clicking a body opens the two-pane loot screen with server timers/contents.
- Pointer dragging transfers the whole risky stack into the ordinary main bag.
  The red border and localized risk tooltip remain after transfer.
- The emptied body disappears after ten seconds and the open screen reports gone.
- Returning to town merges eight safe plus eight risky ore into sixteen safe ore.
- Restarted local server restores the earlier unexpired body and its elapsed timer.
- No mixin/registry/renderer exception. The offline profile-key HTTP 401 and Xvfb
  cursor-shape warning are test-environment messages.

The smoke run found a respawn history bug (the death-to-spawn path could count as
another hazard crossing). The implementation resets history before the first
respawn contact check and handles native teleports as landing samples. A repeated
real-client death/respawn passed after the fix; an adapter regression covers it.

Automated tests separately cover bounded codecs, localization, native death and
claim denials, exact timers, party/killer snapshots, stale competing claims,
full-bag atomic rejection, migration, committed recovery and corrupt/partial WALs.

Still unverified: two authenticated real clients observing the same animation,
late-viewer skin/equipment in another account, graphical killer/party lock and FFA
transition, full keyboard transfer/paging, tiny-screen/large-GUI-scale layouts,
latency, held equipment variants and Windows. These remain acceptance checks;
passing builds does not mark them passed.

## Repeat locally

1. Build/install both matching distributions. Launch with `python3 test.py --skip-build`
   from the coordination workspace when available, or your normal launcher/local server.
2. Spawn lies inside the emerald town. Run `/debug-iron-ore` for safe ore.
3. Walk outside the border before running it again. E shows distinct safe/red stacks.
4. Touch `(8,64,4)`, watch the animation and respawn. Safe items remain.
5. Return near the body, look down and right-click. Drag ore onto an empty main-bag
   slot, or use Take all. Do not touch the block again while approaching.
6. Walk back into town; red borders clear and matching stacks consolidate.
7. Use two accounts to verify the one-minute lock and shared observer presentation.

Visual evidence: [separate safe/risky stacks](images/death-risk-stacks.png),
[resting body](images/death-body.png), [loot container](images/death-loot.png),
[confirmed transfer](images/death-loot-transfer.png), [town merge](images/death-town.png).
