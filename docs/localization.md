# Client text and localization

Minecraft loads English strings from
`protocol/src/main/resources/assets/ancientrpg/lang/en_us.json`. Keep the filename
lowercase. Inventory, Sword Arts, HUD, client feedback, tooltips and narration use
translatable components through `ClientText`; numeric values and item names are
arguments to complete translated phrases.

Item names use Minecraft's `item.ancientrpg.*` keys; descriptions append
`.description`. Slot, family, skill, category and result keys use stable catalogue
identifiers. The protocol's English metadata remains available to non-client
tools, but client rendering must not use it as display text. Bag search matches
the localized item names visible to the player.

Preserve components until layout/rendering so nested translations, fonts and
runtime language changes work. Avoid concatenating English words with values or
using translated labels as filter identifiers. Vanilla keybinding names come from
Minecraft's own language resources.

`ClientLocalizationTest` checks static and dynamic key coverage and prevents GUI
classes from falling back to hardcoded English or literal components. Add keys
and parameterized templates here when extending the GUI. The English file is
part of the compatibility fingerprint: rebuild matching client and server
artifacts after changing it.
