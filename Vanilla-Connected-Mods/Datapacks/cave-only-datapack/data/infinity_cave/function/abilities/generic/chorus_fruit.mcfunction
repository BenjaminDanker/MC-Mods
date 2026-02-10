advancement revoke @s only infinity_cave:abilities/generic/chorus_fruit

execute store result storage infinity_cave:y_level int int 1 run data get entity @s Pos[1] 0.95

execute as @e[type=#infinity_cave:mobs,nbt={HurtTime:10s}] at @s run function infinity_cave:abilities/generic/chorus_tp with storage infinity_cave:y_level