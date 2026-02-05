particle explosion_emitter ~ ~ ~ 0 0 0 0.1 10 normal

execute store result storage infinity_cave:y_level int int 1 run data get entity @s Pos[1] 0.98

function infinity_cave:mechanics/rc/smoke_bomb/teleport with storage infinity_cave:y_level

advancement revoke @s only infinity_cave:mechanics/rc/smoke_bomb