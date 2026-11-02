particle flash{color:[1.000,1.000,1.000,1.00]} ~ ~ ~ 0.3 0.3 0.3 0.6 20 force
particle witch ~ ~ ~ 0 0 0 1.6 500 force
playsound minecraft:entity.warden.sonic_boom hostile @a[distance=..25] ~ ~ ~ 0.75 0.25 1

$data modify storage infinity_cave:mob data set from storage infinity_cave:sulfide_caves data.epic.mobs.$(int)

function infinity_cave:mobs/attributes with storage infinity_cave:mob data
function infinity_cave:mobs/offhand/get with storage infinity_cave:mob data

data modify storage infinity_cave:mob data.AngryAt set from entity @p[gamemode=!spectator] UUID
data modify storage infinity_cave:mob data.persistence set from storage infinity_cave:settings despawn.initial

function infinity_cave:mobs/spawn with storage infinity_cave:mob data

tp @s ~ ~-1000 ~