execute as @e[type=#infinity_cave:mobs,tag=ic.ability] at @s run function infinity_cave:abilities/tick

execute as @a in minecraft:overworld at @s if predicate infinity_cave:y_320_or_more run tp @s ~ 317 ~

execute if data storage infinity_cave:bossbar list[] run function infinity_cave:technical/bossbar/init