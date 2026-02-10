attribute @s minecraft:explosion_knockback_resistance modifier add ic.ignore_kb_res -100 add_value

data modify storage infinity_cave:motionatana rotation set from entity @s Rotation[0]

tp @s @s

execute summon marker run function infinity_cave:mechanics/weapons/katana/set_rot

tag @s add ic.explosion_resistance

schedule function infinity_cave:mechanics/weapons/katana/as 2t append