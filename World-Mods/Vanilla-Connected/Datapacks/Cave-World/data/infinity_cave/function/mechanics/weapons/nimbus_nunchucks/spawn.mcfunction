$data merge entity @s {Item:{id:"seagrass"},NoGravity:true,Motion:$(motion)} 

$summon area_effect_cloud ~ ~ ~ {custom_particle:{type:"sneeze"},ReapplicationDelay:50,Duration:400,potion_contents:{custom_effects:$(active_effects)}}

ride @n[type=area_effect_cloud] mount @s
