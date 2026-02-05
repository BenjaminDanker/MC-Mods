data modify storage infinity_cave:deep_dark data.uncommon set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "deep_dark",\
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
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
      "id": "skeleton",\
      "biome": "deep_dark",\
      "mob": "skeleton",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Skeleton",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "deep_dark",\
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
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "id": "pillager",\
      "biome": "deep_dark",\
      "mob": "pillager",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 2\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"uncommon\"",\
      "name": "Pillager",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "5": {\
      "id": "iron_golem",\
      "biome": "deep_dark",\
      "mob": "iron_golem",\
      "tier": "uncommon",\
      "health_factor": 1,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
      "name": "Iron Golem",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "6": {\
      "id": "wither_skeleton",\
      "biome": "deep_dark",\
      "mob": "wither_skeleton",\
      "tier": "uncommon",\
      "health_factor": 0.45,\
      "attack_damage_factor": 0.11,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
    "7": {\
      "id": "vindicator",\
      "biome": "deep_dark",\
      "mob": "vindicator",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
      "name": "Vindicator",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "8": {\
      "id": "ninja",\
      "biome": "deep_dark",\
      "mob": "wither_skeleton",\
      "tier": "uncommon",\
      "health_factor": 0.4,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.0045,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.008,\
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
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ninja": 1,\
            "infinity_cave:technical/ai/_all": 2,\
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
      "silent": 1,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"uncommon\"",\
      "name": "Ninja",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "9": {\
      "id": "warden",\
      "biome": "deep_dark",\
      "mob": "warden",\
      "tier": "uncommon",\
      "health_factor": 0.55,\
      "attack_damage_factor": 0.1,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
      "name": "Warden",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    },\
    "10": {\
      "id": "ravager",\
      "biome": "deep_dark",\
      "mob": "ravager",\
      "tier": "uncommon",\
      "health_factor": 0.6,\
      "attack_damage_factor": 0.11,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.0125,\
      "scale_factor": 0.011,\
      "knockback_resistance": 0.0125,\
      "head": {\
        "id": "minecraft:chainmail_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:chainmail_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:chainmail_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:chainmail_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:silence"},\
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
      "name": "Ravager",\
      "enchant": 2,\
      "prefix": {\
        "color": "green",\
        "text": "Uncommon "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:deep_dark data.uncommon.entries int 1 run data get storage infinity_cave:deep_dark data.uncommon.mobs