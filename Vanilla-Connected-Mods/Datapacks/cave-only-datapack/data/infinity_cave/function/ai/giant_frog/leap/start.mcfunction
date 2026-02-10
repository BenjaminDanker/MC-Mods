execute on target run rotate @n[type=frog] facing entity @s

particle item_slime ~ ~ ~ 0.5 0.25 0.5 0.2 20 normal

playsound infinity_cave:sound_effect.frog_boing hostile @a[distance=..30] ~ ~ ~ 0.5 1.5

execute on vehicle at @s run function infinity_cave:ai/giant_frog/leap/set_motion