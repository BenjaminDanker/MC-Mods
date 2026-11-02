execute if entity @s[scores={ic.thunder_charge=0}] if items entity @s weapon.* netherite_sword[custom_data~{infinity_cave:{item:"thunder_harpoon"}}] run function infinity_cave:mechanics/weapons/thunder_harpoon/launch

execute if entity @s[scores={ic.thunder_charge=0}] run scoreboard players set @s ic.thunder_int 0

advancement revoke @s[scores={ic.thunder_charge=1..}] only infinity_cave:mechanics/weapons/thunder_harpoon/tick

scoreboard players set @s ic.thunder_charge 0