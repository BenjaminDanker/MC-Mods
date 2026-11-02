effect clear @s poison
effect clear @s weakness
effect clear @s mining_fatigue

clear @s music_disc_11[custom_data~{infinity_cave:{item:"cetirizine"}}] 1

effect give @s oozing 2 0 true

playsound entity.fox.eat player @s ~ ~ ~ 0.5 2 1

advancement revoke @s only infinity_cave:mechanics/cetirizine