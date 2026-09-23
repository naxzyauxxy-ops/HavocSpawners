# HavocSpawners

Spawners for **Paper 1.21.x** (1.21.6+), in a fully configurable **chest GUI** — or Paper dialogs, if
you prefer them.

A spawner can either **simulate** its mobs, banking what they would have dropped so a ×26,000 stack
costs the server nothing, or **spawn them for real** like a vanilla spawner. That is a per-spawner
switch in its own menu. Bedrock players get native forms either way.

---

## Why this exists

It is a ground-up replacement for a SmartSpawner-style setup, with three things done differently:

1. **Your GUI, your layout.** The chest screens are driven by `gui_layouts/*.yml` in the same format
   the old plugin used, so an existing layout drops straight in. Prefer Paper's dialogs? One setting
   swaps every screen over, and both run the same code.
2. **Bulk withdrawal that does not lag.** Emptying forty pages, or a whole spawner holding four
   million items, costs the same per tick as emptying one.
3. **A real importer.** Your existing SmartSpawner database — YAML, SQLite or MySQL — comes across
   in one command.

---

## Loot without configuring anything

Every spawner type produces something out of the box. If a type is missing from `mob_drops.yml` or
`item_spawners.yml`, a table is invented once and cached:

- an **item spawner** drops its own material — a chest spawner spawns chests, a bone block spawner
  spawns bone blocks, and so on for every item in the game;
- a **mob spawner** is learned from that mob's *own vanilla loot table* — the server is asked what a
  sniffer really drops, rather than the plugin shipping a hand-written guess that goes stale the day
  Mojang changes one.

The mob's vanilla table is rolled `loot.auto-samples` times (40 by default) and the results become
drop entries: which materials appeared, how often, and in what amounts. Raise it if you care about
rare drops being represented — at 40 rolls a 2.5% drop is often missed entirely; by 200 it shows up
at about the right rate. It costs that sampling once per mob type, then never again.

```yaml
loot:
  auto-generate: true
  auto-item-exp: 1
  auto-mob-exp: 3
  auto-samples: 40
```

**Anything you configure always wins** — a type present in the YAML is never second-guessed. Every
invented table is named in the server log, so you can see what was generated and override just the
ones you care about. A mob whose vanilla table cannot be read (some need a real killed entity) falls
back to experience only and says so in the log.

---

## Turning a spawner off

The right-click menu has an on/off switch. A spawner turned off stops simulating but keeps everything
it has already produced, so it is the "stop filling up while I deal with this" button, not a
destructive one. Turning it back on restarts the clock rather than paying out a backlog of catch-up
cycles for the time it spent paused.

It is in all three presentations — dialog, chest GUI and Bedrock forms.

---

## Simulated or real spawns

Each spawner works one of two ways, and you can switch a single spawner from its own menu:

| | |
|---|---|
| **SIMULATED** *(default)* | No mob ever exists. The drops a mob *would* have made are worked out and banked in the spawner's storage. This is what lets a ×26,000 stack cost the server nothing. |
| **REAL** | Real mobs are spawned into the world like a vanilla spawner, and players kill them for their own drops. Nothing is banked. |

```yaml
spawner:
  spawn-mode: SIMULATED
  allow-player-spawn-mode: true
  real:
    max-per-cycle: 4
    max-nearby: 24
    spawn-radius: 2
```

**Switching never deletes anything.** Whatever a spawner already banked stays browsable and
withdrawable — it just stops growing while that spawner is in REAL mode, and starts again when you
switch back. Nobody loses a stockpile by trying the other mode out.

**The caps are not optional.** A stacked spawner's simulated mob count is enormous by design, and
asking for that many *real* mobs would take the server down — so real spawning is capped per cycle
and by how many of that mob are already standing near the block. Stacking a REAL spawner makes it
reach the cap more reliably rather than exceed it. An item spawner in REAL mode drops its item on the
floor instead of spawning anything.

---

## Two menu styles

The same plugin, two presentations. Pick one in `config.yml`:

```yaml
ui:
  mode: MODERN            # MODERN (chest GUI) or DIALOG
  allow-player-choice: true
```

