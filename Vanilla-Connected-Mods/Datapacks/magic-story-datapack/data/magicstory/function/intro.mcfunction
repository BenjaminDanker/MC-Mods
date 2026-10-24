tellraw @s {text:"WELCOME TO THE MAGIC SERVER",color:"gold"}
tellraw @s {text:"Here you learn to bend air, water, earth and fire.",color:"gray"}
tellraw @s [{text:"1. Open your skills with ",color:"gray"},{text:"/skills",color:"gold",click_event:{action:"run_command",command:"/skills"}},{text:" to pick your element and learn abilities.",color:"gray"}]
tellraw @s [{text:"2. Put an ability on your hotbar: hold a hotbar slot and type ",color:"gray"},{text:"/bending bind <ability>",color:"gold",click_event:{action:"suggest_command",command:"/bending bind "}},{text:".",color:"gray"}]
tellraw @s [{text:"3. Hold that slot and use the ability (most use left-click). ",color:"gray"},{text:"/bending help",color:"gold",click_event:{action:"run_command",command:"/bending help"}},{text:" explains each one; ",color:"gray"},{text:"/bending display",color:"gold",click_event:{action:"run_command",command:"/bending display"}},{text:" shows what you have bound.",color:"gray"}]
tellraw @s {text:"4. To follow the story, visit a mage tower, an ancient temple, a Nether stronghold and the End, and finally a wizard's tower. Open your advancements (L) to see each step.",color:"gray"}
