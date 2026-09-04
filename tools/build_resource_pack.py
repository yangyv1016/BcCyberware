"""从已审核的透明原图构建 Paper 1.21.11 示例资源包。"""

from __future__ import annotations

import hashlib
import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
PACK_ROOT = ROOT / "sample-pack" / "BcCyberware-Example-Pack"
SOURCE_ROOT = ROOT / "sample-pack" / "source-art"
TEXTURE_ROOT = PACK_ROOT / "assets" / "bccyberware" / "textures" / "item"
MODEL_ROOT = PACK_ROOT / "assets" / "bccyberware" / "models" / "item"
ITEM_ROOT = PACK_ROOT / "assets" / "bccyberware" / "items"
ZIP_PATH = ROOT / "sample-pack" / "BcCyberware-Example-Pack.zip"
SHA1_PATH = ROOT / "sample-pack" / "BcCyberware-Example-Pack.sha1"

DIRECT_ASSETS = (
    "native_brain",
    "native_eyes",
    "native_heart",
    "native_nerves",
    "native_skeleton",
    "native_skin",
    "native_left_arm",
    "native_left_leg",
    "pulseforge_heart",
    "carbon_lattice",
    "prism_dermis",
    "nociception_gate",
    "predictive_optics",
    "adrenal_reservoir",
    "synapse_overclock",
    "arc_pulse_arm",
)

MIRRORED_ASSETS = {
    "native_right_arm": "native_left_arm",
    "native_right_leg": "native_left_leg",
}


def json_write(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def normalize_icon(source: Path, destination: Path, mirror: bool = False) -> None:
    with Image.open(source) as loaded:
        image = loaded.convert("RGBA")
    alpha_box = image.getchannel("A").getbbox()
    if alpha_box is None:
        raise ValueError(f"原图没有可见像素: {source}")
    image = image.crop(alpha_box)
    if mirror:
        image = image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)

    # 64x64 能保留原创细节，内容限制在 56x56 以免贴边。
    image.thumbnail((56, 56), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    position = ((64 - image.width) // 2, (64 - image.height) // 2)
    canvas.alpha_composite(image, position)
    destination.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(destination, format="PNG", optimize=True)


def source_art_available() -> bool:
    required_sources = {f"{name}.png" for name in DIRECT_ASSETS}
    required_sources.update(f"{name}.png" for name in MIRRORED_ASSETS.values())
    return all((SOURCE_ROOT / filename).is_file() for filename in required_sources)


def render_pack_from_source_art() -> None:
    if PACK_ROOT.exists():
        shutil.rmtree(PACK_ROOT)
    TEXTURE_ROOT.mkdir(parents=True)
    MODEL_ROOT.mkdir(parents=True)
    ITEM_ROOT.mkdir(parents=True)

    for name in DIRECT_ASSETS:
        normalize_icon(SOURCE_ROOT / f"{name}.png", TEXTURE_ROOT / f"{name}.png")
    for name, source_name in MIRRORED_ASSETS.items():
        normalize_icon(
            SOURCE_ROOT / f"{source_name}.png",
            TEXTURE_ROOT / f"{name}.png",
            mirror=True,
        )

    all_assets = (*DIRECT_ASSETS, *MIRRORED_ASSETS.keys())
    for name in all_assets:
        json_write(
            MODEL_ROOT / f"{name}.json",
            {
                "parent": "minecraft:item/generated",
                "textures": {"layer0": f"bccyberware:item/{name}"},
            },
        )
        json_write(
            ITEM_ROOT / f"{name}.json",
            {
                "model": {
                    "type": "minecraft:model",
                    "model": f"bccyberware:item/{name}",
                }
            },
        )

    json_write(
        PACK_ROOT / "pack.mcmeta",
        {
            "pack": {
                "description": "BcCyberware 1.21.11 原创义体与原生器官示例材质",
                "min_format": [75, 0],
                "max_format": [75, 0],
            }
        },
    )
    normalize_icon(SOURCE_ROOT / "pulseforge_heart.png", PACK_ROOT / "pack.png")


def validate_pack_tree() -> None:
    all_assets = (*DIRECT_ASSETS, *MIRRORED_ASSETS.keys())
    required_files = [PACK_ROOT / "pack.mcmeta", PACK_ROOT / "pack.png"]
    for name in all_assets:
        required_files.extend(
            (
                TEXTURE_ROOT / f"{name}.png",
                MODEL_ROOT / f"{name}.json",
                ITEM_ROOT / f"{name}.json",
            )
        )
    missing = [str(path.relative_to(ROOT)) for path in required_files if not path.is_file()]
    if missing:
        raise FileNotFoundError("资源包缺少必要文件：\n- " + "\n- ".join(missing))


def build() -> None:
    if source_art_available():
        render_pack_from_source_art()
        mode = "rendered from source art"
    else:
        # CI 不携带体积较大的生成阶段原稿；直接验证并封装仓库中已审核的 64x64 成品。
        mode = "packaged from checked-in assets"

    validate_pack_tree()

    if ZIP_PATH.exists():
        ZIP_PATH.unlink()
    with zipfile.ZipFile(ZIP_PATH, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(PACK_ROOT.rglob("*")):
            if path.is_file():
                relative = path.relative_to(PACK_ROOT).as_posix()
                info = zipfile.ZipInfo(relative, date_time=(2026, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                archive.writestr(info, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

    digest = hashlib.sha1(ZIP_PATH.read_bytes()).hexdigest()
    SHA1_PATH.write_text(digest + "\n", encoding="ascii", newline="\n")
    print(f"Built {ZIP_PATH}")
    print(f"Mode {mode}")
    print(f"SHA-1 {digest}")
    print(f"Textures {len((*DIRECT_ASSETS, *MIRRORED_ASSETS.keys()))}")


if __name__ == "__main__":
    build()
