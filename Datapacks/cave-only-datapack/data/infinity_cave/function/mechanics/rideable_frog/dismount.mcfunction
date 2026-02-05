execute if predicate {condition:"minecraft:inverted",term:{condition:"minecraft:entity_properties",entity:"this",predicate:{vehicle:{type:"minecraft:frog"}}}} run function infinity_cave:mechanics/rideable_frog/not_riding

advancement revoke @s[tag=ic.riding_frog] only infinity_cave:mechanics/rideable_frog/dismount