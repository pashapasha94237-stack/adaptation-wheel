"""Build the small native cuboid-model texture; no external art is required."""

from pathlib import Path
from random import Random

from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "src/main/resources/assets/adaptationwheel/textures/entity/wheel.png"


def main() -> None:
    rng = Random(803)
    palette = [
        (222, 174, 49, 255),
        (235, 189, 63, 255),
        (243, 202, 80, 255),
        (248, 212, 98, 255),
        (204, 150, 35, 255),
    ]
    texture = Image.new("RGBA", (32, 32))
    for y in range(32):
        for x in range(32):
            # Coarse pixels and restrained variation keep it readable at game scale.
            choice = rng.choices(range(5), weights=[25, 42, 22, 7, 4])[0]
            color = palette[choice]
            if x % 8 == 0 and y % 8 < 6:
                color = (248, 213, 104, 255)
            elif y % 8 == 7 and x % 8 > 1:
                color = (192, 138, 30, 255)
            texture.putpixel((x, y), color)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    texture.save(TARGET)
    print(f"Wrote {TARGET} ({texture.width} x {texture.height})")


if __name__ == "__main__":
    main()
