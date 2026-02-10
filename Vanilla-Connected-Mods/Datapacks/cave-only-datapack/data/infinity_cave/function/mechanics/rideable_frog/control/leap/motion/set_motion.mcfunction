playsound infinity_cave:sound_effect.frog_boing player @a[distance=..30] ~ ~ ~ 0.05 2 0

particle cloud ~ ~-0.25 ~ 0.25 0.25 0.25 0.1 8 normal

execute in overworld positioned 0.0 0 0.0 summon marker run function infinity_cave:mechanics/rideable_frog/control/leap/motion/apply_motion with storage infinity_cave:rideable_frog

data modify entity @s Motion set from storage infinity_cave:motion Motion