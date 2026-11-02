advancement revoke @s only infinity_cave:mechanics/frostpale_plant/detect

data modify storage infinity_cave:frostpale scan.player_data set from entity @s

execute store result storage infinity_cave:frostpale scan.slot int 1 run scoreboard players set #inventory ic.int 0

function infinity_cave:mechanics/frostpale_plant/scan/iterate with storage infinity_cave:frostpale scan