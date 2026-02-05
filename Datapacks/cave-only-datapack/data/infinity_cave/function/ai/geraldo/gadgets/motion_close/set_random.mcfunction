$execute store result storage infinity_cave:geraldo Motion[0] double 0.001 run data get storage infinity_cave:geraldo Motion[0] $(tmp_x)
$execute store result storage infinity_cave:geraldo Motion[1] double 0.001 run data get storage infinity_cave:geraldo Motion[1] $(tmp_y)
$execute store result storage infinity_cave:geraldo Motion[2] double 0.001 run data get storage infinity_cave:geraldo Motion[2] $(tmp_z)

data modify entity @s Motion set from storage infinity_cave:geraldo Motion