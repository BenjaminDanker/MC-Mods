# Loads only for the first time

execute store result score $difficulty ic.int run difficulty

data merge storage infinity_cave:settings {\
  difficulty: [\
    { "id": "1", "display": { "text": "Easy", "type": "text" } },\
    { "id": "2", "display": { "text": "Normal", "type": "text" } },\
    { "id": "3", "display": { "text": "Hard", "type": "text" } }\
  ]\
}

execute if score $difficulty ic.int matches 1 run function infinity_cave:settings/difficulty/easy
execute if score $difficulty ic.int matches 2 run function infinity_cave:settings/difficulty/normal
execute if score $difficulty ic.int matches 3 run function infinity_cave:settings/difficulty/hard

data modify storage infinity_cave:settings progression set value [{"id": "2", "display": {"text": "Standard", "type": "text"}}, {"id": "3", "display": {"text": "Fast", "type": "text"}},{"id": "1", "display": {"text": "Slow", "type": "text"}}]
data modify storage infinity_cave:settings frequency set value {type:"minecraft:number_range",key:"frequency",width:200,label:{text:"Spawn Frequency"},start:1,end:100,step:1,initial:20}
data modify storage infinity_cave:settings mob_cap set value {type:"minecraft:number_range",key:"mob_cap",width:200,label:{text:"Mob Cap"},start:1,end:10,step:1,initial:3}
data modify storage infinity_cave:settings spawn_spread set value {type:"minecraft:number_range",key:"spawn_spread",width:200,label:{text:"Mob Spawn Spread"},start:10,end:32,step:1,initial:16}
data modify storage infinity_cave:settings ranged_damage set value {type:"minecraft:number_range",key:"ranged_damage",width:200,label:{text:"Ranged Mobs Damage Scale"},start:0,end:3,step:0.1,initial:1.8}
data modify storage infinity_cave:settings despawn set value {type:"minecraft:boolean",key:"despawn",label:{text:"Mobs Despawn?"},initial:1b,on_true:"1",on_false:"0"}
data modify storage infinity_cave:settings disable_spawn set value {type:"minecraft:boolean",key:"disable_spawn",label:{text:"Disable Mob Spawning"},initial:0b,on_true:"1",on_false:"0"}
data modify storage infinity_cave:settings disable_jump_ai set value {type:"minecraft:boolean",key:"disable_jump_ai",label:{text:"Disable Jump AI"},initial:0b,on_true:"1",on_false:"0"}

scoreboard players set $progression ic.int 2
execute store result score #spawn_frequency ic.int run data get storage infinity_cave:settings frequency.initial
execute store result score #mob_cap ic.int run data get storage infinity_cave:settings mob_cap.initial
execute store result score #spawn_spread ic.int run data get storage infinity_cave:settings spawn_spread.initial

scoreboard players set #loaded1 ic.int 1