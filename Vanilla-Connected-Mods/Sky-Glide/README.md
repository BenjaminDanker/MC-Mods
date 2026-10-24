# Sky-Glide

A Bending addon that adds **TideWard** (Water's defense: absorption hearts and resistance for 8 seconds) and **SkyGlide**: a slow, time-limited Air glide (about 8 blocks a second for 25
seconds, then a 30 second rest). It is gated by the Air Mastery node of the Element-Choice skill tree.

Install: put `sky-glide-1.0.0.jar` in `config/bending/addons/` and `translations/en.properties` in
`config/bending/translations/`. Speed, duration and cooldown live in `bending.conf` under
`abilities.air.skyglide`.

Build: copy the Bending jar and the Adventure/geantyref/option jars it nests into `libs/`, then `./gradlew build`.
