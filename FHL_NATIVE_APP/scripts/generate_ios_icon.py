from pathlib import Path
from PIL import Image

project_root = Path(__file__).resolve().parent.parent
source = project_root / "app" / "src" / "main" / "assets" / "public" / "fhl-electronics-logo.png"
destination = project_root / "ios" / "App" / "App" / "Assets.xcassets" / "AppIcon.appiconset" / "AppIcon-512@2x.png"

icon = Image.open(source).convert("RGBA")
opaque_background = Image.new("RGBA", icon.size, "#7c0609")
opaque_background.alpha_composite(icon)
final_icon = opaque_background.convert("RGB").resize((1024, 1024), Image.Resampling.LANCZOS)
destination.parent.mkdir(parents=True, exist_ok=True)
final_icon.save(destination, "PNG", optimize=True)
print(f"Generated opaque iOS icon at {destination}")
