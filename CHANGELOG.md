# Changelog

## 3.1.4 — Wind-Charged Crafting & Duration
- Added Duration (Длительность) I–III for all five throwing discs, in enchanting tables and anvils.
- Duration level adds +3 outbound flight ticks and +3 blocks of maximum outgoing range.
- Reduced unenchanted flight times/ranges for every disc; level III exceeds former baseline slightly.
- Copper, iron, gold and diamond disc crafting now consumes a Wind Charge in the center of the four-material ring.
- Netherite disc still uses normal smithing upgrade of the wind-charge-crafted diamond disc.
- Existing material-pickup recipe unlock advancements preserved.
- Versioned mod and Actions artifact to 3.1.4.


## 3.1.3 — Recipe Discovery
- Added five vanilla-style recipe unlock advancements for copper, iron, golden, diamond and netherite discs.
- Recipes unlock when their crafting material first enters the player's inventory, producing the default "New Recipes Unlocked!" toast and adding the recipe to the recipe book.
- Netherite upgrade also unlocks on obtaining the diamond disc or netherite upgrade smithing template.
- Kept normal recipe unlock behavior for knowledge books and /recipe commands.
- Versioned JAR and Actions artifact bumped to 3.1.3.


## 3.1.2 — World Interaction Update
- Thrown discs shatter regular, tinted and all stained glass blocks and panes (35 types), with vanilla break particles/sounds and no glass drops.
- Discs break decorated pottery and ordinary flower pots; their stored items and appropriate block drops are preserved.
- Discs ring village bells on hit, including during a fast Wind Burst dash.
- Fragile blocks no longer spend a ricochet: a disc continues through the broken block.
- All other projectile-sensitive blocks keep their vanilla onProjectileHit interactions.
- JAR and GitHub Actions artifacts updated to version 3.1.2.


## 3.1.1 — Smooth Flight
- Enabled vanilla 2-tick position and rotation interpolation for each flying disc's item_display visual.
- Reduced abrupt-looking yaw increments to 32 degrees/tick; client render blends angles between updates.
- Gameplay hitboxes, projectiles, bounce, pickup, enchantments and jukebox support unchanged.
- Artifact now contains version 3.1.1 in its name.
- When the music ZIP is not available, CI publishes a clearly labeled NO-MUSIC preview to test visual changes.


## 3.1.0 — Music Update
- Added vanilla jukebox support to all 5 throwing discs.
- Five custom jukebox song entries and streamed audio declarations.
- Assignments: Copper — Acid Tunnel of Love; Iron — Metalheart 5; Gold — NOW'S YOUR CHANCE TO BE A; Diamond — Attack of the Killer Queen; Netherite — Into the Fire.
- Added Python importer for the user-provided original music ZIP.
- Versioned JAR: ThrowingDiscs-MC1.21.11-3.1.0.jar.
- Complete music JAR is published by Actions after the original five-track ZIP is uploaded to the repository root.

## 3.0.0 — Enchantments Update
- Removed Honey Copper and Slime Copper disc items, recipes, and textures.
- Added Knockaway I–III, Adhesion I, Ricochet I–III as data-driven enchantments.
- Supported vanilla Wind Burst I–III via its original enchantment.
- Vanilla Sharpness, Smite, Bane, Fire Aspect, Knockback, Unbreaking and related compatible enchantments.
- Adhesion collects multiple item stacks and keeps them saved on the projectile.
- Changed output JAR and GitHub Actions artifact to versioned 3.0.0 names.
