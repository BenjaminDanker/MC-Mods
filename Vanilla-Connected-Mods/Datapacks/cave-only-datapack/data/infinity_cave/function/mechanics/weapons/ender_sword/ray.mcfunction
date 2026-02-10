execute if block ~ ~ ~ #infinity_cave:all_but_air run function infinity_cave:mechanics/weapons/ender_sword/hit_block

scoreboard players add #ic.distance ic.int 1

execute if score #ic.hit ic.int matches 0 if score #ic.distance ic.int matches ..40 positioned ^ ^ ^0.5 run function infinity_cave:mechanics/weapons/ender_sword/ray

execute if score #ic.distance ic.int matches 41.. run function infinity_cave:mechanics/weapons/ender_sword/hit_block