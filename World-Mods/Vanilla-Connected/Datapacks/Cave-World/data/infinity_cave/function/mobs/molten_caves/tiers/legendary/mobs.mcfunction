data modify storage infinity_cave:molten_caves data.legendary set value {\
  "mobs": {\
    "1": {\
      "id": "zombified_piglin",\
      "biome": "molten_caves",\
      "mob": "zombified_piglin",\
      "tier": "legendary",\
      "health_factor": 2,\
      "attack_damage_factor": 0.08,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.005,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:netherite_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 4\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Zombified Piglin",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "2": {\
      "id": "ninja",\
      "biome": "molten_caves",\
      "mob": "wither_skeleton",\
      "tier": "legendary",\
      "health_factor": 2,\
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
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ninja": 1,\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:netherite_sword",\
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
          "count": 4\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Ninja",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "3": {\
      "id": "fire_archer",\
      "biome": "molten_caves",\
      "mob": "skeleton",\
      "tier": "legendary",\
      "health_factor": 2,\
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
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
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
            "infinity_cave:technical/ai/fire_archer": 4\
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
          "count": 4\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\",\"fire_archer\"",\
      "name": "Fire Archer",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "4": {\
      "id": "bonk_diver",\
      "biome": "molten_caves",\
      "mob": "piglin_brute",\
      "tier": "legendary",\
      "health_factor": 2.5,\
      "attack_damage_factor": 0.07,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:elytra",\
        "count": 1\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/ai/bonk_diver": 1,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:mace",\
        "count": 1,\
        "components": {\
          "minecraft:enchantments": {\
            "minecraft:density": 10,\
            "minecraft:wind_burst": 10\
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
          "count": 4\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Bonk Diver",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "5": {\
      "id": "hell_hog",\
      "biome": "molten_caves",\
      "mob": "hoglin",\
      "tier": "legendary",\
      "health_factor": 3,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.00625,\
      "attack_knockback_factor": 0.12,\
      "scale_factor": 0.0125,\
      "knockback_resistance": 0.2,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:redstone",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/ai/hell_hog": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:netherite_sword",\
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
          "count": 4\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Hell Hog",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:molten_caves data.legendary.entries int 1 run data get storage infinity_cave:molten_caves data.legendary.mobs