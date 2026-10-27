tellraw @a [{selector:"@s",color:"aqua"},{text:" has relit the Cold Foundry.",color:"yellow"}]
tag @s add completed_tech
tellraw @s {text:"The Foundry was only half of a city. Beyond the gate stands the rest of it, where engineers and mages once worked side by side.",color:"gray",italic:true}
give @s minecraft:clock[custom_data={id:"completed_tech",idType:"Soulbound"},custom_name={text:"Chief Engineer's Seal",color:"gold",italic:false},lore=[{text:"Proof that the Foundry burns again.",color:"gray",italic:false},{text:"Use it at your dock beyond the gate.",color:"dark_gray",italic:true}],enchantment_glint_override=true] 1
playsound minecraft:ui.toast.challenge_complete master @s
tellraw @s [{text:"The way to Magitech opens for you. Craft a ",color:"gray"},{text:"Gate Core",color:"gold"},{text:" and touch it to a frame of steel blocks: the gate is yours alone and closes behind you.",color:"gray"}]
