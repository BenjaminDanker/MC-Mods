data modify storage infinity_cave:limestone_grotto data.legendary set value {\
  "mobs": {\
    "1": {\
      "id": "drowned",\
      "biome": "limestone_grotto",\
      "mob": "drowned",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.23,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/bossbar": 1\         
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Drowned",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "2": {\
      "id": "ninja",\
      "biome": "limestone_grotto",\
      "mob": "wither_skeleton",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.31,\
      "speed_factor": 0.008,\
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
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzEyNTY3YTY1MDQzNzcwZWUzNWE3NzQ5YjM2MWQ2YzAyMDEwNWZmZWYyYmM4NDAxNzQ3ODE1ZWRkZjYxZTk2ZSJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 1,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Ninja",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "3": {\
      "id": "sniper",\
      "biome": "limestone_grotto",\
      "mob": "zombie",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
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
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWZiM2U4YTdjYWRkZWY2NzM5NTliY2YyZmI4ZDhkYjUwODYwODZhNDJjYmViNmUzYjY2ZjgwMDE2ZGI2NDc3MCJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/bossbar": 1\         
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": [\
      {id:"minecraft:armor_stand",Invulnerable:1b,Small:1b,Marker:1b,Invisible:1b,Passengers:[{id:"minecraft:guardian",equipment:{mainhand:{id:"minecraft:netherite_sword",count:1,components:{"item_model":"air","minecraft:enchantments":{"infinity_cave:technical/ai/sniper":1}}}},attributes:[{id:"minecraft:scale",base:0},{id:"minecraft:follow_range",base:64}]}]}],\
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\",\"sniper\"",\
      "name": "Sniper",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "4": {\
      "id": "digger",\
      "biome": "limestone_grotto",\
      "mob": "zombified_piglin",\
      "tier": "legendary",\
      "health_factor": 2.6,\
      "attack_damage_factor": 0.32,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.025,\
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
            "infinity_cave:technical/ai/digger": 1,\
            "infinity_cave:anti-fall": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:netherite_pickaxe",\
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
      "passengers": [{}], \
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Digger",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "5": {\
      "id": "slime",\
      "biome": "limestone_grotto",\
      "mob": "slime",\
      "tier": "legendary",\
      "health_factor": 3,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:copper",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:netherite_axe",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Slime",\
      "enchant": 1,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:limestone_grotto data.legendary.entries int 1 run data get storage infinity_cave:limestone_grotto data.legendary.mobs