$summon $(mob) ~ ~ ~ {Age:0,CustomNameVisible:1b, \ 
DeathLootTable:"infinity_cave:$(biome)/$(tier)/$(id)",\ 
Team:"ic.mob_team", \
IsImmuneToZombification:1b, \
variant:"minecraft:cold", \
PersistenceRequired:$(persistence), \
LeftHanded:0b, \
Tame:1b, \
AngerTime:99999,\
Silent:$(silent), \
drop_chances:{mainhand:0.000,offhand:0.000,feet:0.000,legs:0.000,chest:0.000,head:0.000}, \
CanPickUpLoot:0b, \
Brain:{memories:{"minecraft:dig_cooldown":{value:{},ttl:100000L}}}, \
Health:9999f,Fuse:32767,Size:4,IsImmuneToZombification:1b,AngerTime:99999, \
Tags:[$(tags),$(tier)], \
CustomName:[$(prefix),"$(name)"], \
equipment:{mainhand:$(mainhand),offhand:$(offhand),feet:$(feet),legs:$(legs),chest:$(chest),head:$(head)}, \ 
active_effects:$(effects), \
Passengers:$(passengers), \
attributes:[ \
{id:max_health,base:$(health)}, \
{id:attack_knockback,base:$(attack_knockback)}, \
{id:movement_speed,base:$(speed)}, \
{id:attack_damage,base:$(attack_damage)}, \
{id:knockback_resistance,base:$(knockback_resistance)}, \
{id:scale,base:$(scale)}, \
{id:follow_range,base:64}, \
{id:water_movement_efficiency,base:2}, \
{id:tempt_range,base:64}, \
{id:flying_speed,base:0.15}, \
{id:step_height,base:1}, \
{id:safe_fall_distance,base:8}, \
{id:spawn_reinforcements,base:0}]}

# Bossbar
execute as @n[team=ic.mob_team] at @s run function infinity_cave:mobs/as with storage infinity_cave:mob data