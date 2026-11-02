execute at @s run function infinity_cave:mechanics/weapons/nimbus_nunchucks/particles

data modify storage infinity_cave:nimbus_nunchucks active_effects set value {}
data modify storage infinity_cave:nimbus_nunchucks active_effects set from entity @s active_effects

data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:regeneration"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:resistance"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:speed"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:strength"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:fire_resistance"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:water_breathing"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:night_vision"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:jump_boost"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:absorption"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:conduit_power"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:health_boost"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:glowing"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:dolphins_grace"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:haste"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:luck"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:saturation"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:hero_of_the_village"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:infested"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:unluck"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:trial_omen"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:bad_omen"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:oozing"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:weaving"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:slow_falling"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:instant_damage"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:instant_health"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:wind_charged"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:invisibility"}]
data remove storage infinity_cave:nimbus_nunchucks active_effects[{id:"minecraft:bad_luck"}]

function infinity_cave:mechanics/weapons/nimbus_nunchucks/clear_bad_effects

execute summon marker positioned 0.0 0.0 0.0 run function infinity_cave:mechanics/weapons/nimbus_nunchucks/motion

execute anchored eyes positioned ^ ^ ^ anchored feet summon snowball run function infinity_cave:mechanics/weapons/nimbus_nunchucks/spawn with storage infinity_cave:nimbus_nunchucks