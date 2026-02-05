execute on target run data modify storage infinity_cave:elements amethyst.1.pos set from entity @s Pos

execute store result score @s ic.uuid run time query gametime

execute store result score #compare ic.int run scoreboard players operation @s ic.uuid += #ic60 ic.const

execute store result score #temp_y ic.int run data get storage infinity_cave:elements amethyst.1.pos[1]

data modify storage infinity_cave:elements amethyst.1.pos_x set from storage infinity_cave:elements amethyst.1.pos[0]

data modify storage infinity_cave:elements amethyst.1.pos_y set from storage infinity_cave:elements amethyst.1.pos[1]

data modify storage infinity_cave:elements amethyst.1.pos_z set from storage infinity_cave:elements amethyst.1.pos[2]

execute store result storage infinity_cave:elements amethyst.1.pos[1] int 1 run scoreboard players operation #temp_y ic.int -= 2 ic.const

data modify storage infinity_cave:elements amethyst.1.owner set from entity @s UUID

execute on passengers if entity @s[type=end_crystal] run data modify entity @s beam_target set from storage infinity_cave:elements amethyst.1.pos

function infinity_cave:mobs/amethyst_caves/elements/1/explosion/particles with storage infinity_cave:elements amethyst.1

schedule function infinity_cave:mobs/amethyst_caves/elements/1/explosion/at 60t append