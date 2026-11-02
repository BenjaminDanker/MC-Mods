tellraw @a [{selector:"@s",color:"aqua"},{text:" has finished the Convergence. The Spire is awake.",color:"yellow"}]
tag @s add completed_magic
tellraw @s {text:"The Spire did not fall on its own. Beyond the gate stands the city that broke it, where mages and engineers once worked side by side.",color:"gray",italic:true}
give @s minecraft:nether_star[custom_data={id:"completed_magic",idType:"Soulbound"},custom_name={text:"Seal of the Convergence",color:"aqua",italic:false},lore=[{text:"Proof that the Spire is awake.",color:"gray",italic:false},{text:"Use it at your dock beyond the gate.",color:"dark_gray",italic:true}],enchantment_glint_override=true] 1
playsound minecraft:ui.toast.challenge_complete master @s
tellraw @s [{text:"+12 skill points. ",color:"aqua"},{text:"[Open the skill tree]",color:"gold",underlined:true,click_event:{action:"run_command",command:"skills"}}]
tellraw @s [{text:"The way to Magitech opens for you. Run ",color:"gray"},{text:"/skills ascend",color:"gold",underlined:true,click_event:{action:"suggest_command",command:"/skills ascend"}},{text:", then cast an ability you have taken to level 5 (or the Ascendant State) within 30 seconds, and a gate will open for you alone.",color:"gray"}]
