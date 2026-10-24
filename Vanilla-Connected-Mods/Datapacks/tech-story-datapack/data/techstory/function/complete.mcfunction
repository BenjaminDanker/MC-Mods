tellraw @a [{selector:"@s",color:"aqua"},{text:" has relit the Cold Foundry.",color:"yellow"}]
tag @s add completed_tech
give @s minecraft:clock[custom_data={id:"completed_tech"},custom_name={text:"Chief Engineer's Seal",color:"gold",italic:false},lore=[{text:"Proof that you completed the Tech server.",color:"gray",italic:false}],enchantment_glint_override=true] 1
playsound minecraft:ui.toast.challenge_complete master @s
tellraw @s [{text:"The way to Magitech opens for you. Craft a ",color:"gray"},{text:"Gate Core",color:"gold"},{text:" and touch it to a frame of steel blocks: the gate is yours alone and closes behind you.",color:"gray"}]
