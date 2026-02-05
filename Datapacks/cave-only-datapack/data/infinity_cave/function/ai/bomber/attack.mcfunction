execute if entity @p[distance=..5] store result storage infinity_cave:bomber motion float 0.01 run random value 25..50

execute if entity @p[distance=5.001..10] store result storage infinity_cave:bomber motion float 0.01 run random value 40..60

execute if entity @p[distance=10.001..20] store result storage infinity_cave:bomber motion float 0.01 run random value 80..160

execute if entity @s[tag=epic] run function infinity_cave:ai/bomber/tnt/tier/epic
execute if entity @s[tag=legendary] run function infinity_cave:ai/bomber/tnt/tier/legendary