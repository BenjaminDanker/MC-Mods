data modify entity @s Rotation set from entity @p Rotation

execute if predicate infinity_cave:player/ground if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{movement:{speed:{max:2}}}} \
run function infinity_cave:mechanics/rideable_frog/control/forward/motion/set_motion

execute if block ~ ~ ~ water \
run function infinity_cave:mechanics/rideable_frog/control/forward/motion_water/set_motion