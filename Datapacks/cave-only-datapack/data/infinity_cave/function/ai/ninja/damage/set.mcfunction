$execute if entity @s[tag=ic.ninja_leap] as @a[distance=..3] run damage @s $(slash_damage) mob_attack by @n[type=#infinity_cave:aggro,tag=ic.ninja_leap]

tag @s remove ic.ninja_leap

particle sweep_attack ~ ~ ~ 0 0.5 0 0 10 normal

playsound item.trident.throw hostile @a[distance=..15] ~ ~ ~ 1 2 1