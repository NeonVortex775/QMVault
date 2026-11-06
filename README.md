# QMVault

Personal item storage with a DragonCore interface. One entry per kind of item, so the same material
never ends up split over two slots, and the screen is just a paged view over that list.

Storage is configurable: SQLite by default (single file, nothing to install), MySQL if several
servers should share the same vaults, and the old per-player YAML files as a fallback. Switching
backends imports the existing YAML data on the next start without deleting the originals.

## Requirements

| Plugin | Needed | Why |
| --- | --- | --- |
| DragonCore 2.6.x | yes | the interface is a DragonCore screen |
| PlaceholderAPI | no | nothing depends on it, but it is declared as softdepend |
| MySQL server | no | only for `storage.type: mysql` |

Paper 1.12.2, DragonCore 2.6.2.9, Java 8. The jar ships with the SQLite JDBC driver (Windows, Linux
and macOS natives) so SQLite works without extra files.

## Install

1. Put the jar into `plugins/`. Start once to generate `config.yml`, `gui/` and `data/`.
2. Leave `storage.type: sqlite` unless you know you need MySQL.
3. Textures are client side: `resourcepacks/DragonCore/仓库/*.png`.
4. `/vault reload` after editing `config.yml` or the templates in `gui/`.

## Commands

| Command | Does | Permission |
| --- | --- | --- |
| `/vault` | open your vault | `qingmingvault.use` |
| `/vault open <player>` | open someone else's | `qingmingvault.open` |
| `/vault info [player]` | kind count, total items, backend | `qingmingvault.admin` |
| `/vault reload` | reload config and templates | `qingmingvault.admin` |

Aliases: `/qmv`, `/qmvvault`.

## Storage

```yaml
storage:
  type: sqlite            # sqlite | mysql | yml
  file: "data/vault.db"
  host: "127.0.0.1"
  port: 3306
  database: "minecraft"
  user: "root"
  password: ""
  table-prefix: "qm_"
  migrate-yml: true
```

Two tables:

```
qm_vault_users   uuid | name | kinds | total | updated
qm_vault_items   uuid | idx  | item  | amount
```

`item` is the ItemStack serialised through Bukkit's object stream and Base64 encoded, so
enchantments, lore and NBT survive a round trip. A save replaces the player's rows inside a single
transaction; a crash in the middle leaves the previous state intact.

ItemStack encoding touches server internals, so it happens on the main thread. Everything JDBC
related runs on one background worker, and the main thread only waits for it on load and on
shutdown. If the backend fails to initialise the plugin falls back to YAML instead of refusing to
start.

## Config

| Key | Meaning |
| --- | --- |
| `vault.max-kinds` | how many different items a vault may hold, `0` for no limit |
| `vault.max-per-kind` | cap per item kind, `0` for none |
| `vault.store-scan` | how many inventory slots the "store all" button looks at |
| `vault.custom-step` / `custom-big-step` | quantity arrows in the take overlay |
| `vault.auto-store` | pull matching items out of the inventory when the vault opens |
| `blacklist` | materials that cannot be stored, `SHULKER_*` prefix matching works |
| `autosave-seconds` | write-back interval for dirty vaults |

## Screen

`gui/*.yml` uses the normal DragonCore GUI syntax; the plugin only fills in data and pushes the
file, the layout stays in the YAML. The plugin looks for components by name:

| Component | Role |
| --- | --- |
| `格1` … `格N` | cell frame and click layer |
| `物1` … `物N` | the actual item slot |
| `数1` … `数N` | amount label |
| `背包格1` … `背包格36` | player inventory slots |
| `标题`, `页码`, `上一页`, `下一页` | header and paging |
| `面板xxx` | hidden while no slot is selected |
| `数量xxx` | the quantity overlay |

Slots have to be declared in `SlotConfig.yml` for clicks to work. Two things that are easy to get
wrong: the click layer has to be drawn **above** the item slot or the click never arrives, and the
slot component has to be exactly the size of the cell, because DragonCore scales the item to the
component size.

Clicks are sent as `方法.发包('QingMingVault', '<action>', ...)` and handled server side, so
refreshing the screen does not break the buttons the way EasyGui component ids do.

## Build

```bash
mkdir -p lib   # spigot-api.jar, dragoncore.jar, sqlite-jdbc-3.36.0.3.jar
bash build.sh
```

The SQLite driver is unpacked and merged into the jar during the build, with the signature files and
the unused native binaries stripped. Without `lib/sqlite-jdbc-3.36.0.3.jar` the build still works and
`storage.type: sqlite` reports itself as unavailable at runtime.
