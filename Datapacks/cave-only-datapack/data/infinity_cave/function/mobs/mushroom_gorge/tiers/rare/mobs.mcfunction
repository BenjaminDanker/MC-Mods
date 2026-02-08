data modify storage infinity_cave:mushroom_gorge data.rare set value {\
  "mobs": {\
    "1": {\
      "id": "mooshroom",\
      "biome": "mushroom_gorge",\
      "mob": "mooshroom",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\",\"mooshroom\"",\
      "name": "Mooshroom",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "2": {\
      "id": "bogged",\
      "biome": "mushroom_gorge",\
      "mob": "bogged",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"rare\"",\
      "name": "Skeleton",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "3": {\
      "id": "cave_spider",\
      "biome": "mushroom_gorge",\
      "mob": "cave_spider",\
      "tier": "rare",\
      "health_factor": 0.7,\
      "attack_damage_factor": 0.2,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Cave Spider",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "4": {\
      "id": "pillager",\
      "biome": "mushroom_gorge",\
      "mob": "pillager",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"rare\"",\
      "name": "Pillager",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "5": {\
      "id": "iron_golem",\
      "biome": "mushroom_gorge",\
      "mob": "iron_golem",\
      "tier": "rare",\
      "health_factor": 1.5,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Iron Golem",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "6": {\
      "id": "husk",\
      "biome": "mushroom_gorge",\
      "mob": "husk",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Husk",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "7": {\
      "id": "giant_frog",\
      "biome": "mushroom_gorge",\
      "mob": "frog",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.1,\
      "scale_factor": 0.022,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:wooden_axe",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\",\"giant_frog\"",\
      "name": "Giant Frog",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "8": {\
      "id": "chemist",\
      "biome": "mushroom_gorge",\
      "mob": "witch",\
      "tier": "rare",\
      "health_factor": 0.7,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:iron",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3,\
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
          "count": 2\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"rare\"",\
      "name": "Chemist",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:mushroom_gorge data.rare.entries int 1 run data get storage infinity_cave:mushroom_gorge data.rare.mobs