data modify entity @s FallFlying set value 1b

execute in overworld positioned 0.0 0 0.0 summon marker run function infinity_cave:ai/bonk_diver/flying/apply_motion

rotate @s facing entity @p[gamemode=!spectator,gamemode=!creative]
tp @s @s
execute positioned ~ ~1 ~ facing entity @p[gamemode=!spectator,gamemode=!creative] eyes run function infinity_cave:ai/bonk_diver/flying/use

playsound minecraft:entity.firework_rocket.launch hostile @a[distance=..30] ~ ~ ~ 1 1 1

particle firework ~ ~ ~ 0 0 0 0.1 10 normal