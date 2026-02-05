data modify storage infinity_cave:frozen_caves data.legendary set value {\
  "mobs": {\
    "1": {\
      "id": "vex",\
      "biome": "frozen_caves",\
      "mob": "vex",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.035,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/bossbar":1\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"legendary\"",\
      "name": "Vex",\
      "enchant": 4,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "2": {\
      "id": "snow_golem",\
      "biome": "frozen_caves",\
      "mob": "snow_golem",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.3,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/bossbar":1\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\",\"snow_golem\"",\
      "name": "Snow Golem",\
      "enchant": 4,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "3": {\
      "id": "ram_bear",\
      "biome": "frozen_caves",\
      "mob": "polar_bear",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 1,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:explosion_resistance": 10,\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/bossbar":1\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Ram Bear",\
      "enchant": 4,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "4": {\
      "id": "ice_archer",\
      "biome": "frozen_caves",\
      "mob": "stray",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.35,\
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
              "value": "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYxOWM2Y2I5MjNmYzM3ZDQyMWVjOTEwNDI1YzIyMDNjNGMxYTFiZjc3NTYyMGZmYmZhMmRlMzI1YjM5YThiZiJ9fX0=" \
            }]\
          }\
        }\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/bossbar":1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:bow",\
        "count": 1,\
        "components": {\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ice_archer": 5\
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
                  "amplifier": 20,\
                  "duration": 20,\
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
                  "amplifier": 1,\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"legendary\"",\
      "name": "Ice Archer",\
      "enchant": 1,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:frozen_caves data.legendary.entries int 1 run data get storage infinity_cave:frozen_caves data.legendary.mobs