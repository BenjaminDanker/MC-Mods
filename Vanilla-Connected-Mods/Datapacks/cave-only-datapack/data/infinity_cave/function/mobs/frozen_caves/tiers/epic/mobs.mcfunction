data modify storage infinity_cave:frozen_caves data.epic set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "frozen_caves",\
      "mob": "zombie",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Zombie",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "2": {\
      "id": "stray",\
      "biome": "frozen_caves",\
      "mob": "stray",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.27,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
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
          "count": 3\
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
                  "id": "minecraft:slowness",\
                  "amplifier": 1,\
                  "duration": 100,\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Stray",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "frozen_caves",\
      "mob": "spider",\
      "tier": "epic",\
      "health_factor": 1.2,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:stone_sword",\
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
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Spider",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "4": {\
      "id": "vex",\
      "biome": "frozen_caves",\
      "mob": "vex",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.033,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Vex",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "5": {\
      "id": "wither_skeleton",\
      "biome": "frozen_caves",\
      "mob": "wither_skeleton",\
      "tier": "epic",\
      "health_factor": 1.6,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:stone_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Wither Skeleton",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "6": {\
      "id": "snow_golem",\
      "biome": "frozen_caves",\
      "mob": "snow_golem",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.25,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
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
          "count": 3\
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
          "id": "minecraft:skeleton",\
          "Silent": 1,\
          "Invulnerable": true, \
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
            }\
          ],\
          equipment:{mainhand:{id:"minecraft:bow",count:1,components:{"item_model":"air","minecraft:enchantments":{"infinity_cave:technical/ai/snow_golem":1}}}}, \
          attributes:[{id:"minecraft:scale",base:0},{id:"minecraft:follow_range",base:64}]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\",\"snow_golem\"",\
      "name": "Snow Golem",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "7": {\
      "id": "ram_bear",\
      "biome": "frozen_caves",\
      "mob": "polar_bear",\
      "tier": "epic",\
      "health_factor": 1.25,\
      "attack_damage_factor": 0.15,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.1,\
      "scale_factor": 0.0125,\
      "knockback_resistance": 1,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:explosion_resistance": 10,\
            "infinity_cave:technical/ai/_all": 4\
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
          "count": 3\
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
          "id": "minecraft:stray",\
          "Silent": 1,\
          "Invulnerable": true, \
          "Tags": ["ic.mount"],\
          "Team":"ic.mob_team", \
           equipment:{feet:{id:"minecraft:leather_boots",count:1,components:{"minecraft:dyed_color":16646120,"minecraft:enchantments":{"infinity_cave:technical/ai/ram_bear":4}}}}, \
          "active_effects": [\
            {\
              "id": "minecraft:invisibility",\
              "amplifier": 1,\
              "duration": -1,\
              "show_particles": 0,\
              "show_icon": 0,\
              "ambient": 0\
            }\
          ],\
          attributes:[{id:"minecraft:scale",base:1.9},{id:"minecraft:follow_range",base:64}]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Ram Bear",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "8": {\
      "id": "ice_archer",\
      "biome": "frozen_caves",\
      "mob": "stray",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:player_head",\
        "count": 1,\
        "components": {\
          "minecraft:profile": {\
            "id": [-929155811, -25080598, -1078391422, -2015616720],\
            "properties": [{\
              "name": "textures",\
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYxOWM2Y2I5MjNmYzM3ZDQyMWVjOTEwNDI1YzIyMDNjNGMxYTFiZjc3NTYyMGZmYmZhMmRlMzI1YjM5YThiZiJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:bow",\
        "count": 1,\
        "components": {\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ice_archer": 4\
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
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Ice Archer",\
      "enchant": 1,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:frozen_caves data.epic.entries int 1 run data get storage infinity_cave:frozen_caves data.epic.mobs