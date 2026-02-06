execute if score $progression ic.int matches 1 run scoreboard players add @a[distance=..100] ic.progression 50

execute if score $progression ic.int matches 2 run scoreboard players add @a[distance=..100] ic.progression 100

execute if score $progression ic.int matches 3 run scoreboard players add @a[distance=..100] ic.progression 250

scoreboard players add @s ic.legendary_killed 1

execute unless entity @s[tag=mpds_cave_defeated] run function infinity_cave:mpds/cave_defeat

tellraw @a [{"selector":"@s","color":"gold"},{"text":" has slain a Legendary mob!","color":"yellow"}]

advancement revoke @s only infinity_cave:kill/legendary