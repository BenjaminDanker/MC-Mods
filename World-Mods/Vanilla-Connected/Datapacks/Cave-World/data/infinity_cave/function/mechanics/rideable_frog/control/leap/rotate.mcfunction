execute store result storage infinity_cave:rideable_frog leap_z double 0.05 run scoreboard players get @s ic.frog_leap
execute store result storage infinity_cave:rideable_frog leap_y double 0.025 run scoreboard players get @s ic.frog_leap

execute if score @s ic.frog_leap matches 5.. run function infinity_cave:mechanics/rideable_frog/control/leap/motion/set_motion

scoreboard players reset @s ic.frog_leap

execute on passengers run scoreboard players reset @s ic.frog_leap