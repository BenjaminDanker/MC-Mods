# Incendium's "scroll of returning" needs one in.world_spawn marker. Keep one, remove the copies; every 30 seconds.
execute unless entity @e[type=minecraft:marker,tag=in.world_spawn,tag=markerfix.keep] run tag @e[type=minecraft:marker,tag=in.world_spawn,limit=1,sort=arbitrary] add markerfix.keep
kill @e[type=minecraft:marker,tag=in.world_spawn,tag=!markerfix.keep]
schedule function markerfix:tidy 600t replace
