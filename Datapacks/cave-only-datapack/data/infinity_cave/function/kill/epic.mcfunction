scoreboard players add @s ic.epic_killed 1

execute if score $progression ic.int matches 1 run scoreboard players add @a[distance=..100] ic.progression 25

execute if score $progression ic.int matches 2 run scoreboard players add @a[distance=..100] ic.progression 50

execute if score $progression ic.int matches 3 run scoreboard players add @a[distance=..100] ic.progression 150

advancement revoke @s only infinity_cave:kill/epic