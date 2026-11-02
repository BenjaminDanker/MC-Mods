$damage @s $(damage) infinity_cave:magic_bypass by @n[tag=ic.laser]

playsound minecraft:entity.allay.hurt hostile @a[distance=..25] ~ ~ ~ 0.5 2 1

particle flash{color:[1.000,1.000,1.000,1.00]} ~ ~0.8 ~ 0.5 0.5 0.5 0.05 10 normal

execute on attacker on vehicle on vehicle run tag @s remove ic.laser