$execute at $(temp) if loaded ~ ~ ~ store result bossbar infinity_cave:$(temp) value run data get entity $(temp) Health 1

$execute at $(temp) run function infinity_cave:technical/bossbar/visibility with storage infinity_cave:boss_list

$execute unless entity $(temp) run bossbar remove infinity_cave:$(temp)

$execute unless entity $(temp) run data remove storage infinity_cave:bossbar list[{"id":"$(temp)"}]

$execute if score .iterate ic.int matches ..$(amount) run return run function infinity_cave:technical/bossbar/iterate with storage infinity_cave:boss_list