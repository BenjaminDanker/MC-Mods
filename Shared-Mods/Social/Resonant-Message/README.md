# Resonant Message

Standalone server-side Fabric mod and Velocity plugin for Minecraft 26.2 and Fabric Loader 0.19.3. The Fabric component has no client mod requirement and uses the existing Network-Authorization runtime for its shared signing primitive and backend credentials.

## Behavior

Right-click while an Amethyst Shard is in the active hand to prime one message. The shard remains in inventory until Velocity accepts the cross-network broadcast. The private priming message is:

Your amethyst resonates. Your next message will be heard across worlds.

Priming has no timeout. It cancels on a selected hotbar slot change, active-hand item change, shard drop or inventory move, death, or disconnect. Commands bypass chat interception. Normal chat continues to use the current backend chat path.

## Structure

- common: bounded protocol and HMAC signing
- fabric: server-only Fabric hooks, per-player priming/cancellation, and accepted-send item consumption
- velocity: standalone Velocity plugin, backend authentication, replay journal, and network broadcast; delegates final presentation to Pretty Chat

## Protocol and configuration

Channel: resonant:message_v1. Protocol version 1 carries a structured request with player UUID, text, canonical backend ID, backend epoch, nonce, timestamp, and HMAC-SHA256 signature. Request signatures use the `resonant-message/v1` domain; acknowledgements use `resonant-message/v1/ack`. The signed acknowledgement is returned only after Velocity accepts and queues the broadcast.

Resonant Message reads each backend ID and key from the existing `config/network-authorization.properties` file and reads the matching keys from Velocity's `plugins/wakeuplobby/authorization-backend-keys.properties`. It does not create Resonant Message-specific key files. The shared `DomainSeparatedHmac` helper signs the Resonant Message canonical payload under its own domain, so Network-Authorization signatures cannot be replayed across protocols. Portal signing material remains separate.

Velocity verifies the source server connection, the player's current backend and UUID, the backend identity, timestamp, signature, and persisted replay nonce before broadcasting to all connected players. Pretty Chat owns the message colors and Resonance marker.

## Build

Run gradlew.bat :fabric:jar :velocity:shadowJar. The production artifacts are the Fabric jar and Velocity shadow jar in their module build/libs directories.
