tellraw @a [{selector:"@s",color:"aqua"},{text:" has finished the Convergence. The Spire is awake.",color:"yellow"}]
tag @s add completed_magic
give @s minecraft:nether_star[custom_data={id:"completed_magic"},custom_name={text:"Seal of the Convergence",color:"aqua",italic:false},lore=[{text:"Proof that you completed the Magic server.",color:"gray",italic:false}],enchantment_glint_override=true] 1
playsound minecraft:ui.toast.challenge_complete master @s
tellraw @s [{text:"+4 skill points. ",color:"aqua"},{text:"[Open the skill tree]",color:"gold",underlined:true,click_event:{action:"run_command",command:"skills"}}]
tellraw @s [{text:"The way to Magitech opens for you. Run ",color:"gray"},{text:"/skills ascend",color:"gold",underlined:true,click_event:{action:"suggest_command",command:"/skills ascend"}},{text:", then cast an ability you have taken to level 5 (or the Avatar State) within 30 seconds, and a gate will open for you alone.",color:"gray"}]
