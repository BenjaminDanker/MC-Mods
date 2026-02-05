execute store result storage infinity_cave:randomized_int int double 0.02 run random value -8..8

execute in overworld positioned 0.0 0 0.0 summon marker run function infinity_cave:ai/jump/apply_y with storage infinity_cave:randomized_int

execute positioned ~ ~1 ~ facing entity @p eyes run function infinity_cave:ai/jump/motion

playsound infinity_cave:sound_effect.frog_boing hostile @a[distance=..15] ~ ~ ~ 0.2 2 1 