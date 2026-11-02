# Pretty Chat

Standalone server-side Fabric chat formatting and Velocity rendering API for Resonant Message.

- Fabric formats normal player chat on the current backend only.
- Velocity exposes `PrettyChatRenderer` and the semantic `ChatKind` API to network-wide message producers.
- UUID-derived muted colors are shared by the Fabric and Velocity adapters.
- Resonant messages receive the literal amethyst `✧`; normal local chat does not.

Build with `gradlew :fabric:remapJar :velocity:shadowJar`.
