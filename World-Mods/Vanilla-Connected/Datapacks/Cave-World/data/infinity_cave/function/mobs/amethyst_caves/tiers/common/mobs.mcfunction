data modify storage infinity_cave:amethyst_caves data.common set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "amethyst_caves",\
      "mob": "zombie",\
      "tier": "common",\
      "health_factor": 0.3,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
      "biome": "amethyst_caves",\
      "mob": "skeleton",\
      "tier": "common",\
      "health_factor": 0.3,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
              "custom_color": 12130303,\
              "custom_effects": [\
                {\
                  "id": "minecraft:wither",\
                  "amplifier": 1,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:levitation",\
                  "amplifier": 4,\
                  "duration": 90,\
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
              "custom_color": 15210495,\
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
                  "id": "minecraft:hunger",\
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
      "biome": "amethyst_caves",\
      "mob": "spider",\
      "tier": "common",\
      "health_factor": 0.25,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
      "biome": "amethyst_caves",\
      "mob": "pillager",\
      "tier": "common",\
      "health_factor": 0.3,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
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
          "count": 99,\
          "components": {\
                "max_stack_size": 99,\
                "minecraft:fireworks": {\
                  "flight_duration": 4,\
                  "explosions": [\
                    {\
                      "shape": "small_ball",\
                      "has_twinkle": true,\
                      "has_trail": true,\
                      "colors": [\
                        15210495\
                      ],\
                      "fade_colors": [\
                        16770518\
                      ]\
                    },\
                    {\
                      "shape": "small_ball",\
                      "has_twinkle": true,\
                      "has_trail": true,\
                      "colors": [\
                        15210495\
                      ],\
                      "fade_colors": [\
                        16770518\
                      ]\
                    },\
                    {\
                      "shape": "small_ball",\
                      "has_twinkle": true,\
                      "has_trail": true,\
                      "colors": [\
                        15210495\
                      ],\
                      "fade_colors": [\
                        16770518\
                      ]\
                    }\
                  ]\
                }\
          }\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
              "custom_color": 12130303,\
              "custom_effects": [\
                {\
                  "id": "minecraft:wither",\
                  "amplifier": 1,\
                  "duration": 80,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:levitation",\
                  "amplifier": 4,\
                  "duration": 90,\
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
              "custom_color": 15210495,\
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
                  "id": "minecraft:hunger",\
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
      "biome": "amethyst_caves",\
      "mob": "iron_golem",\
      "tier": "common",\
      "health_factor": 0.5,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.04,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Iron Golem",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "6": {\
      "id": "enderman",\
      "biome": "amethyst_caves",\
      "mob": "enderman",\
      "tier": "common",\
      "health_factor": 0.3,\
      "attack_damage_factor": 0.03,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.01,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Enderman",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "7": {\
      "id": "sludge",\
      "biome": "amethyst_caves",\
      "mob": "endermite",\
      "tier": "common",\
      "health_factor": 0.6,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.0027,\
      "attack_knockback_factor": -0.1,\
      "scale_factor": 0.05,\
      "knockback_resistance": 0.0025,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1,\
            "infinity_cave:technical/ai/sludge": 1\
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
          "id": "minecraft:goat_horn",\
          "count": 1,\
          "components": {\
            "minecraft:attribute_modifiers": [\
              {\
                "type": "minecraft:attack_knockback",\
                "id": "attack_knockback",\
                "amount": 2,\
                "operation": "add_value",\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\"",\
      "name": "Sludge",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "8": {\
      "id": "corrupt_allay",\
      "biome": "amethyst_caves",\
      "mob": "allay",\
      "tier": "common",\
      "health_factor": 0.15,\
      "attack_damage_factor": 0.05,\
      "speed_factor": 0.005,\
      "attack_knockback_factor": 0.1,\
      "scale_factor": 0.025,\
      "knockback_resistance": 1,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
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
              },\
              {\
                "type": "minecraft:flying_speed",\
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
          "id": "minecraft:piglin_brute",\
          "Silent": 1,\
          "CustomName": { "color": "white", "text": "Common Corrupt Allay" },\
          "CustomNameVisible": 0b,\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"common\",\"corrupt_allay\"",\
      "name": "Corrupt Allay",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    },\
    "9": {\
      "id": "chemist",\
      "biome": "amethyst_caves",\
      "mob": "witch",\
      "tier": "common",\
      "health_factor": 0.25,\
      "attack_damage_factor": 0.075,\
      "speed_factor": 0.006,\
      "attack_knockback_factor": -0.1,\
      "scale_factor": 0.01,\
      "knockback_resistance": 0.0005,\
      "head": {\
        "id": "minecraft:leather_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:leather_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:leather_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:leather_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 1,\
            "infinity_cave:technical/ai/chemist": 1\
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
                "type": "minecraft:flying_speed",\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"common\"",\
      "name": "Chemist",\
      "enchant": 1,\
      "prefix": {\
        "color": "white",\
        "text": "Common "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:amethyst_caves data.common.entries int 1 run data get storage infinity_cave:amethyst_caves data.common.mobs