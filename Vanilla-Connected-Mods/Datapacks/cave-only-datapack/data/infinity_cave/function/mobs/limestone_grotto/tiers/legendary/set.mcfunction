particle end_rod ~ ~ ~ 0 0 0 0.2 500 force
playsound minecraft:block.end_portal.spawn hostile @a[distance=..100] ~ ~ ~ 1 0.75 1 

$data modify storage infinity_cave:mob data set from storage infinity_cave:limestone_grotto data.legendary.mobs.$(int)

function infinity_cave:mobs/attributes with storage infinity_cave:mob data
function infinity_cave:mobs/offhand/get with storage infinity_cave:mob data

data modify storage infinity_cave:mob data.AngryAt set from entity @p[gamemode=!spectator] UUID
data modify storage infinity_cave:mob data.persistence set from storage infinity_cave:settings despawn.initial

function infinity_cave:mobs/spawn with storage infinity_cave:mob data

tp @s ~ ~-1000 ~