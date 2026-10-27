tellraw @s {text:"The warning ends mid-sentence, as if the writer was interrupted. The fire is waiting.",color:"gray",italic:true}
tellraw @s [{text:"+6 skill points. ",color:"aqua"},{text:"[Open the skill tree]",color:"gold",underlined:true,click_event:{action:"run_command",command:"skills"}}]
