# UUID STORAGE BOSSBARS
execute store result storage infinity_cave:boss_list iterate int 1 run scoreboard players set .iterate ic.int 0
# GU library
scoreboard objectives add ic.main dummy
scoreboard objectives add ic.uuid.0 dummy
scoreboard objectives add ic.uuid.1 dummy
scoreboard objectives add ic.uuid.2 dummy
scoreboard objectives add ic.uuid.3 dummy
scoreboard objectives add num dummy
scoreboard players set 256 num 256
data modify storage infinity_cave:main hex_chars set value ["00","01","02","03","04","05","06","07","08","09","0a","0b","0c","0d","0e","0f","10","11","12","13","14","15","16","17","18","19","1a","1b","1c","1d","1e","1f","20","21","22","23","24","25","26","27","28","29","2a","2b","2c","2d","2e","2f","30","31","32","33","34","35","36","37","38","39","3a","3b","3c","3d","3e","3f","40","41","42","43","44","45","46","47","48","49","4a","4b","4c","4d","4e","4f","50","51","52","53","54","55","56","57","58","59","5a","5b","5c","5d","5e","5f","60","61","62","63","64","65","66","67","68","69","6a","6b","6c","6d","6e","6f","70","71","72","73","74","75","76","77","78","79","7a","7b","7c","7d","7e","7f","80","81","82","83","84","85","86","87","88","89","8a","8b","8c","8d","8e","8f","90","91","92","93","94","95","96","97","98","99","9a","9b","9c","9d","9e","9f","a0","a1","a2","a3","a4","a5","a6","a7","a8","a9","aa","ab","ac","ad","ae","af","b0","b1","b2","b3","b4","b5","b6","b7","b8","b9","ba","bb","bc","bd","be","bf","c0","c1","c2","c3","c4","c5","c6","c7","c8","c9","ca","cb","cc","cd","ce","cf","d0","d1","d2","d3","d4","d5","d6","d7","d8","d9","da","db","dc","dd","de","df","e0","e1","e2","e3","e4","e5","e6","e7","e8","e9","ea","eb","ec","ed","ee","ef","f0","f1","f2","f3","f4","f5","f6","f7","f8","f9","fa","fb","fc","fd","fe","ff"]

# player stats
scoreboard objectives add ic.common_killed dummy
scoreboard objectives add ic.uncommon_killed dummy
scoreboard objectives add ic.rare_killed dummy
scoreboard objectives add ic.epic_killed dummy
scoreboard objectives add ic.legendary_killed dummy
scoreboard objectives add ic.playtime custom:play_time
scoreboard objectives add ic.progression dummy

# In order for motion to work
forceload add 0 0 0 0

# Scoreboard integer
scoreboard objectives add ic.log_out custom:leave_game
scoreboard objectives add ic.rotation dummy
scoreboard objectives add ic.int dummy
scoreboard objectives add ic.id dummy
scoreboard objectives add ic.data dummy
scoreboard objectives add ic.animate dummy
scoreboard objectives add ic.ai dummy
scoreboard objectives add ic.altar dummy
scoreboard objectives add ic.uuid dummy

scoreboard objectives add ic.frog_leap dummy

# armor, weapons & enchantments
scoreboard objectives add ic.sonic custom:play_time
scoreboard objectives add ic.limestone_sword custom:play_time
scoreboard objectives add ic.damage_blocked minecraft.custom:minecraft.damage_blocked_by_shield
scoreboard objectives add ic.thunder_charge dummy
scoreboard objectives add ic.thunder_int dummy
scoreboard objectives add ic.sonic_charge dummy
scoreboard objectives add ic.deepcavern_cp dummy
scoreboard objectives add ic.molten_lg dummy
scoreboard objectives add ic.frozen_bt dummy
scoreboard objectives add ic.sulfide_hm dummy
scoreboard objectives add ic.sulfide_poison custom:play_time
scoreboard objectives add ic.rc_cd custom:play_time
scoreboard objectives add ic.mob_count dummy

