#!/usr/bin/env python3
"""Probe local Java Edition status without logging credentials or changing server state."""

import json
import socket
import sys


def varint(value):
    result = bytearray()
    while True:
        digit = value & 0x7F
        value >>= 7
        result.append(digit | (0x80 if value else 0))
        if not value:
            return bytes(result)


def read_varint(stream):
    result = 0
    for shift in range(0, 35, 7):
        digit = stream.read(1)
        if not digit:
            raise EOFError("status response ended early")
        result |= (digit[0] & 0x7F) << shift
        if not digit[0] & 0x80:
            return result
    raise ValueError("invalid status packet length")


for port in map(int, sys.argv[1:]):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=3) as connection:
            connection.settimeout(3)
            stream = connection.makefile("rb")
            host = b"localhost"
            handshake = b"\x00" + varint(0) + varint(len(host)) + host
            handshake += port.to_bytes(2, "big") + b"\x01"
            connection.sendall(varint(len(handshake)) + handshake + b"\x01\x00")
            read_varint(stream)
            if read_varint(stream) != 0:
                raise ValueError("unexpected status packet")
            payload = stream.read(read_varint(stream))
            status = json.loads(payload)
            print(f"{port}: OK {status.get('version', {}).get('name', 'unknown')}")
    except (OSError, ValueError, EOFError, json.JSONDecodeError) as error:
        print(f"{port}: FAIL {type(error).__name__}: {error}")
        sys.exit(1)
