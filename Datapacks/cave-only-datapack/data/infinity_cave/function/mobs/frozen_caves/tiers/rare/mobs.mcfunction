data modify storage infinity_cave:frozen_caves data.rare set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "frozen_caves",\
      "mob": "zombie",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Zombie",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "2": {\
      "id": "stray",\
      "biome": "frozen_caves",\
      "mob": "stray",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.2,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.ranged\",\"rare\"",\
      "name": "Stray",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "frozen_caves",\
      "mob": "spider",\
      "tier": "rare",\
      "health_factor": 0.7,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Spider",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "4": {\
      "id": "vex",\
      "biome": "frozen_caves",\
      "mob": "vex",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.2,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.032,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.ranged\",\"rare\"",\
      "name": "Vex",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "5": {\
      "id": "wither_skeleton",\
      "biome": "frozen_caves",\
      "mob": "wither_skeleton",\
      "tier": "rare",\
      "health_factor": 1,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Wither Skeleton",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "6": {\
      "id": "polar_bear",\
      "biome": "frozen_caves",\
      "mob": "polar_bear",\
      "tier": "rare",\
      "health_factor": 1.2,\
      "attack_damage_factor": 0.2,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.015,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 3\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\"",\
      "name": "Polar Bear",\
      "enchant": 3,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    },\
    "7": {\
      "id": "snow_golem",\
      "biome": "frozen_caves",\
      "mob": "snow_golem",\
      "tier": "rare",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.18,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.2,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.015,\
      "head": {\
        "id": "minecraft:iron_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:iron_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:iron_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:iron_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"rare\",\"snow_golem\"",\
      "name": "Snow Golem",\
      "enchant": 4,\
      "prefix": {\
        "color": "blue",\
        "text": "Rare "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:frozen_caves data.rare.entries int 1 run data get storage infinity_cave:frozen_caves data.rare.mobs