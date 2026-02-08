data modify storage infinity_cave:amethyst_caves data.epic set value {\
  "mobs": {\
    "1": {\
      "id": "zombie",\
      "biome": "amethyst_caves",\
      "mob": "zombie",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/chemist": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:instant_damage",\
                      "amplifier": 1,\
                      "duration": 1\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Zombie",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "2": {\
      "id": "skeleton",\
      "biome": "amethyst_caves",\
      "mob": "skeleton",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.21,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/chemist": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:bow",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:instant_damage",\
                      "amplifier": 1,\
                      "duration": 1\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
              "custom_color": 12130303,\
              "custom_effects": [\
                {\
                  "id": "minecraft:wither",\
                  "amplifier": 3,\
                  "duration": 90,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:levitation",\
                  "amplifier": 7,\
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
                  "duration": 90,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:blindness",\
                  "amplifier": 0,\
                  "duration": 30,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:hunger",\
                  "amplifier": 3,\
                  "duration": 70,\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Skeleton",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "3": {\
      "id": "spider",\
      "biome": "amethyst_caves",\
      "mob": "spider",\
      "tier": "epic",\
      "health_factor": 1.2,\
      "attack_damage_factor": 0.25,\
      "speed_factor": 0.008,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/chemist": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
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
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Spider",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "4": {\
      "id": "pillager",\
      "biome": "amethyst_caves",\
      "mob": "pillager",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.21,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:crossbow",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
                  "amplifier": 3,\
                  "duration": 90,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:levitation",\
                  "amplifier": 7,\
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
                  "duration": 90,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:blindness",\
                  "amplifier": 0,\
                  "duration": 30,\
                  "ambient": true,\
                  "show_particles": true,\
                  "show_icon": true\
                },\
                {\
                  "id": "minecraft:hunger",\
                  "amplifier": 3,\
                  "duration": 70,\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Pillager",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "5": {\
      "id": "iron_golem",\
      "biome": "amethyst_caves",\
      "mob": "iron_golem",\
      "tier": "epic",\
      "health_factor": 2,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.007,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.04,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Iron Golem",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "6": {\
      "id": "enderman",\
      "biome": "amethyst_caves",\
      "mob": "enderman",\
      "tier": "epic",\
      "health_factor": 1.25,\
      "attack_damage_factor": 0.22,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/chemist": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Enderman",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "7": {\
      "id": "sludge",\
      "biome": "amethyst_caves",\
      "mob": "endermite",\
      "tier": "epic",\
      "health_factor": 2,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0045,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.04,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/sludge": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\"",\
      "name": "Sludge",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "8": {\
      "id": "corrupt_allay",\
      "biome": "amethyst_caves",\
      "mob": "allay",\
      "tier": "epic",\
      "health_factor": 0.75,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.025,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.melee\",\"epic\",\"corrupt_allay\"",\
      "name": "Corrupt Allay",\
      "enchant": 1,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "9": {\
      "id": "chemist",\
      "biome": "amethyst_caves",\
      "mob": "witch",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/chemist": 1\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"epic\"",\
      "name": "Chemist",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    },\
    "10": {\
      "id": "magician",\
      "biome": "amethyst_caves",\
      "mob": "evoker",\
      "tier": "epic",\
      "health_factor": 1.4,\
      "attack_damage_factor": 0.24,\
      "speed_factor": 0.0075,\
      "attack_knockback_factor": 0.018,\
      "scale_factor": 0.013,\
      "knockback_resistance": 0.02,\
      "head": {\
        "id": "minecraft:diamond_helmet",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "chest": {\
        "id": "minecraft:diamond_chestplate",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "legs": {\
        "id": "minecraft:diamond_leggings",\
        "count": 1,components:{"minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"}}\
      },\
      "feet": {\
        "id": "minecraft:diamond_boots",\
        "count": 1,\
        "components": {\
        "minecraft:trim":{material:"minecraft:amethyst",pattern:"minecraft:silence"},\
          "minecraft:enchantments": {\
            "infinity_cave:technical/ai/_all": 4,\
            "infinity_cave:technical/ai/magician": 2\
          }\
        }\
      },\
      "mainhand": {\
        "id": "minecraft:diamond_sword",\
        "count": 1\
      },\
      "offhand": [\
        {\
          "id": "minecraft:totem_of_undying",\
          "count": 1,\
          "components": {\
            "minecraft:death_protection": {\
              "death_effects": [\
                {\
                  "type": "minecraft:apply_effects",\
                  "effects": [\
                    {\
                      "id": "minecraft:regeneration",\
                      "amplifier": 4,\
                      "duration": 30\
                    }\
                  ]\
                },\
                {\
                  "type": "minecraft:play_sound",\
                  "sound": "item.totem.use"\
                }\
              ]\
            }\
          }\
        },\
        {\
          "id": "minecraft:wind_charge",\
          "count": 1\
        },\
        {\
          "id": "minecraft:golden_apple",\
          "count": 3\
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
      "tags": "\"ic.ability\",\"ic.ranged\",\"ic.magician\",\"epic\"",\
      "name": "Magician",\
      "enchant": 4,\
      "prefix": {\
        "color": "dark_red",\
        "text": "Epic "\
      }\
    }\
  }\
}

# saves amount of entries to list
execute store result storage infinity_cave:amethyst_caves data.epic.entries int 1 run data get storage infinity_cave:amethyst_caves data.epic.mobs