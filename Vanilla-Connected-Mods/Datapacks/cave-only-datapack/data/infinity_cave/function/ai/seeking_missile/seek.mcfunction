execute at @s anchored eyes facing entity @p[gamemode=!spectator] eyes positioned ^ ^ ^1 rotated as @s positioned ^ ^ ^5 facing entity @s eyes facing ^ ^ ^-1 positioned as @s run tp @s ^ ^ ^0.5 ~ ~

execute if entity @a[distance=..1] run function infinity_cave:ai/seeking_missile/explode