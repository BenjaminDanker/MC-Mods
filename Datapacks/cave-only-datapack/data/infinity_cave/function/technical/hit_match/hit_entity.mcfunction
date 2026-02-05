tag @s add ic.target

execute at @s if entity @a[scores={ic.sulfide_poison=0},distance=..10,limit=1] run function infinity_cave:mechanics/armor/sulfide_hm_attack

scoreboard players set #sulfide_poison ic.int 0

tag @s remove ic.target
