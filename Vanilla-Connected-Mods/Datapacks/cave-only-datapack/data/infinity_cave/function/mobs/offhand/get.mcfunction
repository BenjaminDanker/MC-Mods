execute store result score #offhand ic.const run data get storage infinity_cave:mob data.offhand

execute store result storage infinity_cave:mob data.offhand_entries int 1 run scoreboard players operation #offhand ic.const -= #ic1 ic.const

function infinity_cave:mobs/offhand/roll with storage infinity_cave:mob data