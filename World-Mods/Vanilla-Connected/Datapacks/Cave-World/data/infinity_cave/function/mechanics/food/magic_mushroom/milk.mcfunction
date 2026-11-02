advancement revoke @s[advancements={infinity_cave:mechanics/food/magic_mushroom/milk=true}] only infinity_cave:mechanics/food/magic_mushroom/milk
advancement revoke @s[advancements={infinity_cave:mechanics/food/magic_mushroom/random=true}] only infinity_cave:mechanics/food/magic_mushroom/random

scoreboard players set @s ic.tagged 0

title @s actionbar 'The weird effect has subsided'
playsound entity.mooshroom.convert player @a[distance=..15] ~ ~ ~ 0.5 2 1

attribute @s scale modifier remove infinity_cave:magic_mushroom
attribute @s movement_speed modifier remove infinity_cave:magic_mushroom
attribute @s entity_interaction_range modifier remove infinity_cave:magic_mushroom
attribute @s block_interaction_range modifier remove infinity_cave:magic_mushroom
attribute @s jump_strength modifier remove infinity_cave:magic_mushroom
attribute @s step_height modifier remove infinity_cave:magic_mushroom
attribute @s gravity modifier remove infinity_cave:magic_mushroom
attribute @s fall_damage_multiplier modifier remove infinity_cave:magic_mushroom