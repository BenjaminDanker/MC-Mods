particle flash{color:[1.000,1.000,1.000,1.00]}

scoreboard players set #tmp ic.const 0

execute store result storage infinity_cave:geraldo int int 1 run loot spawn ~ ~-1000 ~ loot infinity_cave:technical/random

execute store result storage infinity_cave:geraldo amount int 1 run random value 3..8

execute if data storage infinity_cave:geraldo {int:3} run data modify storage infinity_cave:geraldo amount set value 1

function infinity_cave:ai/geraldo/gadgets/pool with storage infinity_cave:geraldo