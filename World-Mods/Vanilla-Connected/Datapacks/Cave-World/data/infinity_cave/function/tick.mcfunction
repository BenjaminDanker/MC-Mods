execute as @e[type=#infinity_cave:mobs,tag=ic.ability] at @s run function infinity_cave:abilities/tick

execute as @a in minecraft:overworld at @s if predicate infinity_cave:y_320_or_more run tp @s ~ 317 ~

execute if data storage infinity_cave:bossbar list[] run function infinity_cave:technical/bossbar/init

# How deep the cave thinks you are: one line as each progression band opens (highest first, so nobody gets a burst).
execute as @a[scores={ic.progression=1000..}] unless score @s ic.milestone matches 4.. at @s run function infinity_cave:milestone/m4
execute as @a[scores={ic.progression=500..}] unless score @s ic.milestone matches 3.. at @s run function infinity_cave:milestone/m3
execute as @a[scores={ic.progression=250..}] unless score @s ic.milestone matches 2.. at @s run function infinity_cave:milestone/m2
execute as @a[scores={ic.progression=100..}] unless score @s ic.milestone matches 1.. at @s run function infinity_cave:milestone/m1
