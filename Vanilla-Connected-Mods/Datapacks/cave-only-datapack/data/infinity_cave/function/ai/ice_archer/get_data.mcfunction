kill @s

playsound item.trident.hit_ground hostile @a[distance=..25] ~ ~ ~ 1 0.5 1 

execute on origin store result storage infinity_cave:ice_archer strength int 1 run attribute @s attack_damage get 1 

function infinity_cave:ai/ice_archer/apply_data with storage infinity_cave:ice_archer