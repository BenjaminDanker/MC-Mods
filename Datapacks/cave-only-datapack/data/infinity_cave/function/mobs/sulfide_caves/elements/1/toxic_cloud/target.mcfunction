execute on target run data modify storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos set from entity @s Pos

execute store result score @s ic.uuid run time query gametime

execute store result score #compare ic.int run scoreboard players operation @s ic.uuid += #ic20 ic.const

data modify storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos_x set from storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos[0]

data modify storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos_y set from storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos[1]

data modify storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos_z set from storage infinity_cave:elements sulfide_caves.1.toxic_cloud.pos[2]

function infinity_cave:mobs/sulfide_caves/elements/1/toxic_cloud/particles with storage infinity_cave:elements sulfide_caves.1.toxic_cloud

schedule function infinity_cave:mobs/sulfide_caves/elements/1/toxic_cloud/at 20t append