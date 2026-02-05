particle flame ~ ~0.1 ~ 0.15 0.15 0.15 0.1 3 force

execute if entity @s[tag=ic.rare_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 1 run damage @p[distance=..3] 4 generic at ~ ~ ~
execute if entity @s[tag=ic.rare_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 2 run damage @p[distance=..3] 8 generic at ~ ~ ~
execute if entity @s[tag=ic.rare_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 3 run damage @p[distance=..3] 12 generic at ~ ~ ~

execute if entity @s[tag=ic.epic_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 1 run damage @p[distance=..3] 8 generic at ~ ~ ~
execute if entity @s[tag=ic.epic_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 2 run damage @p[distance=..3] 12 generic at ~ ~ ~
execute if entity @s[tag=ic.epic_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 3 run damage @p[distance=..3] 16 generic at ~ ~ ~

execute if entity @s[tag=ic.legendary_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 1 run damage @p[distance=..3] 12 generic at ~ ~ ~
execute if entity @s[tag=ic.legendary_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 2 run damage @p[distance=..3] 18 generic at ~ ~ ~
execute if entity @s[tag=ic.legendary_fire] if predicate {condition:"minecraft:entity_properties",entity:"this",predicate:{periodic_tick:8}} if score $difficulty ic.int matches 3 run damage @p[distance=..3] 24 generic at ~ ~ ~