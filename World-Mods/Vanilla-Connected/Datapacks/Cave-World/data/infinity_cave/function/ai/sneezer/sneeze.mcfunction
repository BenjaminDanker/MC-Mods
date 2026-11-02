execute on vehicle run effect clear @s slowness

execute on vehicle at @s run function infinity_cave:ai/sneezer/recoil/set_motion

execute on vehicle run effect give @s slowness 1 1 true

execute on vehicle run rotate @s facing entity @p[gamemode=!spectator]

function infinity_cave:ai/sneezer/projectile/cloud