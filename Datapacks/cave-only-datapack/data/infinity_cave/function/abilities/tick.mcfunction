# magician
execute if entity @s[tag=ic.magician_blizzard] at @s run function infinity_cave:ai/magician/blizzard/tick
execute if entity @s[tag=ic.magician_fireball] at @s run function infinity_cave:ai/magician/fireball/tick

# sneezer ability
execute if entity @s[tag=ic.toxic_enemy] run function infinity_cave:ai/sneezer/toxic_cloud/animate

# fire ability
execute if entity @s[tag=ic.fire_spiral] run function infinity_cave:particles/fire_spiral/animate

# wind ability
execute if entity @s[tag=ic.tornado] run function infinity_cave:particles/tornado/animate