execute store result storage infinity_cave:frostpale scan.slot int 1 run scoreboard players add #inventory ic.int 1

$execute unless data storage infinity_cave:frostpale scan.player_data.Inventory[{Slot:$(slot)b}].components."minecraft:attribute_modifiers"[{type:"minecraft:tempt_range",id:"infinity_cave:id",amount:1.0d}] unless score #inventory ic.int matches 37.. run return run function infinity_cave:mechanics/frostpale_plant/scan/iterate with storage infinity_cave:frostpale scan

execute unless function infinity_cave:mechanics/frostpale_plant/scan/iterate run function infinity_cave:mechanics/frostpale_plant/scan/calculate