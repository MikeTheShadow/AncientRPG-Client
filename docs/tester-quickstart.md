# AncientRPG tester quickstart

Download the Prism ZIP from the matching prerelease at:
https://github.com/MikeTheShadow/AncientRPG-Client/releases

1. Install Prism Launcher and add your own Minecraft-owning account.
2. Select Add Instance → Import, then select the downloaded ZIP. Prism downloads
   the pinned Minecraft 26.2 and Fabric Loader 0.19.3. Select JDK 25 in the instance's
   Java settings (or use Prism's Java management). No machine-specific Java path is
   included. See https://prismlauncher.org/wiki/help-pages/zip-import/ .
3. Launch the instance. Multiplayer → Add Server: enter the host address provided
   by the test organizer. This pack does not create or host a server.

The pack already includes the matching AncientRPG mod and Fabric API 0.161.0+26.2.
Do not add unrelated content mods or mix prerelease versions: registry IDs and
content identities must match the server exactly. Accounts, tokens, saves and
Minecraft binaries are not included in the pack.

The current release is 0.5.0-dev. environment.json records the exact source commit,
versions and mod SHA-256 checksums. Send that file's version information with bug
reports, plus OS, reproduction steps and a relevant redacted log excerpt. Keep
Prism accounts.json and account/session credentials private.

E opens inventory; K opens Sword Arts. Default skill keys are Z/X/C/V/B. Third
person is enforced during compatible sessions. Aim at a nearby corpse and press F
to loot; rebind Interact / loot body in Controls. Risky ore is outlined red and
becomes safe upon town arrival. An expired corpse closes its open loot menu.

Hosted servers authenticate Minecraft accounts and do not expose local `/debug`
fixtures. To reproduce the organizer's debug world independently, ask for access
to the private local-playtest bundle in AncientRPG-Server releases. That bundle
starts its own loopback server; it is not a public multiplayer hosting shortcut.
