data modify storage infinity_cave:sulfide_caves data.legendary set value {\
  "mobs": {\
    "1": {\
      "id": "bogged",\
      "biome": "sulfide_caves",\
      "mob": "bogged",\
      "tier": "legendary",\
      "health_factor": 2.2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.ranged\",\"legendary\"",\
      "name": "Bogged",\
      "enchant": 1,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "2": {\
      "id": "cave_spider",\
      "biome": "sulfide_caves",\
      "mob": "cave_spider",\
      "tier": "legendary",\
      "health_factor": 2,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.015,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:netherite_sword",\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": {}, \
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\"",\
      "name": "Cave Spider",\
      "enchant": 1,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "3": {\
      "id": "slime",\
      "biome": "sulfide_caves",\
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
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"},\
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
    },\
    "4": {\
      "id": "corrupt_allay",\
      "biome": "sulfide_caves",\
      "mob": "allay",\
      "tier": "legendary",\
      "health_factor": 1,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.02,\
      "scale_factor": 0.05,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"},\
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
        "amplifier": "1b",\
        "duration": 0,\
        "show_particles": "0b"\
      }],\
      "silent": 0,\
      "passengers": [\
        {\
          "id": "minecraft:piglin_brute",\
          "Silent": 1,\
          "IsImmuneToZombification": 1b,\
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
          equipment:{mainhand:{id:"minecraft:saddle",count:1,components:{"item_model":"air","minecraft:dyed_color":16646120,"minecraft:enchantments":{"infinity_cave:technical/ai/corrupt_allay":1}}}}, \
          attributes:[{id:"minecraft:scale",base:1},{id:"minecraft:follow_range",base:64}]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\",\"corrupt_allay\"",\
      "name": "Corrupt Allay",\
      "enchant": 1,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    },\
    "5": {\
      "id": "sneezer",\
      "biome": "sulfide_caves",\
      "mob": "sniffer",\
      "tier": "legendary",\
      "health_factor": 2.6,\
      "attack_damage_factor": 0.3,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.1,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.025,\
      "head": {\
        "id": "minecraft:netherite_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:netherite_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:netherite_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:netherite_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:emerald",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 5\
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
          "id": "minecraft:zombie",\
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
          equipment:{mainhand:{id:"minecraft:saddle",count:1,components:{"item_model":"air","minecraft:dyed_color":16646120,"minecraft:enchantments":{"infinity_cave:technical/ai/sneezer":3}}}}, \
          attributes:[{id:"minecraft:scale",base:1},{id:"minecraft:follow_range",base:128}]\
        }],\
      "tags": "\"ic.ability\",\"ic.melee\",\"legendary\",\"sneezer\"",\
      "name": "Sneezer",\
      "enchant": 4,\
      "prefix": {\
        "color": "gold",\
        "text": "Legendary "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:sulfide_caves data.legendary.entries int 1 run data get storage infinity_cave:sulfide_caves data.legendary.mobs