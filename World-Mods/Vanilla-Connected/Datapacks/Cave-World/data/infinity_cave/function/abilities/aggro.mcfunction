data modify entity @s AngryAt set from entity @p[gamemode=!spectator,gamemode=!creative] UUID

execute if score $disable_block_break_ai ic.int matches 0 at @s[type=#infinity_cave:big] positioned ~ ~-2 ~ if entity @p[distance=..1] run fill ~-1 ~1 ~1 ~1 ~1 ~-1 air replace #infinity_cave:all_but_unbreakable

execute if entity @s[type=#infinity_cave:leapers,predicate=infinity_cave:target] at @s run function infinity_cave:abilities/slime/leap

execute if entity @s[predicate=infinity_cave:percent/50,predicate=infinity_cave:target,predicate=infinity_cave:firework_offhand] if entity @p[distance=5..30] run function infinity_cave:abilities/rocket_ride/start