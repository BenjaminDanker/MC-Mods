execute if entity @s[scores={ic.thunder_int=121}] run playsound minecraft:entity.evoker.cast_spell player @a[distance=..15] ~ ~ ~ 1 1.5 1
execute if entity @s[scores={ic.thunder_int=121}] run particle minecraft:firework ~ ~1 ~ 0.3 0.3 0.3 0.75 60 force
particle minecraft:electric_spark ~ ~1 ~ 0.35 0.35 0.35 2 8 force