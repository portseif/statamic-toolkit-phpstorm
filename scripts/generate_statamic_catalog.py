from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from html import unescape
from pathlib import Path
from time import sleep
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import re

USER_AGENT = "Mozilla/5.0"
BASE_URL = "https://statamic.dev"
ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/kotlin/com/antlers/support/statamic/StatamicCatalogGenerated.kt"


def fetch(url: str, attempts: int = 3) -> str:
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            request = Request(url, headers={"User-Agent": USER_AGENT})
            with urlopen(request, timeout=30) as response:
                return response.read().decode("utf-8")
        except (HTTPError, URLError, TimeoutError) as exc:
            last_error = exc
            if attempt == attempts:
                break
            sleep(0.5 * attempt)

    raise RuntimeError(f"Failed to fetch {url}") from last_error


def extract_article(html: str) -> str:
    match = re.search(r'<article id="content"[^>]*>(.*?)</article>', html, re.S)
    if not match:
        raise RuntimeError("Could not find article content")

    return match.group(1)


def strip_html(raw: str) -> str:
    raw = re.sub(r"<!--.*?-->", "", raw, flags=re.S)
    raw = re.sub(r"<div[^>]*class=['\"]line['\"][^>]*>", "", raw)
    raw = raw.replace("</div>", "\n")
    raw = re.sub(r"<br\s*/?>", "\n", raw)
    raw = re.sub(r"</p\s*>", "\n\n", raw)
    raw = re.sub(r"</li\s*>", "\n", raw)
    raw = re.sub(r"<[^>]+>", "", raw)
    raw = unescape(raw).replace("\xa0", " ")
    raw = raw.replace("\r", "")
    raw = re.sub(r"\n[ \t]+", "\n", raw)
    raw = re.sub(r"[ \t]+\n", "\n", raw)
    raw = re.sub(r"\n{3,}", "\n\n", raw)
    return raw.strip()


def trim_example(example: str | None) -> str | None:
    if not example:
        return None

    lines = example.splitlines()
    max_lines = 12
    if len(lines) > max_lines:
        lines = lines[:max_lines] + ["..."]

    trimmed = "\n".join(lines).strip()
    if len(trimmed) > 900:
        trimmed = f"{trimmed[:900].rstrip()}\n..."

    return trimmed


