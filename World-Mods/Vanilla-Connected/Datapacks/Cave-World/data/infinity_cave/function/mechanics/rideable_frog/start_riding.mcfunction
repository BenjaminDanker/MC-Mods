advancement revoke @s only infinity_cave:mechanics/rideable_frog/start_riding

tag @s add ic.riding_frog

advancement revoke @s only infinity_cave:mechanics/rideable_frog/dismount

tag @s add ic.right_clicked

execute as @e[type=interaction,distance=..10] on vehicle store success score @s ic.uuid on passengers run data modify entity @s interaction.player set from entity @p[tag=ic.right_clicked] UUID 
 
execute as @n[type=frog,scores={ic.uuid=0}] run function infinity_cave:mechanics/rideable_frog/success

tag @s remove ic.right_clicked

attribute @s fall_damage_multiplier modifier add infinity_cave:ridable_frog -1 add_value
attribute @s camera_distance modifier add infinity_cave:ridable_frog 2 add_value