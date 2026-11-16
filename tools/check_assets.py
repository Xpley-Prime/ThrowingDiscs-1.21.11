#!/usr/bin/env python3
"""Check built JAR resource links and complete translations (standard library only)."""
from __future__ import annotations
import json
import pathlib
import struct
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
jars = sorted((root / "build" / "libs").glob("palimpsest-*.jar"))
jars = [p for p in jars if not p.stem.endswith("-sources")]
assert len(jars) == 1, f"Expected a single runnable mod JAR, got {jars}"

with zipfile.ZipFile(jars[0]) as archive:
    contents = set(archive.namelist())

    def read_json(name: str):
        assert name in contents, f"Missing packed JSON: {name}"
        return json.loads(archive.read(name).decode("utf-8"))

    ru = read_json("assets/palimpsest/lang/ru_ru.json")
    en = read_json("assets/palimpsest/lang/en_us.json")
    definitions = sorted(
        p for p in contents
        if p.startswith("assets/palimpsest/items/") and p.endswith(".json")
    )
    blocks = sorted(
        p for p in contents
        if p.startswith("assets/palimpsest/blockstates/") and p.endswith(".json")
    )
    recipes = sorted(
        p for p in contents
        if p.startswith("data/palimpsest/recipe/") and p.endswith(".json")
    )
    assert len(definitions) == 61, f"Expected 61 items, found {len(definitions)}"
    assert len(blocks) == 28, f"Expected 28 blocks, found {len(blocks)}"
    assert len(recipes) == 61, f"Expected 61 recipes, found {len(recipes)}"

    for definition in definitions:
        item_id = pathlib.PurePosixPath(definition).stem
        name_key = f"item.palimpsest.{item_id}"
        assert ru.get(name_key, "").strip(), f"Missing Russian name: {name_key}"
        assert en.get(name_key, "").strip(), f"Missing English name: {name_key}"
        model = read_json(definition)["model"]["model"]
        assert model.startswith("palimpsest:"), (definition, model)
        model_path = f"assets/palimpsest/models/{model.split(':', 1)[1]}.json"
        assert model_path in contents, f"Missing model for {item_id}: {model_path}"

    for state in blocks:
        block_id = pathlib.PurePosixPath(state).stem
        key = f"block.palimpsest.{block_id}"
        assert ru.get(key, "").strip(), f"Missing Russian block name: {key}"
        assert en.get(key, "").strip(), f"Missing English block name: {key}"
        model_path = f"assets/palimpsest/models/block/{block_id}.json"
        data = read_json(model_path)
        assert data["parent"] == "minecraft:block/cube_bottom_top", model_path
        assert all(k in data["textures"] for k in ("top", "side", "bottom"))
        assert read_json(state)["variants"][""]["model"] == f"palimpsest:block/{block_id}"

    checked_textures = set()
    for name in sorted(contents):
        if not name.startswith("assets/palimpsest/models/") or not name.endswith(".json"):
            continue
        model = read_json(name)
        for texture in model.get("textures", {}).values():
            if not texture.startswith("palimpsest:"):
                continue
            relative = texture.split(":", 1)[1]
            png = f"assets/palimpsest/textures/{relative}.png"
            assert png in contents, f"{name} points to missing texture: {png}"
            checked_textures.add(png)
            raw = archive.read(png)
            assert raw[:8] == b"\x89PNG\r\n\x1a\n", f"Bad PNG: {png}"
            width, height = struct.unpack(">II", raw[16:24])
            assert 16 <= width <= 64 and 16 <= height <= 64, (png, width, height)

    for name in recipes:
        recipe = read_json(name)
        assert recipe["type"].startswith("minecraft:crafting_"), name
        if recipe["type"] == "minecraft:crafting_shapeless":
            assert recipe.get("ingredients"), name
            assert all(isinstance(i, str) and ":" in i for i in recipe["ingredients"]), (
                f"Old-style or empty ingredients: {name}"
            )
        if recipe["type"] == "minecraft:crafting_shaped":
            assert recipe.get("pattern") and recipe.get("key"), name
        result = recipe["result"]
        assert result.get("id", "").startswith("palimpsest:"), name

    assert len(checked_textures) >= 89, (
        f"Expected 32 tools, revised quill and top+side of 28 stations, got {len(checked_textures)}"
    )
    print(
        f"PASSED: {len(definitions)} localized items, {len(blocks)} engraved stations, "
        f"{len(recipes)} validated recipes, {len(checked_textures)} linked PNG textures"
    )
