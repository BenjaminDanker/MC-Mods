$execute positioned $(pos_x) $(pos_y) $(pos_z) run summon tnt ~ ~ ~ {fuse:0,explosion_power:4,owner:$(owner),block_state:{Name:"minecraft:air"}}

execute on passengers if entity @s[type=end_crystal] run data remove entity @s beam_target