$damage @s $(vertical) minecraft:mob_attack by @n[scores={ic.ai=1}]

playsound item.mace.smash_ground_heavy hostile @a[distance=..30] ~ ~ ~ 1 1 1

particle dust_pillar{block_state:{Name:"crimson_nylium"}} ~ ~-2 ~ 2 0 2 0.2 200 normal