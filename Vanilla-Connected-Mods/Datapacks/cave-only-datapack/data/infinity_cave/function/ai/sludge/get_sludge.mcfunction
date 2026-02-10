advancement revoke @s only infinity_cave:abilities/generic/sludge

execute store result storage ic:sludge damage int 1 run data get entity @s active_effects[{id:"minecraft:infested"}].amplifier

function infinity_cave:ai/sludge/damage with storage ic:sludge