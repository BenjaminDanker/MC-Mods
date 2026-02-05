data modify storage infinity_cave:frozen_caves data.uncommon set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "frozen_caves",\
      "mob": "zombie",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Zombie",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "2": {\
      "id": "stray",\
      "biome": "frozen_caves",\
      "mob": "stray",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Stray",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "frozen_caves",\
      "mob": "spider",\
      "tier": "uncommon",\
      "health_factor": 0.3,\
      "attack_damage_factor": 0.11,\
      "speed_factor": 0.0065,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Spider",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "4": {\
      "id": "vex",\
      "biome": "frozen_caves",\
      "mob": "vex",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.125,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.031,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Vex",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "5": {\
      "id": "wither_skeleton",\
      "biome": "frozen_caves",\
      "mob": "wither_skeleton",\
      "tier": "uncommon",\
      "health_factor": 0.5,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Wither Skeleton",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "6": {\
      "id": "polar_bear",\
      "biome": "frozen_caves",\
      "mob": "polar_bear",\
      "tier": "uncommon",\
      "health_factor": 0.8,\
      "attack_damage_factor": 0.15,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Polar Bear",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "7": {\
      "id": "snow_golem",\
      "biome": "frozen_caves",\
      "mob": "snow_golem",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.1,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:diamond",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\",\"snow_golem\"",\
      "name": "Snow Golem",\
      "enchant": 4,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:frozen_caves data.uncommon.entries int 1 run data get storage infinity_cave:frozen_caves data.uncommon.mobs