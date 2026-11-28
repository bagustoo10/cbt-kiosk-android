import os
import struct
import sys
import zlib


def hex_to_rgb(hex_str):
    h = hex_str.strip().lstrip("#")
    if len(h) != 6:
        return (5, 150, 105)
    try:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))
    except ValueError:
        return (5, 150, 105)


def make_png(path, rgb=(5, 150, 105), w=192, h=192):
    r, g, b = rgb
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            if 52 <= x <= 140 and 48 <= y <= 144 and (
                x < 68 or x > 124 or y < 64 or y > 128 or (84 <= x <= 108 and 80 <= y <= 112)
            ):
                raw.extend((255, 255, 255, 255))
            else:
                raw.extend((r, g, b, 255))

    def chunk(tag, data):
        return struct.pack("!I", len(data)) + tag + data + struct.pack("!I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack("!IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    color_arg = sys.argv[1] if len(sys.argv) > 1 else "#059669"
    make_png("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png", hex_to_rgb(color_arg))
    print(f"Ikon berhasil dibuat dengan warna {color_arg}")
