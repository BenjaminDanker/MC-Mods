data modify entity @s FallFlying set value 0b

execute in overworld positioned 0.0 0 0.0 summon marker run function infinity_cave:ai/bonk_diver/ground_pound/apply_motion

scoreboard players set @s ic.ai 1

execute positioned ~ ~1 ~ facing entity @p[gamemode=!spectator,gamemode=!creative] eyes run function infinity_cave:ai/bonk_diver/ground_pound/use