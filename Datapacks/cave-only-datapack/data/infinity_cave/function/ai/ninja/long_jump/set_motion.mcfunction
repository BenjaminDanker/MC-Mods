execute in overworld positioned 0.0 0 0.0 summon marker run function infinity_cave:ai/ninja/long_jump/apply_motion

tp @s @s

execute positioned ~ ~1 ~ facing entity @p[gamemode=!spectator] eyes run function infinity_cave:ai/ninja/long_jump/use

tag @s add ic.ninja_leap

playsound item.trident.throw hostile @a[distance=..30] ~ ~ ~ 1 0.25 1