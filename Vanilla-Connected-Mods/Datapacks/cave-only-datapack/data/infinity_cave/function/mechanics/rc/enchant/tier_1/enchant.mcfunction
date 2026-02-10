item modify entity @s[predicate=infinity_cave:enchantlet/enchantable] weapon.offhand infinity_cave:enchants/tier_1 

xp add @s -72 points

clear @s[gamemode=!creative] music_disc_5[custom_data~{"infinity_cave":{item:"enchantlet",tier:1b}}] 1

execute at @s run function infinity_cave:mechanics/rc/enchant/tier_1/particles

playsound block.enchantment_table.use player @s ~ ~ ~ 0.5 2 1