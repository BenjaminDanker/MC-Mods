execute on target at @s run summon area_effect_cloud ~ ~ ~ {custom_particle:{type:"dust_pillar",block_state:"minecraft:amethyst_block"},ReapplicationDelay:60,Radius:0.5f,RadiusPerTick:0.05f,RadiusOnUse:0f,Duration:120,DurationOnUse:0,Age:-10000,WaitTime:-10000,potion_contents:{custom_effects:[{id:"minecraft:levitation",amplifier:40,duration:10,show_particles:1b,show_icon:1b,ambient:1b}]},Passengers:[{id:"minecraft:area_effect_cloud",custom_particle:{type:"end_rod"},ReapplicationDelay:0,Radius:0.5f,RadiusPerTick:0.05f,RadiusOnUse:0f,Duration:120,DurationOnUse:0,Age:-10000,WaitTime:-10000}]}

$particle trail{color:[0.816,0.212,1.000],target:$(pos),duration:100} ~ ~ ~ 1.5 1.5 1.5 0.4 200 normal

playsound entity.evoker.prepare_attack hostile @a[distance=..30] ~ ~ ~ 1 0 1