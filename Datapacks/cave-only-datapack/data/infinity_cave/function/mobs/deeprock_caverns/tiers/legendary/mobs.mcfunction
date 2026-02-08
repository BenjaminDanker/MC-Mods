data modify storage infinity_cave:deeprock_caverns data.legendary set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "deeprock_caverns",\
      "mob": "zombie",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Zombie",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "2": {\
      "id": "skeleton",\
      "biome": "deeprock_caverns",\
      "mob": "skeleton",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"legendary\"",\
      "name": "Skeleton",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "deeprock_caverns",\
      "mob": "spider",\
      "tier": "legendary",\
      "health_factor": 1.9,\
      "attack_damage_factor": 0.33,\
      "speed_factor": 0.0085,\
      "attack_knockback_factor": 0.022,\
      "scale_factor": 0.012,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Spider",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "4": {\
      "id": "bomber",\
      "biome": "deeprock_caverns",\
      "mob": "creeper",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:netherite",pattern:"minecraft:dune"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5,\
            "infinity_cave:technical/ai/bomber": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:tnt",\
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
        "amplifier": 1,\
        "duration": 0,\
        "show_particles": false\
      }],\
      "silent": 0,\
      "passengers": [], \
      "tags": "\"ic.ability\",\"ic.ranged\",\"legendary\"",\
      "name": "Bomber",\
      "enchant": 5,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:deeprock_caverns data.legendary.entries int 1 run data get storage infinity_cave:deeprock_caverns data.legendary.mobs