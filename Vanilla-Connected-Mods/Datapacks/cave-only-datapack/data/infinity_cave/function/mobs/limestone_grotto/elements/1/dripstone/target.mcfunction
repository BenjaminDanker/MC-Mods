execute on target run data modify storage infinity_cave:elements limestone_grotto.1.dripstone.pos set from entity @s Pos

execute store result score @s ic.uuid run time query gametime

execute store result score #compare ic.int run scoreboard players operation @s ic.uuid += #ic20 ic.const

data modify storage infinity_cave:elements limestone_grotto.1.dripstone.pos_x set from storage infinity_cave:elements limestone_grotto.1.dripstone.pos[0]

data modify storage infinity_cave:elements limestone_grotto.1.dripstone.pos_y set from storage infinity_cave:elements limestone_grotto.1.dripstone.pos[1]

execute store result score #temp_y ic.int run data get storage infinity_cave:elements limestone_grotto.1.dripstone.pos[1]

execute store result storage infinity_cave:elements limestone_grotto.1.dripstone.pos[1] int 1 run scoreboard players operation #temp_y ic.int += #ic8 ic.const

data modify storage infinity_cave:elements limestone_grotto.1.dripstone.pos_z set from storage infinity_cave:elements limestone_grotto.1.dripstone.pos[2]

function infinity_cave:mobs/limestone_grotto/elements/1/dripstone/particles with storage infinity_cave:elements limestone_grotto.1.dripstone

schedule function infinity_cave:mobs/limestone_grotto/elements/1/dripstone/at 20t append