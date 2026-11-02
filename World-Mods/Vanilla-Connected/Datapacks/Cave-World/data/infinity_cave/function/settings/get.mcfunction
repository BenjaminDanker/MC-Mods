$dialog show @s { \
  "type": "minecraft:server_links",\
  "title": {\
    "text": "Infinity Cave Settings"\
  },\
  "inputs": [\
    {\
      "type": "minecraft:single_option",\
      "key": "difficulty",\
      "label": {\
        "text": "Difficulty",\
        "type": "text"\
      },\
      "options": $(difficulty) \
    },\
    {\
      "type": "minecraft:single_option",\
      "key": "progression",\
      "label": {\
        "text": "Progression",\
        "type": "text"\
      },\
      "options": $(progression) \
    },\
    $(frequency),\
    $(mob_cap),\
    $(spawn_spread),\
    $(ranged_damage),\
    $(despawn),\
    $(disable_spawn),\
    $(disable_jump_ai)\
  ],\
  "pause": true,\
  "after_action": "close",\
  "exit_action": {\
    "label": {\
      "text": "Confirm"\
    },\
    "action": {\
      "type": "dynamic/run_command",\
      "template": "function infinity_cave:settings/set {difficulty:\u0024(difficulty), progression:\u0024(progression),frequency:\u0024(frequency), mob_cap:\u0024(mob_cap), spawn_spread:\u0024(spawn_spread), ranged_damage:\u0024(ranged_damage),despawn:\u0024(despawn), disable_spawn:\u0024(disable_spawn), disable_jump_ai:\u0024(disable_jump_ai)}"\
    }\
  }\
}
