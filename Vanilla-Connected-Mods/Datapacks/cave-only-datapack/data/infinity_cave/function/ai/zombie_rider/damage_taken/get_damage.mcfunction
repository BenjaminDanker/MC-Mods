execute store result score #tmp ic.const run data get entity @s Health

scoreboard players set #tmp1024 ic.const 1024

execute store result storage infinity_cave:zombie_rider damage_taken float 1 run scoreboard players operation #tmp1024 ic.const -= #tmp ic.const

function infinity_cave:ai/zombie_rider/damage_taken/calculate with storage infinity_cave:zombie_rider