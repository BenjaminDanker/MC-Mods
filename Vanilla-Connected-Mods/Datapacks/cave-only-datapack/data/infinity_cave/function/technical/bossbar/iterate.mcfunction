$data modify storage infinity_cave:boss_list temp set from storage infinity_cave:bossbar list[$(iterate)].id

execute store result storage infinity_cave:boss_list iterate int 1 run scoreboard players add .iterate ic.int 1

function infinity_cave:technical/bossbar/check with storage infinity_cave:boss_list

$execute if score .iterate ic.int matches $(amount).. run function infinity_cave:technical/bossbar/reset