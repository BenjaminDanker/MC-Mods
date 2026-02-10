kill @s

summon frog ~ ~ ~ {variant:"infinity_cave:rideable_frog",active_effects:[{id:"minecraft:regeneration",amplifier:0,duration:-1,show_particles:0b,show_icon:0b,ambient:1b},{id:"minecraft:resistance",amplifier:1,duration:-1,show_particles:0b,show_icon:0b,ambient:1b}],Passengers:[{id:"minecraft:interaction",height:0.26f}],equipment:{saddle:{id:"minecraft:acacia_log",count:1,components:{"minecraft:enchantments":{"infinity_cave:technical/rideable_frog":1}}}},attributes:[{id:"minecraft:scale",base:2}]}

playsound entity.frog.death player @a[distance=..15]
particle flash{color:[1.000,1.000,1.000,1.00]}