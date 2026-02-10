item modify entity @s weapon.offhand infinity_cave:curse_remove

clear @s[gamemode=!creative] music_disc_5[custom_data~{"infinity_cave":{item:"curse_remover"}}] 1

execute at @s run function infinity_cave:mechanics/rc/curse_remover/particles

xp add @s -15 levels

playsound block.respawn_anchor.deplete player @s ~ ~ ~ 0.2 2 1