| | |
|---|---|
| **MODERN** *(default)* | A classic chest GUI. Item icons in real slots, controls on the bottom row, the shape players already know. |
| **DIALOG** | Paper's server-side dialogs. Real buttons and tooltips, sliders for page ranges, a text field for network names, and no inventory to desync. |

### The chest layouts are yours

The storage and sell-confirmation screens are laid out by two files, in the same `slot_N` format the
old plugin used — drop an existing layout in and it keeps working:

```
plugins/HavocSpawners/gui_layouts/storage_gui.yml
plugins/HavocSpawners/gui_layouts/sell_confirm_gui.yml
```

Storage `slot_1`–`slot_9` are the bottom row. Supported actions: `previous_page`, `next_page`,
`sort_items`, `open_filter`, `sell_all`, `sell_and_exp`, `collect_exp`, `take_all`, `drop_page`,
`bulk_withdraw`, `return_main`, `close`, `none`. The `if: sell_integration / no_sell_integration`
conditions work, so one slot can be *Sell + XP* when an economy is hooked and *Take all* when it is
not. `info_button: true` gives the display tile, and `PLAYER_HEAD` means "use this spawner's own
icon" — the mob-head slot. Any slot you leave out is filled with a plain panel.

Sell confirmation uses `slot_1`–`slot_27` with `confirm`, `cancel` and `none`, and
`skip_sell_confirmation: true` sells immediately with no confirm screen at all.

Both files reload with `/hs reload`. A typo falls back to the built-in layout and logs a warning
rather than leaving you a screen with no buttons on it.

With `allow-player-choice: true` every player picks for themselves:

```
/hs ui modern
/hs ui dialog
/hs ui default     # follow the server setting again
```

The main menu also carries a **Menu style** button, so a player can flip between the two without
leaving the spawner they are looking at.

Their choice lives in `ui_modes.yml` — its own small file, not the spawner database, so a database
blip can never lock anyone out of the menus. A player who never runs the command simply follows the
server default and never appears in the file.

**Both styles drive the same code.** Every chest button calls the identical action the dialog button
calls — withdrawing, selling, stacking, upgrading, filtering — so a fix lands in both at once and
they cannot drift apart. Only the layout differs.

Where a chest genuinely cannot do what a dialog does, it is replaced rather than dropped:

- the bulk-withdraw slider becomes preset buttons (1, 5, 20 pages, or everything);
- the stack slider becomes ±1 / ±8 / ±64 and *add everything*;
- naming a network — the one thing a chest has no field for — closes the menu and captures your next
  chat line instead (type `cancel` to back out).

Every click inside a chest menu is cancelled before it runs, including shift-clicks and number-key
swaps, so an icon can never be taken out and a real item can never be shoved in.

Bedrock players are unaffected by all of this: Geyser cannot render Java dialogs, so they keep
getting native Bedrock forms whichever mode the server is in.

---

## Requirements

| | |
|---|---|
| Server | Paper 1.21.6 or newer (Folia supported) |
| Java | 21 |
| Optional | Vault (selling/upgrades), EconomyShopGUI or ShopGUIPlus (prices), Floodgate (Bedrock) |
| Clients | Java and Bedrock (see below) |

The plugin refuses to enable below 1.21.6, because the Dialog API does not exist there.

---

## Installing

1. Drop `HavocSpawners-1.5.0.jar` into `plugins/`.
2. Start the server once to generate `plugins/HavocSpawners/`.
3. Edit `config.yml`, then `/hs reload`.

---

## Importing from SmartSpawner

Stop SmartSpawner first (or remove it), then pick the mode matching how it stored data:

```
/hs import yaml      # plugins/SmartSpawner/spawners_data.yml
/hs import sqlite    # plugins/SmartSpawner/spawners.db
/hs import mysql     # credentials from the import: section of config.yml
```

The import runs off the main thread and reports how many rows were read, imported, skipped and failed.

**What comes across:** position, entity type, item-spawner material, stack size, stored XP, the whole
virtual inventory (including damaged items and tipped arrows), drop filters, preferred sort order and
the last player who used the spawner (as the new owner).

**What does not:** delay, activation range, capacity and mob counts are re-derived from *this*
plugin's `config.yml`, so one file governs balance after the move. Capacity maths is identical
(`45 slots × pages-per-stack × stack size`), so nothing overflows — and the importer adds items
without a capacity check regardless, so an import can never delete a player's stock.

