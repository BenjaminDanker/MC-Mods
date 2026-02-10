# Ocean-Tectonic Ocean-Only

Merged vanilla datapack:
- Ocean-only Overworld (no land biomes)
- Tectonic-style deep ocean terrain shaping
- `sea_level = 319`
- Bedrock ceiling intended at world top (Y=319)

## Install
1. Create/open a world.
2. Copy this folder (`ocean-tectonic-ocean-only`) into:
   `<world save>/datapacks/`
3. In-game: run `/reload`.
4. Create a **new** world to see worldgen changes.

## Quick test commands
- `/gamemode spectator`
- `/tp @s 0 319 0`
- `/locate biome minecraft:deep_ocean`

## Notes
- This pack overrides the vanilla overworld dimension generator at `data/minecraft/dimension/overworld.json`.
- Terrain shaping comes from Tectonic’s overworld noise settings and referenced density functions.
