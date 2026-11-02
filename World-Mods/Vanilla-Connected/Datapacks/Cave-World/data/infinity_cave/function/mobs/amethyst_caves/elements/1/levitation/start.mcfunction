execute on passengers if entity @s[type=end_crystal] run particle minecraft:end_rod ~ ~1 ~ 0 0 0 1 100 normal

execute on target run data modify storage infinity_cave:elements amethyst.1.pos set from entity @s Pos

function infinity_cave:mobs/amethyst_caves/elements/1/levitation/summon with storage infinity_cave:elements amethyst.1