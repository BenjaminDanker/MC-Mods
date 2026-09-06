# Pet service network boundary

The application rejects IPv4 and IPv6 wildcard bind addresses. Prefer loopback when nginx and
pet-service share a host. If gameplay/proxy hosts require direct access, bind one private address
and permit TCP 8787 only from the exact Fabric, Velocity, and HTTPS reverse-proxy
addresses.

`pet-companion.nft.example` is an additive example using IANA documentation addresses. It must not
be installed verbatim and must never be used with `flush ruleset`. First export the active rules,
replace every address, merge the rules into the host's managed firewall, and validate syntax:

```sh
sudo nft list ruleset > /root/nftables-before-pet-companion.txt
sudo nft --check --file /etc/nftables.d/pet-companion.nft
```

Test from one allowed and one denied source before enabling the service. The denied source must
not establish TCP 8787. Keep SSH/management access in the host's existing rules; this template
does not manage it. If IPv6 is used, add explicit private IPv6 source/destination rules before
binding an IPv6 address. Wildcard IPv6 remains rejected by service configuration.

The public internet should reach nginx on TLS 443, not port 8787. The nginx example publishes only
the exact signed `/v1/stripe/webhook`, opaque `/checkout/{token}`, and inert result paths while
returning 404 for all bearer-authenticated routes.
Do not enable body transforms, decompression, or filters: Stripe verification uses the exact raw
request bytes. Validate with `nginx -t`, then test that `/health/ready` and `/v1/pets` are 404 from
the public origin while the internal private origin still requires its bearer token.
