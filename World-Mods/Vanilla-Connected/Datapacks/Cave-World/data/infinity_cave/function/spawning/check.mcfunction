advancement revoke @s only infinity_cave:spawning/check

scoreboard players set @s ic.mob_count 0

# gets player count in a radius of 50 blocks
scoreboard players set #count ic.int 0
execute store result score #player_count ic.int as @a[distance=..50] run scoreboard players operation #count ic.int += #ic1 ic.const

# gets mob count in a radius of 50 blocks
scoreboard players set #count ic.int 0
scoreboard players set #mob_count ic.int 0
execute store result score #mob_count ic.int as @e[type=#infinity_cave:aggro,team=ic.mob_team,distance=..50] run scoreboard players operation #count ic.int += #ic1 ic.const

scoreboard players operation #mob_count ic.int *= #player_count ic.int

scoreboard players operation @s ic.mob_count = #mob_count ic.int

execute if score #mob_count ic.int >= #mob_cap ic.int run return fail

function infinity_cave:spawning/spawn