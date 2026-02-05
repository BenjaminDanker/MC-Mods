data modify storage infinity_cave:deeprock_caverns data.common set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "deeprock_caverns",\
      "mob": "zombie",\
      "tier": "common",\
      "health_factor": 0.2,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:wooden_sword",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Zombie",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "2": {\
      "id": "skeleton",\
      "biome": "deeprock_caverns",\
      "mob": "skeleton",\
      "tier": "common",\
      "health_factor": 0.2,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"common\"",\
      "name": "Skeleton",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "deeprock_caverns",\
      "mob": "spider",\
      "tier": "common",\
      "health_factor": 0.15,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Spider",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "4": {\
      "id": "pillager",\
      "biome": "deeprock_caverns",\
      "mob": "pillager",\
      "tier": "common",\
      "health_factor": 0.2,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"common\"",\
      "name": "Pillager",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "5": {\
      "id": "iron_golem",\
      "biome": "deeprock_caverns",\
      "mob": "iron_golem",\
      "tier": "common",\
      "health_factor": 0.5,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:wooden_sword",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Iron Golem",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "6": {\
      "id": "wither_skeleton",\
      "biome": "deeprock_caverns",\
      "mob": "wither_skeleton",\
      "tier": "common",\
      "health_factor": 0.2,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Wither Skeleton",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "7": {\
      "id": "vindicator",\
      "biome": "deeprock_caverns",\
      "mob": "vindicator",\
      "tier": "common",\
      "health_factor": 0.25,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Vindicator",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "8": {\
      "id": "ninja",\
      "biome": "deeprock_caverns",\
      "mob": "wither_skeleton",\
      "tier": "common",\
      "health_factor": 0.2,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.009,\
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
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/ninja": 1,\
            "infinity_cave:technical/ai/_all": 1,\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Ninja",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "9": {\
      "id": "cave_spider",\
      "biome": "deeprock_caverns",\
      "mob": "cave_spider",\
      "tier": "common",\
      "health_factor": 0.25,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.02,\
      "knockback_resistance": 0.004,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Cave Spider",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:deeprock_caverns data.common.entries int 1 run data get storage infinity_cave:deeprock_caverns data.common.mobs