$scoreboard players set $difficulty ic.int $(difficulty)
$scoreboard players set $progression ic.int $(progression)

### Difficulty
execute if score $difficulty ic.int matches 1 run function infinity_cave:settings/difficulty/easy
execute if score $difficulty ic.int matches 2 run function infinity_cave:settings/difficulty/normal
execute if score $difficulty ic.int matches 3 run function infinity_cave:settings/difficulty/hard

### Progression
execute if score $progression ic.int matches 1 run data merge storage infinity_cave:settings {\
  progression: [\
    { "id": "1", "display": { "text": "Slow", "type": "text" } },\
    { "id": "2", "display": { "text": "Standard", "type": "text" } },\
    { "id": "3", "display": { "text": "Fast", "type": "text" } }\
  ]\
}

execute if score $progression ic.int matches 2 run data merge storage infinity_cave:settings {\
  progression: [\
    { "id": "2", "display": { "text": "Standard", "type": "text" } },\
    { "id": "3", "display": { "text": "Fast", "type": "text" } },\
    { "id": "1", "display": { "text": "Slow", "type": "text" } }\
  ]\
}

execute if score $progression ic.int matches 3 run data merge storage infinity_cave:settings {\
  progression: [\
    { "id": "3", "display": { "text": "Fast", "type": "text" } },\
    { "id": "1", "display": { "text": "Slow", "type": "text" } },\
    { "id": "2", "display": { "text": "Standard", "type": "text" } }\
  ]\
}

$data modify storage infinity_cave:settings frequency.initial set value $(frequency)
$scoreboard players set #spawn_frequency ic.int $(frequency)

$data modify storage infinity_cave:settings mob_cap.initial set value $(mob_cap)
$scoreboard players set #mob_cap ic.int $(mob_cap)

$data modify storage infinity_cave:settings ranged_damage.initial set value $(ranged_damage)

$data modify storage infinity_cave:settings spawn_spread.initial set value $(spawn_spread)
$scoreboard players set #spawn_spread ic.int $(spawn_spread)

$data modify storage infinity_cave:settings disable_spawn.initial set value $(disable_spawn)
$data modify storage infinity_cave:settings disable_jump_ai.initial set value $(disable_jump_ai)
$data modify storage infinity_cave:settings despawn.initial set value $(despawn)