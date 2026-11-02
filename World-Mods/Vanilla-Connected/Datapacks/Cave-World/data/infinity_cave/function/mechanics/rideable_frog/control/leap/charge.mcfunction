data modify entity @s Rotation set from entity @p Rotation

execute store result storage infinity_cave:rideable_frog pitch double 0.015 run scoreboard players add @s ic.frog_leap 1

execute if score @s ic.frog_leap matches 100.. run scoreboard players set @s ic.frog_leap 100

execute on passengers run function infinity_cave:mechanics/rideable_frog/control/leap/bar with storage infinity_cave:rideable_frog