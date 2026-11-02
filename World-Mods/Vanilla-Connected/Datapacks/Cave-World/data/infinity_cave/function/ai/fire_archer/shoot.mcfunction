data modify storage infinity_cave:fire_archer data set from entity @s

execute store result storage infinity_cave:fire_archer int int 1 run loot spawn ~ ~-1000 ~ loot {pools:[{rolls:1,entries:[{type:"empty"}]},{rolls:1,entries:[{type:"minecraft:item",name:"minecraft:dirt",conditions:[{condition:"minecraft:random_chance",chance:0.2}]}]}]}

function infinity_cave:ai/fire_archer/list with storage infinity_cave:fire_archer data

execute on origin store result storage infinity_cave:fire_archer data.attack_damage double 1 run attribute @s attack_damage get

kill @s

function infinity_cave:ai/fire_archer/load_projectile with storage infinity_cave:fire_archer

function infinity_cave:ai/fire_archer/apply_data with storage infinity_cave:fire_archer data