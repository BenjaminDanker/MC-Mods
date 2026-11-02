scoreboard players set @s ic.thunder_charge 1
scoreboard players add @s ic.thunder_int 1

playsound entity.warden.heartbeat player @a[distance=..15] ~ ~ ~ 0.1 2 1
particle minecraft:electric_spark ~ ~1 ~ 0.5 0.5 0.5 0.7 2 force

execute if entity @s[scores={ic.thunder_int=41..80}] run function infinity_cave:mechanics/weapons/thunder_harpoon/charge_1
execute if entity @s[scores={ic.thunder_int=81..120}] run function infinity_cave:mechanics/weapons/thunder_harpoon/charge_2
execute if entity @s[scores={ic.thunder_int=121..160}] run function infinity_cave:mechanics/weapons/thunder_harpoon/charge_3
execute if entity @s[scores={ic.thunder_int=161..200}] run function infinity_cave:mechanics/weapons/thunder_harpoon/charge_4
execute if entity @s[scores={ic.thunder_int=201..}] run function infinity_cave:mechanics/weapons/thunder_harpoon/charge_5

advancement revoke @s only infinity_cave:mechanics/weapons/thunder_harpoon/charge
advancement revoke @s only infinity_cave:mechanics/weapons/thunder_harpoon/tick