Set `import.skip-existing: false` if you want a second import to overwrite spawners already present.

> Tip: run `/hs import` on a copy of your world first. It is additive and safe, but a dry run on a
> test server tells you the row counts before it matters.

---

## The bulk withdrawal

This is the feature most spawner plugins get wrong. Naively, "drop 40 pages" builds 1,800 item stacks
and 1,800 dropped entities inside a single tick, and the server stutters.

HavocSpawners:

- stores items as `long` counters per item signature, so a page is *derived* rather than stored;
- converts only `bulk-drop.stacks-per-tick` virtual slots into real stacks per tick;
- throws stacks out along the player's line of sight, or fills their inventory first if asked;
- hard-caps loose item entities per request (`bulk-drop.max-item-entities`);
- puts anything that could not be delivered straight back into storage — items are never destroyed;
- shows live progress on the action bar and locks the spawner so two withdrawals cannot race.

**Where the items go.** By default withdrawals are *thrown* — each stack spawns just below eye level
and is launched along your line of sight with a little random spread, exactly like pressing Q. They
arc out in front of you instead of piling up on your feet, so you can aim them into a hopper, a
minecart or a shulker. Tune it with `throw-from-look`, `throw-strength` and `pickup-delay-ticks`, or
set `throw-from-look: false` to go back to plain drops underfoot.

Every withdrawal dialog also carries an *Into my inventory instead of the ground* toggle, so a player
can override `prefer-player-inventory` per action.

Three ways to pull items out:

- **Storage → Drop a page** — throws 45 stacks, one click, the old plugin's drop button. The
  screen stays open and refreshes when the throw lands, so you can sit there and empty page after page.
- **Storage → Bulk withdraw** — pick a first and last page with the sliders, or *Withdraw everything*.
- **Storage → \<item\> → Drop all on ground** — every unit of one material, thrown out.

The same engine drains an entire network from one button.

---

## Exclusive features

**Auto-sell & auto-collect** — per spawner, running every `automation.interval-seconds` even while
the owner is offline. Auto-sell pays priced drops into the owner's balance.

Auto-collect feeds **a hopper placed directly under the spawner** — that block and nothing else. No
linking step, no other container types, no search radius. Break the hopper and collection stops until
one is put back; the toggle is left alone so rebuilding resumes it. Each pass moves a bounded number
of stacks, so a hopper chain never stalls a region thread.

**Upgrade tiers** — five levels out of the box (`upgrades.yml`), each buying faster cycles, a loot
multiplier, extra storage pages, extra XP capacity and extra activation range. Levels ride along on
the spawner item when it is broken and replaced.

**Networks + analytics** — group spawners into a named network and control them together: sell the
whole network, claim all XP, drain everything to yourself, or flip auto-sell for all of them at once.
Every spawner keeps a rolling hourly history, so *items/hour* and *earnings/hour* are measurements
rather than lifetime averages, and `/hs top` ranks the best earners.

