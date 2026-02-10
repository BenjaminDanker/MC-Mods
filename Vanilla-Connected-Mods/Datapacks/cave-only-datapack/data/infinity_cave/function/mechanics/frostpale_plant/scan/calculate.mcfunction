scoreboard players operation #inventory ic.int -= #ic10 ic.const

execute if score #inventory ic.int matches 0.. run function infinity_cave:mechanics/frostpale_plant/scan/inventory
execute if score #inventory ic.int matches -9..-1 run data modify storage infinity_cave:frostpale scan.location set value hotbar

#hotbar 
execute if score #inventory ic.int matches -1 run data modify storage infinity_cave:frostpale scan.slot set value 8
execute if score #inventory ic.int matches -2 run data modify storage infinity_cave:frostpale scan.slot set value 7
execute if score #inventory ic.int matches -3 run data modify storage infinity_cave:frostpale scan.slot set value 6
execute if score #inventory ic.int matches -4 run data modify storage infinity_cave:frostpale scan.slot set value 5
execute if score #inventory ic.int matches -5 run data modify storage infinity_cave:frostpale scan.slot set value 4
execute if score #inventory ic.int matches -6 run data modify storage infinity_cave:frostpale scan.slot set value 3
execute if score #inventory ic.int matches -7 run data modify storage infinity_cave:frostpale scan.slot set value 2
execute if score #inventory ic.int matches -8 run data modify storage infinity_cave:frostpale scan.slot set value 1
execute if score #inventory ic.int matches -9 run data modify storage infinity_cave:frostpale scan.slot set value 0

execute if score #inventory ic.int matches 27.. if data storage infinity_cave:frostpale scan.player_data.equipment.offhand.components."minecraft:attribute_modifiers"[{type:"minecraft:tempt_range",id:"infinity_cave:id",amount:1.0d}] run function infinity_cave:mechanics/frostpale_plant/scan/offhand

function infinity_cave:mechanics/frostpale_plant/scan/set with storage infinity_cave:frostpale scan