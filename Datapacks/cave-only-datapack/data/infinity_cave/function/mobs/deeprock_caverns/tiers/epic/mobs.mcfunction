data modify storage infinity_cave:deeprock_caverns data.epic set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "deeprock_caverns",\
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
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Zombie",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "2": {\
      "id": "skeleton",\
      "biome": "deeprock_caverns",\
      "mob": "skeleton",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Skeleton",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "deeprock_caverns",\
      "mob": "spider",\
      "tier": "epic",\
      "health_factor": 1.2,\
      "attack_damage_factor": 0.27,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Spider",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "4": {\
      "id": "pillager",\
      "biome": "deeprock_caverns",\
      "mob": "pillager",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Pillager",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "5": {\
      "id": "iron_golem",\
      "biome": "deeprock_caverns",\
      "mob": "iron_golem",\
      "tier": "epic",\
      "health_factor": 2,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.04,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "diamond Golem",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "6": {\
      "id": "wither_skeleton",\
      "biome": "deeprock_caverns",\
      "mob": "wither_skeleton",\
      "tier": "epic",\
      "health_factor": 1.5,\
      "attack_damage_factor": 0.26,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Wither Skeleton",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "7": {\
      "id": "vindicator",\
      "biome": "deeprock_caverns",\
      "mob": "vindicator",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Vindicator",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "8": {\
      "id": "cave_spider",\
      "biome": "deeprock_caverns",\
      "mob": "cave_spider",\
      "tier": "epic",\
      "health_factor": 2,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.023,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Cave Spider",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "9": {\
      "id": "bomber",\
      "biome": "deeprock_caverns",\
      "mob": "creeper",\
      "tier": "epic",\
      "health_factor": 2,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.023,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/bomber": 1\  
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Bomber",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:deeprock_caverns data.epic.entries int 1 run data get storage infinity_cave:deeprock_caverns data.epic.mobs