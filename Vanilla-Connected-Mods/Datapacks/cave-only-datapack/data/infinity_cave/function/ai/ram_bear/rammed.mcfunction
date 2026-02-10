execute on vehicle store result storage infinity_cave:ram_bear damage int 1.25 run attribute @s attack_damage get

rotate @s facing entity @p[gamemode=!spectator]

summon armor_stand ^ ^1.4 ^0.5 {Invisible:1b,equipment:{feet:{id:"minecraft:acacia_button",count:1,components:{"minecraft:enchantments":{"infinity_cave:technical/motion":1}}}}}

execute on target at @s run function infinity_cave:ai/ram_bear/ram_attack with storage infinity_cave:ram_bear

playsound entity.generic.explode hostile @a[distance=..40] ~ ~ ~ 1 0 1

particle minecraft:flame ~ ~1 ~ 0.5 0.5 0.5 0.4 100 normal