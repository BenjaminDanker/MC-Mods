$execute store result storage infinity_cave:attributes health double $(health_factor) run random value 60..120
$execute store result storage infinity_cave:attributes attack_damage double $(attack_damage_factor) run random value 60..120
$execute store result storage infinity_cave:attributes speed double $(speed_factor) run random value 50..65
$execute store result storage infinity_cave:attributes attack_knockback double $(attack_knockback_factor) run random value 60..120
$execute store result storage infinity_cave:attributes knockback_resistance double $(knockback_resistance) run random value 10..30
$execute store result storage infinity_cave:attributes scale double $(scale_factor) run random value 80..120

data modify storage infinity_cave:mob data merge from storage infinity_cave:attributes