---

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/hs info` | `havocspawners.command.use` | Opens the spawner you are looking at |
| `/hs list` | `havocspawners.command.list` | Spawner browser with teleport buttons |
| `/hs near [radius]` | `havocspawners.command.near` | Lists spawners around you |
| `/hs top` | `havocspawners.command.top` | Top earning spawners |
| `/hs prices` | `havocspawners.command.use` | Sell price list |
| `/hs ui dialog\|modern` | *(none)* | Choose how the menus look, per player |
| `/hs give <player> mob\|item <TYPE> [amount] [stack] [level]` | `havocspawners.command.give` | Gives a spawner item |
| `/hs import yaml\|sqlite\|mysql` | `havocspawners.command.import` | SmartSpawner import |
| `/hs reload` | `havocspawners.command.reload` | Reloads every config file |
| `/hs clearghosts` | `havocspawners.command.reload` | Drops spawners whose world is gone |
| `/hs fixblocks` | `havocspawners.command.reload` | Repairs spawner blocks showing the wrong mob |
| `/hs fixitems [player]` | `havocspawners.command.reload` | Rewrites old spawner items in inventories, ender chests and shulkers |
| `/hs settype mob\|item <TYPE>` | `havocspawners.command.reload` | Forces the type of the spawner you are looking at |
| `/hs inspect` | `havocspawners.command.reload` | Dumps what the held spawner item really contains |
| `/hs stats` | `havocspawners.command.reload` | Runtime counters |

Aliases: `/havocspawners`, `/hspawners`, `/havoc`, and — for drop-in compatibility with an existing
SmartSpawner setup — `/spawner` and `/ss`.

### Legacy give syntax

`give` accepts the old SmartSpawner argument order as well as its own, so shop menus that fire
`spawner give ...` through console keep working untouched. All of these are equivalent:

```
/hs give Steve mob ZOMBIE 3                    # native
/spawner give Steve zombie 3                   # legacy, kind inferred
/spawner give spawner Steve zombie 3           # legacy, explicit mob spawner
/spawner give item_spawner Steve bone_block 3  # legacy, explicit item spawner
```

`[stack]` and `[level]` are optional trailing arguments on every form and default to 1. With no kind
token the name is matched against entity types first, then materials.

> Remove SmartSpawner before relying on `/spawner` and `/ss` — with both plugins installed, whichever
> registers first wins the alias.

Feature permissions: `havocspawners.use`, `.stack`, `.break`, `.changetype`, `.sell`, `.upgrade`,
`.automation`, `.network`, `.bulkdrop`, `.admin`.

---

## Configuration files

| File | Purpose |
|---|---|
| `config.yml` | Storage backend, spawner defaults, breaking rules, bulk-drop tuning, economy, automation, networks, analytics |
| `mob_drops.yml` | Loot table per entity type |
| `item_spawners.yml` | Loot table per item-spawner material |
| `prices.yml` | Custom sell prices |
| `upgrades.yml` | The upgrade ladder |
| `lang/en_US.yml` | Chat messages (MiniMessage) — copy the folder to add a language |
| `gui_layouts/*.yml` | Chest-GUI button layouts (storage, sell confirmation) |
| `ui_modes.yml` | Per-player menu style, written only for players who chose one |

---

## Storage

SQLite by default; set `storage.mode: MYSQL` for a shared database across servers
(`storage.server-name` separates them). Writes are queued and flushed in batches off the main thread
every `storage.flush-interval-seconds`; nothing touches the database from a region thread. A failed
flush rolls back and re-queues, so a database blip never loses a spawner.

Items are stored via `ItemStack#serializeAsBytes`, so enchantments, custom names and every data
component survive a restart — unlike the legacy `MATERIAL:amount` format, which silently dropped them.

---

## Protection plugins

Every handler runs at `HIGHEST` with `ignoreCancelled`, so any claim or region plugin that cancels a
break or interact event first is respected automatically. No per-plugin integration to keep updated.

---

## Building

No Gradle wrapper is committed; the CI workflow pins the Gradle version instead.

```bash
gradle build        # -> build/libs/HavocSpawners-1.5.0.jar
```

GitHub Actions (`.github/workflows/build.yml`) builds on every push and uploads the jar as an
artifact. Push a tag starting with `v` (or run the workflow manually with *release* checked) to
publish a GitHub release with the jar attached.

---

## Java and Bedrock

Bedrock clients **cannot see Java dialog screens** — Geyser does not translate those packets — so on
a cross-play server a Bedrock player right-clicking a spawner would get nothing at all. HavocSpawners
detects them through Floodgate and sends the same screens as **native Bedrock forms** instead.

Everything is covered: the spawner menu, storage browser, per-item actions, bulk withdraw (with real
Bedrock sliders and a toggle), sell confirmation, stacking, upgrades, automation, networks, filters,
analytics, the spawner browser, leaderboard and price list. Every button routes through the exact
same code the Java dialogs use, so behaviour is identical — only the presentation differs.

The Floodgate and Cumulus APIs are reached entirely by reflection, so:

- there is **no extra dependency** to install or keep in version-sync;
- with Floodgate absent, `available()` stays false and every player simply keeps the Java dialogs;
- if a future Geyser release renames something, the forms degrade to a logged warning rather than an
  error spam or a broken menu.

```yaml
bedrock:
  enabled: true
  force-forms-for-java: false   # true = send forms to Java players too, for testing
  colors:                       # Bedrock only understands the 16 legacy codes
    accent: "c"                 # the hex theme above is mapped onto these
    good: "f"
    ...
```

Two things worth knowing: Bedrock forms carry no item icons, so storage rows are shown as
`Name / count` text; and Bedrock has no hex colour support, hence the separate legacy palette.

---

## Breaking a spawner

Breaking a full spawner used to throw its entire contents on the ground, which is exactly the kind of
entity flood that drops TPS. It no longer drops anything: `breaking.storage-on-break` decides what
happens instead.

| Mode | What happens |
|---|---|
| `KEEP_IN_ITEM` *(default)* | The contents ride **inside the spawner item** and come back when it is placed again. Instant, lossless, zero entities — breaking a spawner holding four million bones costs the same as an empty one. |
| `VOID` | Contents are destroyed. Fast, but players lose the lot. |
| `DROP` | The old behaviour. Still metered, but this is the setting that costs TPS. |

With `KEEP_IN_ITEM` the item's lore shows what it is carrying, and placing it restores the storage
without a capacity check — so tightening `pages-per-stack` later can never delete what a player
already had. Stored XP still goes straight to the breaker either way.

---

## Old and foreign spawner items

A spawner item minted by **SmartSpawner**, by an older build of this plugin, by a crate, a shop or a
plain `/give` carries none of HavocSpawners' persistent data. Placing one used to fall straight
through to a hard-coded pig — so every old spawner sitting in a player's ender chest turned into a
pig spawner the moment it hit the ground.

Placement now interrogates the item properly, in descending order of trust:

1. **the item's own block data** — where vanilla, WorldEdit and most plugins keep the type;
2. **any foreign persistent data** — scanned by *value* rather than by key name, so it reads
   SmartSpawner and plugins nobody has written yet, with no namespace list to keep updated;
3. **the display name and lore** — colour codes (`§a`, `&c`, `&#f40d0d`) stripped and small caps
   like `ᴢᴏᴍʙɪᴇ ꜱᴘᴀᴡɴᴇʀ` folded back to ASCII, longest word-run matched first so *Cave Spider* beats
   *Spider*, and old names like *Zombie Pigman* mapped to their modern types;
4. **the block itself, one tick later** — in case vanilla had not written the item's data yet.

Only when all four come back empty does `legacy.unknown-type` apply, and the player is told so they
can correct it with a spawn egg rather than quietly running a pig farm.

**Item spawners are held to a higher standard than mob spawners.** A material name turns up on a mob
spawner for entirely innocent reasons — `Contains: Rotten Flesh` in the lore, a loot preview, a
foreign key holding a menu icon — and reading one of those as the spawner's identity is how a zombie
spawner came out the other side as an item spawner. So:

- a **material** is believed only from a key that says it holds one (`item`, `material`, `block`,
  `drop`, `loot`), or from the words directly in front of the word *Spawner* in the item's name —
  `Bone Block Spawner` is an item spawner, `Zombie Spawner (Bones)` is not;
- an **entity type** is believed from anywhere, because a mob name on a spawner item is never an
  accident;
- **lore is searched for entity types only** — lore describes what a spawner *holds*, not what it is.

Where a name contains both — `Rabbit Spawner`, with `RABBIT` being an entity *and* an item — the
entity wins.

**Unless the item declares itself.** SmartSpawner records an item spawner as the literal token `ITEM`
where a mob spawner names a mob, with the material stored beside it:

```yaml
entityType: ITEM
itemSpawnerMaterial: BONE_BLOCK
```

That token is a declaration, and once it is present there is nothing left to confuse a material with
— so the caution above is dropped: the material is taken from *any* key, or from anywhere in the
name, and a declared item spawner is never turned back into a mob spawner by a stray mob name. If it
declares itself but never says which material, it says so in chat and points at `/hs settype` rather
than silently becoming a pig.

### Clearing out the old items entirely

Identifying a foreign item at placement fixes it the moment it is placed — it does nothing for the
hundreds already sitting in ender chests, and each of those is a support ticket waiting to happen. So
they get rewritten in place instead: an old spawner item is replaced, in its own slot, by the
equivalent HavocSpawners item of the same type and count.

Two things run that sweep:

- **On login**, for the joining player's inventory, ender chest and any shulker boxes they carry
  (`legacy.convert-on-join`). This is the one that matters: an offline player's inventory cannot be
  reached through the API at all, so login is the only moment their stock can be fixed — no player
  data files are ever opened by hand.
- **`/hs fixitems`** on demand, for everyone online at once, or `/hs fixitems <player>` for one.

```yaml
legacy:
  convert-on-join: true         # sweep each player as they log in
  convert-inside-shulkers: true # players hoard spawners in shulkers
  remove-unidentified: false    # see below
```

It is a **conversion, not a deletion**. An item that cannot be identified at all is left exactly
where it is and reported, because an unidentifiable spawner is still somebody's property — look at
one with `/hs inspect` before deciding. `remove-unidentified: true` deletes them instead; that one is
off by default deliberately.

The sweep runs on each player's own region thread, so it is Folia-safe, and it is delayed a second
after login so it lands after anything else that restores an inventory on join.

### When it still gets it wrong

`/hs inspect`, holding the spawner, prints everything the item actually carries — its name, its block
data, every persistent-data key and value, and the verdict the reader reached:

```
│ Ours        false
│ Name        ʙᴏɴᴇ ʙʟᴏᴄᴋ ꜱᴘᴀᴡɴᴇʀ
│ smartspawner:entitytype            ITEM
│ smartspawner:itemspawnermaterial   BONE_BLOCK
│ Verdict     item BONE_BLOCK (from item data (declared))
```

That is the whole diagnosis in one screenshot, instead of guessing from the symptom.

```yaml
legacy:
  unknown-type: PIG      # what an unidentifiable spawner item becomes
  warn-on-unknown: true  # tell the player when we had to guess
```

Spawners **already mis-adopted** by an earlier build are recoverable: `/hs fixblocks` treats the block
as evidence in the two cases where it can be trusted — a spawner stored as the fallback type, and a
spawner stored as an *item* spawner whose block still shows a mob — and adopts the block's type back
into the database rather than overwriting it. Those are reported separately as *recovered*.

Anything that survives both passes can be corrected by hand without breaking it:

```
/hs settype mob ZOMBIE        # look at the spawner first
/hs settype item BONE_BLOCK
```

Unlike the spawn egg, `settype` does not require the storage to be empty — the contents of a
mis-typed spawner are exactly what you are trying not to lose.

---

## The spinning mob in the cage

A spawner block gets the mob it displays from vanilla `block_entity_data`, not from the plugin. That
data does not exist for every spawner: an **item spawner** has no entity to put there at all, and a
few entity types do not round-trip through an item's block state. When it is missing the client falls
back to vanilla's default spawn data — a **pig** — which is why bone block and shulker spawners looked
like pig spawners even though the plugin's own data was correct the whole time.

The block state is now written from plugin data at placement (and again a tick later, so it wins over
vanilla's own write), and whenever a spawner's type is changed with an egg. Item spawners get an empty
cage rather than a stray pig. That fixes every type at once rather than special-casing the two that
were reported.

Spawners **already placed** in your world still carry the wrong block data. Run:

```
/hs fixblocks
```

It walks every known spawner, repairs the ones whose displayed type disagrees with their real type,
and reports how many were skipped because their chunk was not loaded — fly out to those and run it
again. Storage, stack size, level and type were never affected, so nothing is lost either way.

---

## Theming

The house theme is red on white. Every colour the plugin draws — dialogs *and* chat — comes from two
places, both editable without touching the jar:

- `config.yml` → `theme:` — the seven dialog colours (`accent`, `accent-dim`, `good`, `warn`, `bad`,
  `ink`, `faint`). Any `#rrggbb` value works; an invalid one falls back to the built-in default.
- `lang/en_US.yml` — the chat messages, written in MiniMessage, so they carry their own colours.

Change either and run `/hs reload`. No rebuild.

---

## Known scope

- Dialog labels are English only; their colours are themeable but the wording is not yet in `lang/`.
- Holograms are stubbed behind `hologram.enabled` and not yet rendered.
- Shop integrations are read-only price lookups (EconomyShopGUI, ShopGUIPlus) reached reflectively,
  so a shop plugin update can never break spawner selling.
