# Changelog

## 1.0.2

### Added
- Swasher now accepts Quick Charge, Multishot, and Piercing (functional, not just cosmetic).
- Addon mod crossbows/longbows auto-detect via `tconstruct:modifiable/ranged/crossbows` and `tconstruct:modifiable/ranged/longbows` tags.

### Fixed
- Sledge hammer, mattock, and swasher no longer accept WEAPON-category enchants.
- Piercing on swasher now hits every entity in the projectile's path (was only hitting the first).
- TC 3.9 compatibility: fallback paths for missing `ranged/quick_charge` and `ranged/launcher` tags.

### Performance
- Multi-hit Piercing pass throttles when more than 64 fluid-spit projectiles are active at once.

## 1.0.1

### Added
- Javelin accepts TRIDENT enchants (Loyalty, Channeling, Impaling) and standard damage enchants. Riptide excluded.
- Enchantability auto-computed from tool durability (5–30 range, scales with material quality) — replaces flat config value.
- `/twe info` subcommands (`enchantability`, `unbreaking`, `sweep`, `enchants`) and `/twe tinkertype all` dump for diagnostics.

### Fixed
- Curse of Binding no longer leaks onto weapons via treasure shelves.
- Double-Unbreaking on armor.
- Hoe-only enchants (e.g. Temptation) no longer leak onto pickaxes.
- Shield enchants (Reflective, Shield Bash) no longer leak onto non-shield items.
- Staffs and fishing rods no longer incorrectly receive TRIDENT category.

## 1.0.0
- Initial public release.
