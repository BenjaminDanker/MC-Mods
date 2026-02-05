effect clear @s darkness
effect clear @s blindness

clear @s music_disc_11[custom_data={infinity_cave:{item:"light_orb"}}] 1

playsound block.note_block.chime player @s ~ ~ ~ 0.2 1 1

advancement revoke @s only infinity_cave:mechanics/light_orb