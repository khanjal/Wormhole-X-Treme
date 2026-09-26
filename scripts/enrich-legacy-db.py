"""Writes a copy of the 2016 fixture database with the states it lacks: enrich-legacy-db.py <in> <out>.

The real database has no IDCs, two networks, one world and no gate saved mid-trip. Each edit
below is one the old plugin could have written itself: a column value, a fixed-width flag, or a
length-prefixed string rewritten with its correct length. Blob offsets are those of binary
version 8, the version every gate in the fixture uses.
"""
import shutil
import sqlite3
import struct
import sys

# Binary version 8: version byte, three 12-byte blocks and two 32-byte locations, then the sign
# fields (flag, block, int index, long target), the active flag and the long target id.
ACTIVE = 1 + 3 * 12 + 2 * 32 + 1 + 12 + 4 + 8
TARGET = ACTIVE + 1
FACING = TARGET + 8

IDCS = {"Vanadium": ("vanadium23", True), "Silver": ("ag47", True), "Potassium": ("k19", False)}
NETWORKS = {"Zinc": "Traders", "Boron": "Traders", "Chromium": "Traders",
            "Neon": "Mining", "Aluminium": "Mining", "Sulfur": "Admin", "Flourine": "Explorers"}
# Paper creates both beside the default level-name, which boot-test.sh leaves as "world".
WORLDS = {"Cobalt": ("world_nether", "NETHER"), "Nickel": ("world_nether", "NETHER"),
          "Xenon": ("world_the_end", "THE_END")}
MID_TRIP = ("Gallium", "Manganese")
# Cut where version 8 holds fixed-width material ordinals, so the reader runs out of bytes; a cut
# inside a length prefix would be a different test (see GateSerializer.sized).
TRUNCATED = "Oxygen"


def with_idc(blob, code, iris_shut):
    assert blob[0] == 8, "only binary version 8 is laid out here"
    facing_len = struct.unpack(">i", blob[FACING:FACING + 4])[0]
    at = FACING + 4 + facing_len
    old_len = struct.unpack(">i", blob[at:at + 4])[0]
    rest = blob[at + 4 + old_len:]
    encoded = code.encode("utf-8")
    # The iris flag is the byte straight after the code.
    return blob[:at] + struct.pack(">i", len(encoded)) + encoded + bytes([1 if iris_shut else 0]) + rest[1:]


def mid_trip(blob, target_id):
    return blob[:ACTIVE] + b"\x01" + struct.pack(">q", target_id) + blob[FACING:]


def main(src, dst):
    shutil.copyfile(src, dst)
    db = sqlite3.connect(dst)
    ids = dict(db.execute("SELECT Name, Id FROM Stargates"))
    blobs = dict(db.execute("SELECT Name, GateData FROM Stargates"))

    for name, (code, shut) in IDCS.items():
        db.execute("UPDATE Stargates SET GateData = ? WHERE Name = ?", (with_idc(blobs[name], code, shut), name))
    for name, network in NETWORKS.items():
        db.execute("UPDATE Stargates SET Network = ? WHERE Name = ?", (network, name))
    for name, (world, environment) in WORLDS.items():
        db.execute("UPDATE Stargates SET WorldName = ?, World = ?, WorldEnvironment = ? WHERE Name = ?",
                   (world, world, environment, name))
    first, second = MID_TRIP
    db.execute("UPDATE Stargates SET GateData = ? WHERE Name = ?", (mid_trip(blobs[first], ids[second]), first))
    db.execute("UPDATE Stargates SET GateData = ? WHERE Name = ?", (mid_trip(blobs[second], ids[first]), second))
    db.execute("UPDATE Stargates SET GateData = ? WHERE Name = ?", (blobs[TRUNCATED][:200], TRUNCATED))

    db.commit()
    db.close()


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
