execute on target run data modify storage infinity_cave:elements mushroom.1.size_spore.pos set from entity @s Pos

execute store result score @s ic.uuid run time query gametime

execute store result score #compare ic.int run scoreboard players operation @s ic.uuid += #ic60 ic.const

execute store result score #temp ic.int run random value 1..2

execute if score #temp ic.int matches 1 run data modify storage infinity_cave:elements mushroom.1.size_spore.effect set value 'shrink'
execute if score #temp ic.int matches 2 run data modify storage infinity_cave:elements mushroom.1.size_spore.effect set value 'enlarge'

data modify storage infinity_cave:elements mushroom.1.size_spore.pos_x set from storage infinity_cave:elements mushroom.1.size_spore.pos[0]

data modify storage infinity_cave:elements mushroom.1.size_spore.pos_y set from storage infinity_cave:elements mushroom.1.size_spore.pos[1]

data modify storage infinity_cave:elements mushroom.1.size_spore.pos_z set from storage infinity_cave:elements mushroom.1.size_spore.pos[2]

function infinity_cave:mobs/mushroom_gorge/elements/1/size_spore/particles with storage infinity_cave:elements mushroom.1.size_spore

schedule function infinity_cave:mobs/mushroom_gorge/elements/1/size_spore/at 60t append