data modify entity @s Rotation[0] set from storage infinity_cave:motionatana rotation

execute at @s run summon armor_stand ^ ^1.5 ^-0.4 {Invisible:1b,equipment:{feet:{id:"minecraft:acacia_button",count:1,components:{"minecraft:enchantments":{"infinity_cave:technical/motion":1}}}}}

kill @s