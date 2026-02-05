
particle flame ^0.000 ^0.000 ^0.000 0 0 0 0 1 normal @a
particle flame ^0.001 ^0.008 ^0.005 0 0 0 0 1 normal @a
particle flame ^0.002 ^0.017 ^0.010 0 0 0 0 1 normal @a
particle flame ^0.005 ^0.025 ^0.014 0 0 0 0 1 normal @a
particle flame ^0.010 ^0.033 ^0.018 0 0 0 0 1 normal @a
particle flame ^0.015 ^0.042 ^0.020 0 0 0 0 1 normal @a
particle flame ^0.020 ^0.050 ^0.022 0 0 0 0 1 normal @a
particle flame ^0.027 ^0.058 ^0.022 0 0 0 0 1 normal @a

particle flash{color:[1.000,1.000,1.000,1.00]} ^0.027 ^0.058 ^0.022 0 0 0 0 1 normal @a

particle flame ~ ~ ~ 0 0 0 0.2 100 normal @a

playsound item.totem.use hostile @a[distance=..20] ~ ~ ~ 1 2 1
 
summon fireball ~ ~ ~ {ExplosionPower:3b,Invulnerable:1b,acceleration_power:2d,Motion:[0.0,-1.0,0.0]}

execute if block ~ ~ ~ air run setblock ~ ~-1 ~ magma_block