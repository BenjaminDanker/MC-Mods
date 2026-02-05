execute on target run data modify storage infinity_cave:elements frozen_caves.1.freeze.pos set from entity @s Pos

execute store result score @s ic.uuid run time query gametime

execute store result score #compare ic.int run scoreboard players operation @s ic.uuid += #ic20 ic.const

data modify storage infinity_cave:elements frozen_caves.1.freeze.pos_x set from storage infinity_cave:elements frozen_caves.1.freeze.pos[0]

data modify storage infinity_cave:elements frozen_caves.1.freeze.pos_y set from storage infinity_cave:elements frozen_caves.1.freeze.pos[1]

data modify storage infinity_cave:elements frozen_caves.1.freeze.pos_z set from storage infinity_cave:elements frozen_caves.1.freeze.pos[2]

function infinity_cave:mobs/frozen_caves/elements/1/freeze/particles with storage infinity_cave:elements frozen_caves.1.freeze

schedule function infinity_cave:mobs/frozen_caves/elements/1/freeze/at 20t append