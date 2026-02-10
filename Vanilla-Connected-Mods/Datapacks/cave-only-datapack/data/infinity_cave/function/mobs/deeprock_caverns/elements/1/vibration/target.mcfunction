execute on target run data modify storage infinity_cave:elements deeprock_caverns.1.vibration.pos set from entity @s Pos

execute store result score @s ic.uuid run time query gametime

execute store result score #compare ic.int run scoreboard players operation @s ic.uuid += #ic60 ic.const

data modify storage infinity_cave:elements deeprock_caverns.1.vibration.pos_x set from storage infinity_cave:elements deeprock_caverns.1.vibration.pos[0]

data modify storage infinity_cave:elements deeprock_caverns.1.vibration.pos_y set from storage infinity_cave:elements deeprock_caverns.1.vibration.pos[1]

data modify storage infinity_cave:elements deeprock_caverns.1.vibration.pos_z set from storage infinity_cave:elements deeprock_caverns.1.vibration.pos[2]

function infinity_cave:mobs/deeprock_caverns/elements/1/vibration/particles with storage infinity_cave:elements deeprock_caverns.1.vibration

schedule function infinity_cave:mobs/deeprock_caverns/elements/1/vibration/at 60t append