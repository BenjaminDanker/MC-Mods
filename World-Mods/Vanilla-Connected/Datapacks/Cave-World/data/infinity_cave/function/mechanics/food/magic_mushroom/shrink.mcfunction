advancement revoke @s[advancements={infinity_cave:mechanics/food/magic_mushroom/shrink=true}] only infinity_cave:mechanics/food/magic_mushroom/shrink

scoreboard players set @s ic.tagged 1

title @s actionbar 'You have shrunk, drink Milk to undo.'

attribute @s scale modifier remove infinity_cave:magic_mushroom
attribute @s movement_speed modifier remove infinity_cave:magic_mushroom
attribute @s entity_interaction_range modifier remove infinity_cave:magic_mushroom
attribute @s block_interaction_range modifier remove infinity_cave:magic_mushroom
attribute @s jump_strength modifier remove infinity_cave:magic_mushroom
attribute @s step_height modifier remove infinity_cave:magic_mushroom
attribute @s fall_damage_multiplier modifier remove infinity_cave:magic_mushroom
attribute @s gravity modifier remove infinity_cave:magic_mushroom
 
execute store result storage infinity_cave:magic_mushroom size double 0.001 run random value -800..-200
execute store result storage infinity_cave:magic_mushroom speed double 0.0005 run random value 150..400
execute store result storage infinity_cave:magic_mushroom reach double 0.0005 run random value -1000..-500
execute store result storage infinity_cave:magic_mushroom block_reach double 0.0005 run random value -1000..-500
execute store result storage infinity_cave:magic_mushroom jump double 0.001 run random value 400..550
execute store result storage infinity_cave:magic_mushroom step_height double 0.001 run random value -250..-20
execute store result storage infinity_cave:magic_mushroom gravity double 0.001 run random value -500..-200
attribute @s fall_damage_multiplier modifier add infinity_cave:magic_mushroom -6 add_value

function infinity_cave:mechanics/food/magic_mushroom/randomize/apply with storage infinity_cave:magic_mushroom