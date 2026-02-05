tag @s remove ic.marker
scoreboard players set @s ic.animate 80

summon area_effect_cloud ~ ~0.25 ~ {custom_particle:{type:"flash"},ReapplicationDelay:0,Radius:0.5f,RadiusPerTick:0f,RadiusOnUse:0f,Duration:80,DurationOnUse:0,Age:0,WaitTime:0}