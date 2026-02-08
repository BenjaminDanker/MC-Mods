data modify storage infinity_cave:molten_caves data.rare set value {\
  "mobs": {\
    "1": {\
      "id": "zombified_piglin",\
      "biome": "molten_caves",\
      "mob": "zombified_piglin",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.06,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.012,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.004,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
      "name": "Zombified Piglin",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "2": {\
      "id": "skeleton",\
      "biome": "molten_caves",\
      "mob": "skeleton",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.06,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.012,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.004,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:bow",\
        "count": 1,\
          "components": {\
          "minecraft:enchantments": {\
            "minecraft:flame": 1\
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
      "id": "magma_cube",\
      "biome": "molten_caves",\
      "mob": "magma_cube",\
      "tier": "rare",\
      "health_factor": 1.3,\
      "attack_damage_factor": 0.08,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.010,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.005,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:iron_sword",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Magma Cube",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "4": {\
      "id": "piglin_brute",\
      "biome": "molten_caves",\
      "mob": "piglin_brute",\
      "tier": "rare",\
      "health_factor": 1.2,\
      "attack_damage_factor": 0.06,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.012,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.005,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:iron_axe",\
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
      "name": "Piglin Brute",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "5": {\
      "id": "hell_hog",\
      "biome": "molten_caves",\
      "mob": "hoglin",\
      "tier": "epic",\
      "health_factor": 1.25,\
      "attack_damage_factor": 0.07,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.1,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.1,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3,\
            "infinity_cave:technical/ai/hell_hog": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:iron_sword",\
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
          "id": "minecraft:blaze",\
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
          ]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Hell Hog",\
      "enchant": 4,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "6": {\
      "id": "wither_skeleton",\
      "biome": "molten_caves",\
      "mob": "wither_skeleton",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.07,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.012,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.004,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Wither Skeleton",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "7": {\
      "id": "ninja",\
      "biome": "molten_caves",\
      "mob": "wither_skeleton",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.075,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.0095,\
      "knockback_resistance": 0.005,\
      "head": {\
        "id": "minecraft:player_head",\
        "count": 1,\
        "components": {\
          "minecraft:profile": {\
            "id": [-929155811, -25080598, -1078391422, -2015616720],\
            "properties": [{\
              "name": "textures",\
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzEyNTY3YTY1MDQzNzcwZWUzNWE3NzQ5YjM2MWQ2YzAyMDEwNWZmZWYyYmM4NDAxNzQ3ODE1ZWRkZjYxZTk2ZSJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ninja": 1,\
            "infinity_cave:technical/ai/_all": 3,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1,\
        "components": {\
          "minecraft:item_model": "infinity_cave:katana"\
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
      "silent": 1,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Ninja",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "8": {\
      "id": "fire_archer",\
      "biome": "molten_caves",\
      "mob": "skeleton",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.07,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.012,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.004,\
      "head": {\
        "id": "minecraft:player_head",\
        "count": 1,\
        "components": {\
          "minecraft:profile": {\
            "id": [-929155811, -25080598, -1078391422, -2015616720],\
            "properties": [{\
              "name": "textures",\
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWM3NzcyYzdjZGNkZGI2Yjc5ZDU1MjVmOWRjZWJjNzQ4YWFiZGFlMzhkOWUzOGVlYTdmZTc4YTUwMWRlNmVkZSJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:bow",\
        "count": 1,\
        "components": {\
          "item_model": "infinity_cave:fire_bow",\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/fire_archer": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\",\"fire_archer\"",\
      "name": "Fire Archer",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:molten_caves data.rare.entries int 1 run data get storage infinity_cave:molten_caves data.rare.mobs