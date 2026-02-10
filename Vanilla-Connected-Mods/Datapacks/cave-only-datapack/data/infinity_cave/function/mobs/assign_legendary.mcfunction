execute store result score #temp ic.int run random value 1..2

$execute if score #temp ic.int matches 1 run return run item modify entity @s armor.feet {function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/$(biome)/1":1}}

$execute if score #temp ic.int matches 2 run return run item modify entity @s armor.feet {function:"set_enchantments",enchantments:{"infinity_cave:technical/elements/$(biome)/2":1}}
