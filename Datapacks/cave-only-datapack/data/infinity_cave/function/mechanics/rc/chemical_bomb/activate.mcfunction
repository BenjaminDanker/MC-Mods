execute at @s run function infinity_cave:mechanics/rc/chemical_bomb/particles

execute summon marker positioned 0.0 0.0 0.0 run function infinity_cave:mechanics/rc/chemical_bomb/motion

execute anchored eyes positioned ^ ^ ^ anchored feet run function infinity_cave:mechanics/rc/chemical_bomb/spawn with storage infinity_cave:chemical_bomb

advancement revoke @s only infinity_cave:mechanics/rc/chemical_bomb