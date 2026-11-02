execute if block ~ ~ ~ #infinity_cave:all_but_air run function infinity_cave:mechanics/weapons/molten_hammer/hit_block
scoreboard players add #ic.distance ic.int 1

execute if score #ic.hit ic.int matches 0 if score #ic.distance ic.int matches ..15 positioned ^ ^ ^0.5 run function infinity_cave:mechanics/weapons/molten_hammer/ray

execute if score #ic.hit ic.int matches 0 if score #ic.distance ic.int matches 16.. run function infinity_cave:mechanics/weapons/molten_hammer/hit_air