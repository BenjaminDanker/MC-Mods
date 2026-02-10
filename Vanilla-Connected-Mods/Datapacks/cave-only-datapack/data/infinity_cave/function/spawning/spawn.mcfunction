execute if entity @s[scores={ic.progression=..99}] store result score #ic.tier ic.int run random value 1..700
execute if entity @s[scores={ic.progression=100..249}] store result score #ic.tier ic.int run random value 1..908
execute if entity @s[scores={ic.progression=250..499}] store result score #ic.tier ic.int run random value 200..950
execute if entity @s[scores={ic.progression=500..999}] store result score #ic.tier ic.int run random value 300..988
execute if entity @s[scores={ic.progression=1000..2499}] store result score #ic.tier ic.int run random value 400..1000
execute if entity @s[scores={ic.progression=2500..4999}] store result score #ic.tier ic.int run random value 600..1000
execute if entity @s[scores={ic.progression=5000..}] store result score #ic.tier ic.int run random value 600..1010

execute store result storage infinity_cave:settings spawn_spread.y_int int 1 run data get entity @s Pos[1] 0.95

function infinity_cave:spawning/roll_tier with storage infinity_cave:settings spawn_spread