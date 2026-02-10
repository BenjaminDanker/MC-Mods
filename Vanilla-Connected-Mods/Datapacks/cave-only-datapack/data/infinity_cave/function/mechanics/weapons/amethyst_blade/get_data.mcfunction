execute store result score #max_health ic.const run attribute @s minecraft:max_health get

execute store result score #health ic.const run data get entity @s Health

scoreboard players operation #max_health ic.const /= 2 ic.const

execute on attacker run function infinity_cave:technical/player/fetch

execute if score #max_health ic.const >= #health ic.const run function infinity_cave:mechanics/weapons/amethyst_blade/half with storage infinity_cave:data player