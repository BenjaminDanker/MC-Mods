effect give @a[distance=..15] blindness 3 1 true

playsound block.fire.extinguish hostile @a[distance=..15] ~ ~ ~ 0.5 1 1

particle minecraft:large_smoke ~ ~1 ~ 2 2 2 0 100 normal

$execute positioned ~ ~2 ~ run spreadplayers ~ ~ 12 24 under $(int) true @s