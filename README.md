# HavocSpawners

Virtual, dialog-driven spawners for **Paper 1.21.x** (1.21.6+).

Spawners never spawn a mob. They simulate one, bank the drops in a virtual store, and hand them to
the player through server-side **dialogs** — no chest GUIs, no click-slot maths, no inventory desync.

---

## Why this exists

It is a ground-up replacement for a SmartSpawner-style setup, with three things done differently:

1. **Dialogs instead of chest GUIs.** Every screen is a Paper `Dialog`: real buttons, real tooltips,
   real text inputs and sliders. Storage is shown *per item type* with counts, stack totals, share of
   capacity and live sell value, instead of 45 nameless chest slots.
2. **Bulk withdrawal that does not lag.** Emptying forty pages, or a whole spawner holding four
   million items, costs the same per tick as emptying one.
3. **A real importer.** Your existing SmartSpawner database — YAML, SQLite or MySQL — comes across
   in one command.

---

## Requirements

| | |
|---|---|
| Server | Paper 1.21.6 or newer (Folia supported) |
| Java | 21 |
| Optional | Vault (selling/upgrades), EconomyShopGUI or ShopGUIPlus (prices) |

The plugin refuses to enable below 1.21.6, because the Dialog API does not exist there.

---

## Installing

1. Drop `HavocSpawners-1.0.4.jar` into `plugins/`.
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
the owner is offline. Auto-sell pays priced drops into the owner's balance; auto-collect pushes
stacks into a linked chest, barrel or hopper (link it by clicking *Link a container* and right-clicking
the block). Both are bounded per pass so a full double chest never stalls a region thread.

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
| `/hs give <player> mob\|item <TYPE> [amount] [stack] [level]` | `havocspawners.command.give` | Gives a spawner item |
| `/hs import yaml\|sqlite\|mysql` | `havocspawners.command.import` | SmartSpawner import |
| `/hs reload` | `havocspawners.command.reload` | Reloads every config file |
| `/hs clearghosts` | `havocspawners.command.reload` | Drops spawners whose world is gone |
| `/hs stats` | `havocspawners.command.reload` | Runtime counters |

Aliases: `/havocspawners`, `/hspawners`, `/havoc`.

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
gradle build        # -> build/libs/HavocSpawners-1.0.4.jar
```

GitHub Actions (`.github/workflows/build.yml`) builds on every push and uploads the jar as an
artifact. Push a tag starting with `v` (or run the workflow manually with *release* checked) to
publish a GitHub release with the jar attached.

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
