# Sky-Islands

Fabric mod for the Sky-Islands server targeting Minecraft 26.2 with Fabric
Loader 0.19.3 and Java 25. The mod keeps the dragon, giant, portal,
night-ghast, special-item, and natural creeper-spawn features. Legacy
extended-distance entity-tracker and mob-spawner mixins are left out of the
metadata pending a separate internal-API rework.

## Build

```powershell
# From this folder
java -version
.\gradlew.bat jar
```

Place the resulting jar from `build\libs` into a 26.2 server `mods` folder.
