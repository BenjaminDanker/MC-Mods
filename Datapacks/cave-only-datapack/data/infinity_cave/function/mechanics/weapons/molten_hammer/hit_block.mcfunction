scoreboard players set #ic.hit ic.int 1

particle explosion_emitter ~ ~ ~ 0 0 0 0 1 force
particle lava ~ ~ ~ 0.5 0 0.5 0.1 10 force
particle flame ~ ~ ~ 1 1 1 0.1 100 force

execute as @e[type=!#infinity_cave:non_living,tag=!ic.molten_hammer,distance=..8] run damage @s 35 infinity_cave:magic_bypass by @p[tag=ic.molten_hammer] from @p[tag=ic.molten_hammer]

playsound item.mace.smash_ground_heavy player @a[distance=..15] ~ ~ ~ 0.4 0.7 1