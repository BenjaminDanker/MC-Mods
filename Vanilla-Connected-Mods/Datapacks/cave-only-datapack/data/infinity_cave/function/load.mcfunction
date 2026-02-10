scoreboard players set @s ic.sonic_charge 0

scoreboard players set @s ic.thunder_charge 0

scoreboard players set @s ic.repel 36000

execute store result score @s ic.uuid run data get entity @s UUID[0]

function infinity_cave:technical/hit_match/player_hurt_entity/setup/setup_id

execute store result score @s ic.progression run scoreboard players get @s ic.progression