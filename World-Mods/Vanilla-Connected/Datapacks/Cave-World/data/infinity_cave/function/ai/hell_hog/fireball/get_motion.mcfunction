data modify storage ic:hell_hog motion set from entity @s Motion

kill @s

particle flash{color:[1.000,1.000,1.000,1.00]} ~ ~ ~

playsound item.mace.smash_ground hostile @a[distance=..50] ~ ~ ~ 2 2 1

function infinity_cave:ai/hell_hog/fireball/fireball with storage ic:hell_hog