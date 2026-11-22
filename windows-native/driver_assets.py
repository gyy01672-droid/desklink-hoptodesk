"""Stage the signed Windows VDD from the fixed official HopToDesk distribution.

Input is the output of UPX 5.3.0's standard decompressor. The outer image and
each original embedded asset are independently pinned. No driver is installed.
"""
import hashlib
from pathlib import Path
import sys

IMAGE_SHA256 = "2f1428a50b0e8154336a4034c27b51d0f3c6aa189ee7d2f121ba79743004170f"
ASSETS = (
    ("HtdVDD.inf", 16649512, 3594, "a76b989dbac90363bc0d585ba1fa3b6114f98c6405f599a8ddd9ec2ea6137308"),
    ("HtdVDD.dll", 16653106, 363696, "dbd62ad93dcf3a1d0b6f38707f962a7bf79ef9de9fecdf38644528f953478b32"),
    ("HtdVDD.cat", 17016802, 10868, "3bf1609143a17f1aac9194bd4fd42f5da05b651119baafb46ed0b2ad3d051cbe"),
    ("vdd_settings.xml", 17027670, 4425, "58ddf788051ed9f9893fdc0983bc767dd0c9d66de397179e8f931aecef510bb4"),
    ("HtdVDD-publisher.cer", 17032095, 1573, "4e7f7ec91066ffb94fa20c8fe6e65617f8b3982eb237b093cf0ba7c216d7bfb3"),
)

def stage(image, destination):
    data = image.read_bytes()
    if hashlib.sha256(data).hexdigest() != IMAGE_SHA256:
        raise ValueError("Unexpected unpacked executable")
    destination.mkdir(parents=True, exist_ok=True)
    for name, offset, size, expected in ASSETS:
        part = data[offset:offset+size]
        if hashlib.sha256(part).hexdigest() != expected:
            raise ValueError("Asset mismatch: " + name)
        (destination / name).write_bytes(part)

if __name__ == "__main__":
    stage(Path(sys.argv[1]), Path(sys.argv[2]))
