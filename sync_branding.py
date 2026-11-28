import base64
import io
import json
import os
import subprocess
import sys
import urllib.request

URL_SHIELD_KEY = b"BGT2026_CBT_URL_SHIELD"


def encode_hidden_url(plain: str) -> str:
    raw = plain.strip().encode("utf-8")
    if not raw:
        return ""
    xored = bytes(raw[i] ^ URL_SHIELD_KEY[i % len(URL_SHIELD_KEY)] for i in range(len(raw)))
    return "ENC1:" + base64.b64encode(xored).decode("ascii")


client_type = sys.argv[1].strip() if len(sys.argv) > 1 else "apk_only"
license_project = sys.argv[2].strip() if len(sys.argv) > 2 else "owner-apk-bagus"
school_project = sys.argv[3].strip() if len(sys.argv) > 3 else "ruang-uji-bagus"
school_slug = sys.argv[4].strip().lower() if len(sys.argv) > 4 else "ruangujibagus"
fallback_name = sys.argv[5].strip() if len(sys.argv) > 5 else "CBT Exam Browser"
fallback_color = sys.argv[6].strip() if len(sys.argv) > 6 else "#059669"
fallback_url = sys.argv[7].strip() if len(sys.argv) > 7 else "https://ruang-uji-bagus.web.app"
fallback_entry_pin = sys.argv[8].strip() if len(sys.argv) > 8 else ""

final_name = fallback_name
final_color = fallback_color
final_url_enc = encode_hidden_url(fallback_url)
final_entry_pin = fallback_entry_pin
logo_saved = False
icon_path = "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"

if client_type == "apk_only":
    doc_url = f"https://firestore.googleapis.com/v1/projects/{license_project}/databases/(default)/documents/apk_clients/{school_slug}"
else:
    doc_url = f"https://firestore.googleapis.com/v1/projects/{school_project}/databases/(default)/documents/license/main"

print(f">>> Mengambil Branding ({client_type}) dari: {doc_url}")
try:
    req = urllib.request.Request(doc_url, headers={"User-Agent": "CBT-APK-Factory/2.5"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    fields = data.get("fields", {})
    remote_name = fields.get("appName", {}).get("stringValue", "").strip()
    remote_logo = fields.get("logoDataUrl", {}).get("stringValue", "").strip()
    remote_color = fields.get("themeColor", {}).get("stringValue", "").strip()
    remote_url_enc = fields.get("targetUrlEnc", {}).get("stringValue", "").strip()
    remote_entry_pin = fields.get("entryPin", {}).get("stringValue", "").strip()

    if remote_name:
        final_name = remote_name
    if remote_color.startswith("#") and len(remote_color) == 7:
        final_color = remote_color
    if remote_url_enc.startswith("ENC1:"):
        final_url_enc = remote_url_enc
    if remote_entry_pin:
        final_entry_pin = remote_entry_pin

    if remote_logo:
        raw_bytes = None
        if remote_logo.startswith("data:image/") and "," in remote_logo:
            raw_bytes = base64.b64decode(remote_logo.split(",", 1)[1])
        elif remote_logo.startswith("http://") or remote_logo.startswith("https://"):
            with urllib.request.urlopen(remote_logo, timeout=15) as img_resp:
                raw_bytes = img_resp.read()
        if raw_bytes:
            os.makedirs(os.path.dirname(icon_path), exist_ok=True)
            try:
                from PIL import Image
                img = Image.open(io.BytesIO(raw_bytes)).convert("RGBA").resize((192, 192), Image.LANCZOS)
                img.save(icon_path, format="PNG")
                logo_saved = True
                print(">>> Logo APK custom berhasil dipasang (192x192 PNG)!")
            except Exception:
                if raw_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
                    with open(icon_path, "wb") as f_img:
                        f_img.write(raw_bytes)
                    logo_saved = True
except Exception as err:
    print(f">>> Info: Menggunakan fallback lokal ({err})")

if not logo_saved:
    subprocess.run(["python3", "generate_icon.py", final_color], check=True)

with open("synced_app_name.txt", "w", encoding="utf-8") as f:
    f.write(final_name)
with open("synced_url_enc.txt", "w", encoding="utf-8") as f:
    f.write(final_url_enc)
with open("synced_entry_pin.txt", "w", encoding="utf-8") as f:
    f.write(final_entry_pin)
with open("synced_theme_color.txt", "w", encoding="utf-8") as f:
    f.write(final_color)
print(f">>> Final Nama APK: {final_name} | Warna: {final_color} | EntryPIN: {'AKTIF' if final_entry_pin else 'KOSONG'}")
