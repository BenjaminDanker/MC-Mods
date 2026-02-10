particle minecraft:totem_of_undying ~ ~1 ~ 0 0 0 0.4 100 force
playsound minecraft:item.trident.return hostile @a[distance=..25] ~ ~ ~ 1 0.1 1

$data modify storage infinity_cave:mob data set from storage infinity_cave:molten_caves data.uncommon.mobs.$(int)

function infinity_cave:mobs/attributes with storage infinity_cave:mob data
function infinity_cave:mobs/offhand/get with storage infinity_cave:mob data

data modify storage infinity_cave:mob data.AngryAt set from entity @p[gamemode=!spectator] UUID
data modify storage infinity_cave:mob data.persistence set from storage infinity_cave:settings despawn.initial

function infinity_cave:mobs/spawn with storage infinity_cave:mob data

tp @s ~ ~-1000 ~