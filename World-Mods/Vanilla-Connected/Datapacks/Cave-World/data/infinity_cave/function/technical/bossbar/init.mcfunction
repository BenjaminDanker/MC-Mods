execute store result score .boss_list ic.int run data get storage infinity_cave:bossbar list

execute unless score .iterate ic.int matches 0.. store result storage infinity_cave:boss_list iterate int 1 run scoreboard players set .iterate ic.int 0

execute store result storage infinity_cave:boss_list amount int 1 run scoreboard players operation .boss_list ic.int -= #ic1 ic.const

function infinity_cave:technical/bossbar/iterate with storage infinity_cave:boss_list