# entity hit detection
scoreboard objectives add ic.const dummy
scoreboard players set 2 ic.const 2
scoreboard players set 20 ic.const 20
scoreboard players set -1 ic.const -1
scoreboard objectives add ic.phit dummy
scoreboard objectives add ic.hit_cd dummy
scoreboard players set #ic1 ic.const 1
scoreboard players set #ic10 ic.const 10
scoreboard players set #ic-5 ic.const -5
scoreboard players set #ic5 ic.const 5
scoreboard players set #ic8 ic.const 8
scoreboard players set #ic40 ic.const 40
scoreboard players set #ic90 ic.const 90
scoreboard players set #ic20 ic.const 420
scoreboard players set #ic60 ic.const 360
scoreboard players set #ic00 ic.const 300
scoreboard players set #ic0 ic.const 240
scoreboard players set #ic180 ic.const 180
scoreboard players set #ic120 ic.const 120
scoreboard players set #ic60 ic.const 60


scoreboard objectives add ic.tagged dummy
scoreboard objectives add ic.id dummy
scoreboard objectives add ic.bit0 dummy
scoreboard objectives add ic.bit1 dummy
scoreboard objectives add ic.bit2 dummy
scoreboard objectives add ic.bit3 dummy
scoreboard objectives add ic.bit4 dummy
scoreboard objectives add ic.bit5 dummy
scoreboard objectives add ic.bit6 dummy
scoreboard objectives add ic.bit7 dummy
scoreboard objectives add ic.bit8 dummy
scoreboard objectives add ic.bit9 dummy
scoreboard objectives add ic.bit10 dummy
scoreboard objectives add ic.bit11 dummy
scoreboard objectives add ic.bit12 dummy
scoreboard objectives add ic.bit13 dummy
scoreboard objectives add ic.bit14 dummy
scoreboard objectives add ic.bit15 dummy

# Teams to identify mobs
team add ic.mob_team
team modify ic.mob_team collisionRule never
team modify ic.mob_team friendlyFire false
team modify ic.mob_team color gold

# Misc
scoreboard objectives add ic.health health
gamerule commandModificationBlockLimit 10000000
scoreboard players set #disable_offhand ic.int 0
scoreboard players set #antipillar ic.int 0
scoreboard players set #doublespawn ic.int 1
scoreboard players set $interval ic.int 120
scoreboard objectives add ic.repel custom:play_time

# 1 second clock
schedule function infinity_cave:abilities/5_seconds 5s

# settings
execute store result score #loaded1 ic.int unless score #loaded1 ic.int matches 0.. run function infinity_cave:first_time_load

# Mob IDs
function infinity_cave:configuration

# Fire Archer list
data merge storage infinity_cave:fire_archer { \
  projectiles: ["arrow", "spectral_arrow"], \
}

# Geraldo Gadgets list
data merge storage infinity_cave:geraldo { \
  gadgets: ["arrow", "splash_potion", "splash_potion", "armor_stand", "tnt"], \
  nbt: [ \
    {data: {damage: 30d}}, \
    {data: {Item: {id: "minecraft:splash_potion", count: 1, components: {"minecraft:potion_contents": {custom_effects: [{id: "minecraft:instant_damage", amplifier: 1, duration: 10}]}}}}}, \
    {data: {Item: {id: "minecraft:splash_potion", count: 1, components: {"minecraft:potion_contents": {custom_effects: [{id: "minecraft:levitation", amplifier: 14, duration: 100}]}}}}}, \
    {data: {NoGravity: 1b, Invulnerable: 1b, Small: 1b, Invisible: 1b, equipment: {saddle: {id: "minecraft:oak_button", count: 1, components: {"minecraft:item_model": "air", "minecraft:enchantments": {"infinity_cave:technical/seeking_missile": 50}}}}}}, \
    {data: {fuse: 80}} \
  ] \
}