data modify storage infinity_cave:mushroom_gorge data.uncommon set value {\
  "mobs": {\
    "1": {\
      "id": "mooshroom",\
      "biome": "mushroom_gorge",\
      "mob": "mooshroom",\
      "tier": "uncommon",\
      "health_factor": 0.5,\
      "attack_damage_factor": 0.06,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.06,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.011,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1,\
        "components": {\
          "minecraft:enchantments": {\
            "levels": {\
              "minecraft:fire_aspect": 1\
            }\
          }\
        }\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:iron_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [\
        {\
          "id": "minecraft:piglin_brute",\
          "Silent": 1,\
          "IsImmuneToZombification":1b,\
          "Invulnerable": false, \
          "Tags": ["ic.mount"],\
          "Team":"ic.mob_team", \
          "active_effects": [\
            {\
              "id": "minecraft:invisibility",\
              "amplifier": 1,\
              "duration": -1,\
              "show_particles": 0,\
              "show_icon": 0,\
              "ambient": 0\
            },\
            {\
              "id": "minecraft:instant_health",\
              "amplifier": 100,\
              "duration": -1,\
              "show_particles": 0,\
              "show_icon": 0,\
              "ambient": 0\
            }\
          ],\
          equipment:{mainhand:{id:"minecraft:saddle",count:1,components:{"item_model":"air","minecraft:dyed_color":16646120,"minecraft:enchantments":{"infinity_cave:technical/ai/mooshroom":1}}}}, \
          attributes:[{id:"minecraft:scale",base:0.75},{id:"minecraft:follow_range",base:64},{id:"minecraft:max_health",base:1024}]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\",\"mooshroom\"",\
      "name": "Mooshroom",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "2": {\
      "id": "bogged",\
      "biome": "mushroom_gorge",\
      "mob": "bogged",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.011,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.003,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:bow",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:golden_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:tipped_arrow",\
          "count": 99,\
          "components": {\
            "max_stack_size": 99,\
            "minecraft:potion_contents": {\
              "custom_color": 6184036,\
              "custom_effects": [\
                {\
                  "id": "minecraft:mining_fatigue",\
                  "amplifier": 1,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:levitation",\
                  "amplifier": 4,\
                  "duration": 40,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:tipped_arrow",\
          "count": 99,\
          "components": {\
            "max_stack_size": 99,\
            "minecraft:potion_contents": {\
              "custom_color": 6184036,\
              "custom_effects": [\
                {\
                  "id": "minecraft:mining_fatigue",\
                  "amplifier": 1,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:blindness",\
                  "amplifier": 0,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:wither",\
                  "amplifier": 0,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                }\
              ]\
            }\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Skeleton",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "3": {\
      "id": "cave_spider",\
      "biome": "mushroom_gorge",\
      "mob": "cave_spider",\
      "tier": "uncommon",\
      "health_factor": 0.3,\
      "attack_damage_factor": 0.06,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.011,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.003,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:wooden_sword",\
        "count": 1,\
        "components": {\
          "minecraft:enchantments": {\
            "levels": {\
              "minecraft:knockback": 1\
            }\
          }\
        }\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:iron_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Cave Spider",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "4": {\
      "id": "pillager",\
      "biome": "mushroom_gorge",\
      "mob": "pillager",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.0055,\
      "attack_knockback_factor": 0.011,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:crossbow",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:golden_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:tipped_arrow",\
          "count": 99,\
          "components": {\
            "max_stack_size": 99,\
            "minecraft:potion_contents": {\
              "custom_color": 6184036,\
              "custom_effects": [\
                {\
                  "id": "minecraft:mining_fatigue",\
                  "amplifier": 1,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:levitation",\
                  "amplifier": 4,\
                  "duration": 40,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:tipped_arrow",\
          "count": 99,\
          "components": {\
            "max_stack_size": 99,\
            "minecraft:potion_contents": {\
              "custom_color": 6184036,\
              "custom_effects": [\
                {\
                  "id": "minecraft:mining_fatigue",\
                  "amplifier": 1,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:blindness",\
                  "amplifier": 0,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:wither",\
                  "amplifier": 0,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                }\
              ]\
            }\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Pillager",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "5": {\
      "id": "iron_golem",\
      "biome": "mushroom_gorge",\
      "mob": "iron_golem",\
      "tier": "uncommon",\
      "health_factor": 1,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:wooden_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:iron_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Iron Golem",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "6": {\
      "id": "husk",\
      "biome": "mushroom_gorge",\
      "mob": "husk",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.0055,\
      "attack_knockback_factor": 0.011,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:wooden_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:iron_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Husk",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "7": {\
      "id": "giant_frog",\
      "biome": "mushroom_gorge",\
      "mob": "frog",\
      "tier": "uncommon",\
      "health_factor": 0.5,\
      "attack_damage_factor": 0.06,\
      "speed_factor": 0.0055,\
      "attack_knockback_factor": 0.055,\
      "scale_factor": 0.021,\
      "knockback_resistance": 0.003,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:stone_axe",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:movement_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:iron_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [\
        {\
          "id": "minecraft:piglin_brute",\
          "Silent": 1,\
          "IsImmuneToZombification":1b,\
          "Invulnerable": false, \
          "Tags": ["ic.mount"],\
          "Team":"ic.mob_team", \
          "active_effects": [\
            {\
              "id": "minecraft:invisibility",\
              "amplifier": 1,\
              "duration": -1,\
              "show_particles": 0,\
              "show_icon": 0,\
              "ambient": 0\
            },\
            {\
              "id": "minecraft:instant_health",\
              "amplifier": 100,\
              "duration": -1,\
              "show_particles": 0,\
              "show_icon": 0,\
              "ambient": 0\
            }\
          ],\
          equipment:{mainhand:{id:"minecraft:saddle",count:1,components:{"item_model":"air","minecraft:dyed_color":16646120,"minecraft:enchantments":{"infinity_cave:technical/ai/giant_frog":1}}}}, \
          attributes:[{id:"minecraft:scale",base:0.75},{id:"minecraft:follow_range",base:64},{id:"minecraft:max_health",base:1024}]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\",\"giant_frog\"",\
      "name": "Giant Frog",\
      "enchant": 1,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "8": {\
      "id": "chemist",\
      "biome": "mushroom_gorge",\
      "mob": "witch",\
      "tier": "uncommon",\
      "health_factor": 0.5,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.0055,\
      "attack_knockback_factor": -0.011,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2,\
            "infinity_cave:technical/ai/chemist": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:iron_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 1\
        },\
        {\
          "id": "minecraft:firework_rocket",\
          "count": 1\
        },\
        {\
          "id": "minecraft:leather_boots",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:flying_speed",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:shield",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:armor",\
                "id": "armor",\
                "amount": 5,\
                "operation": "add_value",\
                "slot": "any"\
              }\
            ]\
          }\
        },\
        {\
          "id": "minecraft:golden_sword",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_damage",\
                "id": "attack_damage",\
                "amount": 0.1,\
                "operation": "add_multiplied_base",\
                "slot": "any"\
              }\
            ]\
          }\
        }\
      ],\
      "effects": [{\
        "id": "minecraft:invisibility",\
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Chemist",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:mushroom_gorge data.uncommon.entries int 1 run data get storage infinity_cave:mushroom_gorge data.uncommon.mobs