def kotlin_string(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    escaped = escaped.replace("\n", "\\n")
    return f'"{escaped}"'


def tag_name_from_path(path: str, display_name: str, example: str | None) -> str:
    slug = path.rsplit("/", 1)[-1]
    if ":" in display_name:
        first, rest = slug.split("-", 1)
        default = f"{first}:{rest.replace('-', '_')}".lower()
    elif "_" in display_name:
        default = display_name.lower()
    else:
        default = slug.lower()

    return infer_tag_name(default, example)


def first_paragraph(article: str) -> str:
    paragraph = re.search(r"<p>(.*?)</p>", article, re.S)
    return strip_html(paragraph.group(1)) if paragraph else ""


def first_antlers_example(article: str) -> str | None:
    match = re.search(
        r"""<pre><code[^>]*data-lang=['"]antlers['"][^>]*>(.*?)</code></pre>""",
        article,
        re.S,
    )
    if not match:
        return None

    return trim_example(strip_html(match.group(1)))


def infer_tag_name(default: str, example: str | None) -> str:
    if not example:
        return default

    candidates = [default, default.replace("-", "_"), default.replace("_", "-")]
    for candidate in dict.fromkeys(candidates):
        pattern = rf"""\{{\{{\s*/?{re.escape(candidate)}(?=[:\s}}/])"""
        if re.search(pattern, example):
            return candidate

    return default


def infer_modifier_name(default: str, example: str | None) -> str:
    if not example:
        return default

    candidates = [default, default.replace("-", "_"), default.replace("_", "-")]
    for candidate in dict.fromkeys(candidates):
        pattern = rf"""\|\s*{re.escape(candidate)}(?=[:(\s}}])"""
        if re.search(pattern, example):
            return candidate

    return default


def parse_tags() -> list[dict[str, str | None]]:
    html = fetch(f"{BASE_URL}/tags/all-tags")
    article = extract_article(html)
    rows = re.findall(
        r"""<tr>\s*<td>\s*<a href="([^"]+)">([^<]+)</a>\s*</td>\s*<td>\s*(.*?)\s*</td>\s*</tr>""",
        article,
        re.S,
    )

    def build_item(row: tuple[str, str, str]) -> dict[str, str | None]:
        path, display_name, desc_html = row
        page_article = extract_article(fetch(f"{BASE_URL}{path}"))
        example = first_antlers_example(page_article)
        return {
            "name": tag_name_from_path(path, display_name, example),
            "displayName": display_name,
            "description": first_paragraph(page_article) or strip_html(desc_html),
            "example": example,
            "url": f"{BASE_URL}{path}",
        }

    with ThreadPoolExecutor(max_workers=12) as executor:
        return list(executor.map(build_item, rows))


def parse_modifiers() -> list[dict[str, str | None]]:
    html = fetch(f"{BASE_URL}/modifiers/all-modifiers")
    article = extract_article(html)
    section_match = re.search(r"<h1>All Modifiers</h1>(.*)", article, re.S)
    section = section_match.group(1) if section_match else article

    items: list[tuple[str, str]] = []
    seen_names: set[str] = set()
    for path, name in re.findall(r"""<li><a href="(/modifiers/[^"]+)">([^<]+)</a></li>""", section):
        if name in seen_names:
            continue
        seen_names.add(name)
        items.append((path, name))

    def build_item(item: tuple[str, str]) -> dict[str, str | None]:
        path, name = item
        page_article = extract_article(fetch(f"{BASE_URL}{path}"))
        example = first_antlers_example(page_article)
        return {
            "name": infer_modifier_name(name, example),
            "displayName": name,
            "description": first_paragraph(page_article),
            "example": example,
            "url": f"{BASE_URL}{path}",
        }

    with ThreadPoolExecutor(max_workers=12) as executor:
        return list(executor.map(build_item, items))


def parse_variables() -> list[dict[str, str | None]]:
    html = fetch(f"{BASE_URL}/variables/all-variables")
    article = extract_article(html)
    rows = re.findall(
        r"""<tr>\s*<td>\s*<a href="([^"]+)">([^<]+)</a>\s*</td>\s*<td>\s*(.*?)\s*</td>\s*</tr>""",
        article,
        re.S,
    )

    def build_item(row: tuple[str, str, str]) -> dict[str, str | None]:
        path, display_name, desc_html = row
        page_article = extract_article(fetch(f"{BASE_URL}{path}"))
        example = first_antlers_example(page_article)
        return {
            "name": infer_variable_name(path, example),
            "displayName": display_name,
            "description": first_paragraph(page_article) or strip_html(desc_html),
            "example": example,
            "url": f"{BASE_URL}{path}",
        }

    with ThreadPoolExecutor(max_workers=12) as executor:
        return list(executor.map(build_item, rows))


def infer_variable_name(path: str, example: str | None) -> str:
    default = path.rsplit("/", 1)[-1].lower()
    if not example:
        return default

    candidates = [default, default.replace("-", "_"), default.replace("_", "-")]
    for candidate in dict.fromkeys(candidates):
        pattern = rf"""\{{\{{\s*{re.escape(candidate)}(?=[:\s}}|])"""
        if re.search(pattern, example):
            return candidate

    return default


def render_item(item: dict[str, str | None]) -> str:
    example = "null" if not item["example"] else kotlin_string(item["example"])
    return (
        "    StatamicDocItem("
        f"name = {kotlin_string(item['name'])}, "
        f"displayName = {kotlin_string(item['displayName'])}, "
        f"description = {kotlin_string(item['description'])}, "
        f"example = {example}, "
        f"url = {kotlin_string(item['url'])}"
        ")"
    )


def render(items: list[dict[str, str | None]], property_name: str) -> str:
    sorted_items = sorted(items, key=lambda item: item["name"])
    body = ",\n".join(render_item(item) for item in sorted_items)
    return f"internal val {property_name}: List<StatamicDocItem> = listOf(\n{body}\n)\n"


def main() -> None:
    tags = parse_tags()
    modifiers = parse_modifiers()
    variables = parse_variables()
    output = f"""package com.antlers.support.statamic

// Generated from https://statamic.dev/tags/all-tags and https://statamic.dev/modifiers/all-modifiers.
// Refresh with scripts/generate_statamic_catalog.py.

{render(tags, "GENERATED_TAGS")}
{render(modifiers, "GENERATED_MODIFIERS")}
{render(variables, "GENERATED_VARIABLES")}
"""
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(output)
    print(f"Wrote {OUTPUT}")
    print(f"Tags: {len(tags)}")
    print(f"Modifiers: {len(modifiers)}")
    print(f"Variables: {len(variables)}")


if __name__ == "__main__":
    main()
