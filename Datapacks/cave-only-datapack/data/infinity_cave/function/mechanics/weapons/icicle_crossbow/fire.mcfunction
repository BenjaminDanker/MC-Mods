data modify storage ic:icicle_crossbow data set from entity @s

execute store result storage ic:icicle_crossbow data.Motion[0] double 0.001 run data get storage ic:icicle_crossbow data.Motion[0] 1250
execute store result storage ic:icicle_crossbow data.Motion[1] double 0.001 run data get storage ic:icicle_crossbow data.Motion[1] 1250
execute store result storage ic:icicle_crossbow data.Motion[2] double 0.001 run data get storage ic:icicle_crossbow data.Motion[2] 1250

kill @s[type=arrow]

function infinity_cave:mechanics/weapons/icicle_crossbow/projectile with storage ic:icicle_crossbow

execute anchored eyes run particle snowflake ^ ^ ^ 0.1 0.1 0.1 0 10 force

playsound entity.stray.death player @a[distance=..15] ~ ~ ~ 0.5 2 1