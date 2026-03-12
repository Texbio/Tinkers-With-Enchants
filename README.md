# Tinkers with Enchants

A Minecraft Forge mod for **1.20.1** that enables vanilla and modded enchantments on [Tinkers' Construct](https://www.curseforge.com/minecraft/mc-mods/tinkers-construct) tools, weapons, and armor.
</br >\- Used AI to help make the mod

## Features

- **Enchanting table support** — TConstruct tools can be enchanted at vanilla and modded enchanting tables
- **Anvil support** — combine enchanted books with TConstruct tools
- **Smart category mapping** — each tool type only gets enchantments that make sense (swords get Sharpness, pickaxes get Efficiency, bows get Power, etc.)
- **Full enchantment effects** — Sharpness, Fire Aspect, Knockback, Sweeping Edge, Power, Flame, Multishot, Infinity, Lure, Unbreaking, all Protection variants, and more all work correctly
- **Enchantment glint** — enchanted TConstruct items show the enchantment shimmer on tools and armor
- **Stacking with modifiers** — enchantments stack with TConstruct's own modifier system (e.g. Sharpness + Quartz damage)
- **Modded enchantment support** — any mod that registers enchantments with standard `EnchantmentCategory` works automatically, plus custom categories are detected via probing

## Mod Compatibility

### Tested & Working

| Mod | Status |
|---|---|
| **Apotheosis** | Full support — all enchantments including Endless Quiver, Crescendo of Bolts, Life Mending, Capturing, etc. |
| **Enchanting Infuser** | Full support — both unenchanted and already-enchanted items |
| **Unique Enchants** | Full support — all enchantments auto-detected |
| **Min's Enchantments** | Full support — custom categories (PROJECTILE_WEAPON, SHIELD, etc.) probed and mapped |
| **Rad Enchants** | Full support — custom categories auto-detected |
| **Tinker's Ingenuity** | Compatible — no mixin conflicts |

### How Modded Enchantments Work

Mods that use standard `EnchantmentCategory` (WEAPON, DIGGER, BOW, ARMOR, etc.) work with zero configuration. For mods that create custom categories with `instanceof` checks against vanilla classes, TWE probes the category with vanilla reference items to determine what it represents and maps it to the correct TConstruct tool types.

## Configuration

Config file: `config/tinkerswithenchants-common.toml`

| Option | Default | Description |
|---|---|---|
| `enchantability` | 14 | Enchantability value for TConstruct tools (vanilla diamond = 10, gold = 22) |
| `allowEnchantingTable` | true | Allow enchanting at vanilla/modded enchanting tables |
| `allowAnvil` | true | Allow applying enchanted books via anvil |
| `filterArmorEnchantments` | true | Prevent armor enchantments from appearing on non-armor tools |

## Commands

- `/twe tinkertype` — Shows the held tool's ToolActions, TConstruct tags, TWE enchantment categories, and compatible enchantments
- `/twe tinkertype all` — Dumps all TConstruct items to a text file in `config/tinkerswithenchants/`

## Requirements

- Minecraft 1.20.1
- Forge 47.4+
- [Tinkers' Construct](https://www.curseforge.com/minecraft/mc-mods/tinkers-construct) 3.9+
- [Mantle](https://www.curseforge.com/minecraft/mc-mods/mantle) 1.11+

## Building

```
./gradlew build
```

Output jar will be in `build/libs/`.

## License

[MIT](LICENSE)
