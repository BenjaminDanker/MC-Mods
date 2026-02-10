execute on origin store result storage infinity_cave:chemist strength int 0.5 run attribute @s attack_damage get

tag @s add ic.chemist_potion

data modify storage infinity_cave:chemist motion set from entity @s Motion

execute store result storage infinity_cave:chemist motion[0] double 0.001 run data get storage infinity_cave:chemist motion[0] 2000
execute store result storage infinity_cave:chemist motion[1] double 0.001 run data get storage infinity_cave:chemist motion[1] 2000
execute store result storage infinity_cave:chemist motion[2] double 0.001 run data get storage infinity_cave:chemist motion[2] 2000

kill @s

playsound block.brewing_stand.brew hostile @a[distance=..30] ~ ~ ~ 0.4 1 1

function infinity_cave:ai/chemist/bomb with storage infinity_cave:chemist