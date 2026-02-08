data modify storage infinity_cave:limestone_grotto data.epic set value {\
  "mobs": {\
    "1": {\
      "id": "drowned",\
      "biome": "limestone_grotto",\
      "mob": "drowned",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.21,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:trident",\
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
      "name": "Drowned",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "2": {\
      "id": "skeleton",\
      "biome": "limestone_grotto",\
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
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
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
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
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
      "biome": "limestone_grotto",\
      "mob": "spider",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
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
      "id": "vindicator",\
      "biome": "limestone_grotto",\
      "mob": "vindicator",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
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
      "name": "Vindicator",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "5": {\
      "id": "ninja",\
      "biome": "limestone_grotto",\
      "mob": "wither_skeleton",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
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
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ninja": 1,\
            "infinity_cave:technical/ai/_all": 4,\
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
      "silent": 1,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Ninja",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "6": {\
      "id": "sniper",\
      "biome": "limestone_grotto",\
      "mob": "zombie",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:player_head",\
        "count": 1,\
        "components": {\
          "minecraft:profile": {\
            "id": [-929155811, -25080598, -1078391422, -2015616720],\
            "properties": [{\
              "name": "textures",\
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWZiM2U4YTdjYWRkZWY2NzM5NTliY2YyZmI4ZDhkYjUwODYwODZhNDJjYmViNmUzYjY2ZjgwMDE2ZGI2NDc3MCJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:trident",\
        "count": 1,\
        "components": {\
          "minecraft:enchantments": {\
            "levels": {\
              "minecraft:sharpness": 1\
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
      {id:"minecraft:armor_stand",Invulnerable:1b,Small:1b,Marker:1b,Invisible:1b,Passengers:[{id:"minecraft:guardian",equipment:{mainhand:{id:"minecraft:diamond_sword",count:1,components:{"item_model":"air","minecraft:enchantments":{"infinity_cave:technical/ai/sniper":1}}}},attributes:[{id:"minecraft:scale",base:0},{id:"minecraft:follow_range",base:64}]}]}],\
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\",\"sniper\"",\
      "name": "Sniper",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "7": {\
      "id": "digger",\
      "biome": "limestone_grotto",\
      "mob": "zombified_piglin",\
      "tier": "epic",\
      "health_factor": 1.6,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.0125,\
      "knockback_resistance": 0.05,\
      "head": {\
        "id": "minecraft:player_head",\
        "count": 1,\
        "components": {\
          "minecraft:profile": {\
            "id": [-929155811, -25080598, -1078391422, -2015616720],\
            "properties": [{\
              "name": "textures",\
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGFjMzEwMGJkNzZjNTNiNzc5ZTRkNzBhZjBmYzAyZjM0ODE4OWJhNzY2MDY4ZmY3Njg1ZTI0OGQzY2FmNWM2NiJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/digger": 1,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_pickaxe",\
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
      "passengers": [{}], \
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Digger",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "8": {\
      "id": "slime",\
      "biome": "limestone_grotto",\
      "mob": "slime",\
      "tier": "epic",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_axe",\
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
      "name": "Slime",\
      "enchant": 1,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:limestone_grotto data.epic.entries int 1 run data get storage infinity_cave:limestone_grotto data.epic.mobs