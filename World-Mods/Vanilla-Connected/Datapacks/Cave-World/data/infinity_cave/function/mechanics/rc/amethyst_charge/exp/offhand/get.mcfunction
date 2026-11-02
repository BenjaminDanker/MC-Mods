execute if entity @s[level=..5] run return run function infinity_cave:mechanics/rc/amethyst_charge/exp/fail

data modify storage infinity_cave:amethyst_charge amount set from entity @s XpTotal
execute store result storage infinity_cave:amethyst_charge levels int 1 run data get entity @s XpLevel

playsound block.respawn_anchor.charge player @s ~ ~ ~ 0.5 2 1

# Need this to set player's XpTotal to 0
xp add @s -2147483648 points

function infinity_cave:mechanics/rc/amethyst_charge/exp/offhand/set with storage infinity_cave:amethyst_charge