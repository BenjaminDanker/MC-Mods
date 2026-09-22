# Network Authorization

The Fabric module receives player-bound snapshots from WakeUpLobby over the dedicated
`silverauth:sync_v1` channel. It evaluates them locally through `authorization-common`;
it has no MariaDB dependency and never falls back to vanilla OP for central decisions.

Install `network-authorization` on each active Fabric backend alongside any migrated
first-party mod. On first startup it creates `config/network-authorization.properties`.
Set `server_id` to the exact canonical Velocity server key (for example `vanilla1`) and set
`backend_key_base64` to that backend's unique key. WakeUpLobby creates
`plugins/wakeuplobby/authorization-backend-keys.properties`; put the same Base64 key
under the matching server ID there. Use a different random 32-byte-or-longer key for
every backend. Do not reuse portal signing material. `waiting_lobby` is configured only
if it is an actual Fabric backend; a proxy-only holding server needs no Fabric config.
Both implementations restrict
the properties file to owner read/write where POSIX permissions are available.

The proxy creates empty key entries only; it does not invent or log deployment secrets.
Until both sides have matching keys and an explicit server ID, snapshots are not accepted
and centralized checks deny. The `magic` configuration must retain its already-proven
key; all other backends must use distinct keys.
