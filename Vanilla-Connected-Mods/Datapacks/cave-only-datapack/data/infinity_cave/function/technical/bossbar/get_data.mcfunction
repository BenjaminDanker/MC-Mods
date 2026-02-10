$data modify storage infinity_cave:bossbar list append value {health:$(health),color:$(color),style:$(style),id:"$(out)"}

$function infinity_cave:technical/bossbar/generate with storage infinity_cave:bossbar list[{"id":"$(out)"}]