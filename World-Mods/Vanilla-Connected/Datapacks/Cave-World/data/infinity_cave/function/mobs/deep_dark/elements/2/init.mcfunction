function infinity_cave:technical/database/fetch_uuid/generate
execute store result storage infinity_cave:main health int 1 run data get entity @s Health
data merge storage infinity_cave:main {color:"purple",style:"notched_20"}
function infinity_cave:technical/bossbar/get_data with storage infinity_cave:main

scoreboard players set @s ic.tagged 1

execute if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{passenger:{}}} on passengers run return run function infinity_cave:mobs/deep_dark/elements/2/passenger