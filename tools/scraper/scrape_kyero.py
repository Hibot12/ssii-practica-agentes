import argparse
import asyncio
import json
import os
import random
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urljoin, urlsplit, urlunsplit

import nodriver as uc


BASE_URL = "https://www.kyero.com/es/espana-propiedad-en-venta-0l55529"
DEFAULT_OUTPUT = Path(__file__).resolve().parents[2] / "agents/src/es/upm/ssii/reagent/viviendas.json"
RESIDENTIAL_CATEGORY_URLS = [
    "https://www.kyero.com/es/espana-apartamentos-en-venta-0l55529g1",
    "https://www.kyero.com/es/espana-villas-en-venta-0l55529g2",
    "https://www.kyero.com/es/espana-casas-adosadas-en-venta-0l55529g3",
    "https://www.kyero.com/es/espana-casas-de-campo-en-venta-0l55529g4",
]
TILE_SELECTOR = 'a[data-testid="property-tile"]'
NON_RESIDENTIAL_URLS = (
    "land-for-sale",
    "plot-for-sale",
    "commercial-property",
    "commercial-for-sale",
    "business-for-sale",
    "garage-for-sale",
    "terreno-en-venta",
    "parcela-en-venta",
    "propiedad-comercial",
    "negocio-en-venta",
    "garaje-en-venta",
)
NON_RESIDENTIAL_TEXT = re.compile(
    r"\b(land|plot|commercial|business|garage|terreno|parcela|comercial|negocio|garaje)\b",
    re.IGNORECASE,
)


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).split())


def to_int(value: Any) -> int:
    if value is None:
        return 0
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return int(value)
    if isinstance(value, dict):
        for key in ("value", "amount", "price"):
            number = to_int(value.get(key))
            if number:
                return number
        return 0

    digits = "".join(char for char in str(value) if char.isdigit())
    return int(digits) if digits else 0


def to_float(value: Any) -> float:
    if value is None:
        return 0.0
    if isinstance(value, bool):
        return float(value)
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(str(value).strip().replace(",", "."))
    except ValueError:
        return 0.0


def find_browser() -> str | None:
    browser_path = os.environ.get("KYERO_BROWSER_PATH")
    if browser_path and Path(browser_path).exists():
        return browser_path

    paths = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    ]

    local = Path.home() / "AppData" / "Local"
    for pattern in (
        "Microsoft/EdgeCore/*/msedge.exe",
        "Microsoft/EdgeWebView/Application/*/msedge.exe",
        "Google/Chrome/Application/chrome.exe",
    ):
        paths.extend(str(path) for path in local.glob(pattern))

    for path in paths:
        if Path(path).exists():
            return path
    return None


def walk_json(value: Any):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk_json(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk_json(child)


def node_types(node: dict[str, Any]) -> set[str]:
    value = node.get("@type") or node.get("type")
    if isinstance(value, str):
        return {value}
    if isinstance(value, list):
        return {str(item) for item in value}
    return set()


def find_node(nodes: list[dict[str, Any]], wanted: set[str]) -> dict[str, Any]:
    for node in nodes:
        if node_types(node) & wanted:
            return node
    return {}


def json_breadcrumbs(nodes: list[dict[str, Any]]) -> list[str]:
    breadcrumb = find_node(nodes, {"BreadcrumbList"})
    items = breadcrumb.get("itemListElement")
    if not isinstance(items, list):
        return []

    names = []
    for item in sorted(items, key=lambda item: to_int(item.get("position"))):
        name = clean_text(item.get("name"))
        if name and name not in names:
            names.append(name)
    return names


def read_schema_data(scripts: list[str]) -> dict[str, Any]:
    nodes = []
    for script in scripts:
        try:
            data = json.loads(script.strip())
        except (AttributeError, json.JSONDecodeError):
            continue
        nodes.extend(node for node in walk_json(data) if isinstance(node, dict))

    home = find_node(nodes, {"SingleFamilyResidence", "Apartment"})
    offer = find_node(nodes, {"Offer"})
    address = find_node(nodes, {"PostalAddress"})
    agency = find_node(nodes, {"Organization"})

    if not offer and isinstance(home.get("offers"), dict):
        offer = home["offers"]
    if not address and isinstance(home.get("address"), dict):
        address = home["address"]

    image = home.get("image")
    if isinstance(image, list):
        image = image[0] if image else ""

    return {
        "precio": to_int(offer.get("price")),
        "moneda": clean_text(offer.get("priceCurrency")),
        "habitaciones": to_int(home.get("numberOfBedrooms")),
        "banos": to_int(home.get("numberOfBathroomsTotal")),
        "superficieM2": to_int(home.get("floorSize")),
        "ciudad": clean_text(address.get("addressLocality")),
        "provincia": clean_text(address.get("addressRegion")),
        "latitud": to_float(home.get("latitude")),
        "longitud": to_float(home.get("longitude")),
        "agencia": clean_text(agency.get("name")),
        "imagenPrincipal": clean_text(image),
        "breadcrumbs": json_breadcrumbs(nodes),
        "descripcion": clean_text(home.get("description")),
    }


def property_id(url: str) -> str:
    match = re.search(r"/property/(\d+)", url)
    return match.group(1) if match else ""


def normalize_url(url: str) -> str:
    url = clean_text(url)
    if not url:
        return ""
    parts = urlsplit(url)
    return urlunsplit(
        (parts.scheme.lower(), parts.netloc.lower(), parts.path.rstrip("/"), "", "")
    )


def property_type(title: str, breadcrumbs: list[str]) -> str:
    for crumb in reversed(breadcrumbs):
        if crumb.lower().endswith((" for sale", " en venta")):
            return clean_text(re.sub(r"\s+(for sale|en venta)$", "", crumb, flags=re.IGNORECASE))

    match = re.search(r"^(.+?)\s+(in|en)\s+", title, re.IGNORECASE)
    return clean_text(match.group(1)) if match else ""


def location_parts(breadcrumbs: list[str], city: str, province: str) -> dict[str, str]:
    parts = [part for part in breadcrumbs if part and part.lower() not in {"home", "hogar"}]
    if parts and parts[-1].lower().endswith((" for sale", " en venta")):
        parts.pop()

    zone = ""
    if len(parts) >= 5:
        candidate = parts[-2]
        if candidate.lower() not in {city.lower(), province.lower()}:
            zone = candidate

    return {
        "pais": clean_text(parts[0]) if len(parts) >= 1 else "",
        "region": clean_text(parts[1]) if len(parts) >= 2 else "",
        "zona": clean_text(zone),
    }


def fallback_reference(meta_description: str) -> str:
    if "|" not in meta_description:
        return ""
    return clean_text(meta_description.rsplit("|", 1)[-1])


def unique_texts(values) -> list[str]:
    result = []
    for value in values:
        value = clean_text(value)
        if value and value not in result:
            result.append(value)
    return result


def split_feature_values(value: str) -> list[str]:
    value = re.sub(
        r"\s+(Rooms|Other|Heating|Utilities|Views|Kitchen|Bathroom|Security|Habitaciones|Otros|Calefacción|Utilidades|Vistas|Cocina|Baño|Seguridad):",
        r", \1:",
        value,
    )
    return unique_texts(part.strip(" .") for part in value.split(","))


def parse_features(items: list[str]) -> dict[str, list[str]]:
    labels = [
        ("Energy & utilities", "utilities"),
        ("Energía & utilidades", "utilities"),
        ("Interior features", "interior"),
        ("Características interiores", "interior"),
        ("Location", "location"),
        ("Ubicación", "location"),
        ("Outside", "outside"),
        ("Fuera", "outside"),
        ("Parking", "parking"),
        ("Estacionamiento", "parking"),
        ("Quality", "quality"),
        ("Calidad", "quality"),
        ("Leisure", "leisure"),
        ("Ocio", "leisure"),
        ("Security", "security"),
        ("Seguridad", "security"),
        ("Views", "views"),
        ("Vistas", "views"),
        ("Pool", "pool"),
        ("Piscina", "pool"),
    ]

    features: dict[str, list[str]] = {}
    for item in items:
        item = clean_text(item)
        for label, key in labels:
            if item.lower().startswith(label.lower()):
                values = split_feature_values(item[len(label) :])
                if values:
                    features[key] = unique_texts(features.get(key, []) + values)
                break
    return features


def price_m2(price: int, area: int) -> int:
    if price <= 0 or area <= 0:
        return 0
    return round(price / area)


def nearest_airport(text: str, city: str) -> dict[str, Any]:
    text = clean_text(text)
    headings = [
        f"Airports near the centre of {city}",
        f"Aeropuertos cerca del centro de {city}",
    ]
    for heading in headings:
        if city and text.lower().startswith(heading.lower()):
            text = clean_text(text[len(heading) :])
            break

    matches = re.findall(
        r"([A-Za-zÀ-ÿ' .-]*(?:airport|Aeropuerto)[A-Za-zÀ-ÿ' .-]*),\s*(\d+)\s*km",
        text,
        re.IGNORECASE,
    )
    if not matches:
        return {"aeropuertoMasCercano": "", "distanciaAeropuertoKm": 0}

    name, distance = min(matches, key=lambda item: to_int(item[1]))
    return {
        "aeropuertoMasCercano": clean_text(name),
        "distanciaAeropuertoKm": to_int(distance),
    }


def image_url(url: str) -> str:
    url = clean_text(url)
    match = re.search(r"https://production-kyero-property-images[^\s\"']+", url)
    return match.group(0) if match else url


def property_images(values: list[str]) -> list[str]:
    return unique_texts(
        image_url(value)
        for value in values
        if "property-images" in value and "/agents/" not in value
    )


def agent_name(text: str) -> str:
    text = clean_text(text)
    if text.startswith("Agent information "):
        text = text[len("Agent information ") :]
    if text.startswith("Información del agente "):
        text = text[len("Información del agente ") :]

    for marker in (
        " English spoken",
        " More about",
        " Request more information",
        " Habla ",
        " Más información",
        " Solicitar más información",
        " Las visitas",
    ):
        index = text.find(marker)
        if index > 0:
            return clean_text(text[:index])
    return text


def feature_flags(features: dict[str, list[str]]) -> dict[str, bool]:
    text = " ".join(
        value.lower()
        for values in features.values()
        for value in values
    )
    return {
        "tienePiscina": "pool" in text or "piscina" in text,
        "tieneParking": "parking" in text or "garage" in text or "estacionamiento" in text or "garaje" in text,
        "tieneTerraza": "terrace" in text or "solarium" in text or "terraza" in text,
        "tieneJardin": "garden" in text or "jardín" in text or "jardin" in text,
        "aireAcondicionado": "air conditioning" in text or "aire acondicionado" in text,
        "amueblado": ("furnished" in text or "amueblado" in text) and "unfurnished" not in text and "sin amueblar" not in text,
        "cercaPlaya": "near beach" in text or "close to beach" in text or "cerca de la playa" in text,
    }


def is_bad_url(url: str) -> bool:
    url = url.lower()
    return any(piece in url for piece in NON_RESIDENTIAL_URLS)


def is_house(vivienda: dict[str, Any]) -> bool:
    if vivienda["habitaciones"] <= 0:
        return False

    text = " ".join(
        clean_text(vivienda.get(key))
        for key in ("tipo", "titulo", "url")
    )
    return NON_RESIDENTIAL_TEXT.search(text) is None


async def read_json(tab, script: str) -> Any:
    result = await tab.evaluate(script, return_by_value=True)
    if not isinstance(result, str):
        return None
    return json.loads(result)


async def wait_page_loaded(tab) -> bool:
    script = """
    (() => JSON.stringify({
      url: location.href,
      title: document.title,
      mainLength: document.querySelector("main")?.innerText?.length || 0
    }))()
    """

    for _ in range(20):
        try:
            state = await read_json(tab, script)
        except Exception:
            state = None

        if (
            isinstance(state, dict)
            and "/captcha/" not in state.get("url", "")
            and not state.get("title", "").startswith("Just a moment")
            and state.get("mainLength", 0) > 0
        ):
            return True
        await tab.sleep(1)

    return False


async def accept_cookies(tab) -> None:
    script = r"""
    (() => {
      const buttons = Array.from(document.querySelectorAll('button, a, [role="button"]'));
      const accept = /(accept|agree|allow all|allow cookies|got it|ok|aceptar|permitir|de acuerdo)/i;
      const reject = /(reject|decline|settings|preferences|manage|customize|rechazar|configuraci[oó]n|preferencias)/i;

      for (const button of buttons) {
        const text = (button.innerText || button.textContent || button.ariaLabel || button.value || '').trim();
        const id = button.id || '';
        const className = button.className || '';
        const visible = button.offsetWidth > 0 && button.offsetHeight > 0;

        if (!visible || reject.test(text)) continue;
        if (accept.test(text) || /accept/i.test(id) || /accept/i.test(className)) {
          button.click();
          return JSON.stringify(true);
        }
      }

      return JSON.stringify(false);
    })()
    """
    try:
        clicked = await read_json(tab, script)
    except Exception:
        return

    if clicked:
        await tab.sleep(0.5)


def search_url(base_url: str, page: int) -> str:
    if page == 1:
        return base_url
    separator = "&" if "?" in base_url else "?"
    return f"{base_url}{separator}page={page}"


async def property_urls(browser, url: str, page: int, retries: int) -> list[str]:
    script = f"""
    (() => JSON.stringify(
      Array.from(document.querySelectorAll({json.dumps(TILE_SELECTOR)}))
        .map((link) => link.href || link.getAttribute("href"))
        .filter(Boolean)
    ))()
    """

    for attempt in range(retries + 1):
        tab = await browser.get(search_url(url, page))
        await tab.sleep(2 + attempt)
        await accept_cookies(tab)

        links = await read_json(tab, script) or []
        if links:
            return [urljoin(BASE_URL, link) for link in links]

        print(f"[search] pagina {page} vacia, intento {attempt + 1}", flush=True)

    return []


async def scrape_property(browser, url: str) -> dict[str, Any] | None:
    tab = await browser.get(url)
    await tab.sleep(0.7)
    await accept_cookies(tab)
    if not await wait_page_loaded(tab):
        return None

    script = """
    (() => {
      const clean = (value) => (value || "").replace(/\\s+/g, " ").trim();
      const unique = (values) => Array.from(new Set(values.map(clean).filter(Boolean)));
      const mainText = document.querySelector("main")?.innerText || "";
      const referenceMatch = mainText.match(/(?:Property reference|Referencia de la propiedad):\\s*([^\\n]+?)(?:\\s+You might also like|\\s+También te puede interesar|\\s+Receive property|$)/i);
      const addedMatch = mainText.match(/(?:Added\\s+(?:today|yesterday|\\d+\\s+\\w+\\s+ago)|Añadid[oa]\\s+(?:hoy|ayer|hace\\s+(?:(?:alrededor|más|menos)\\s+de\\s+)?\\d+\\s+[^\\s]+))/i);

      return JSON.stringify({
        titulo: clean(document.querySelector("h1")?.innerText),
        canonical: document.querySelector('link[rel="canonical"]')?.href || "",
        descripcion: clean(document.querySelector('#expandable-text, .property-description')?.innerText),
        descripcionMeta: document.querySelector('meta[name="description"]')?.content || "",
        ogImage: document.querySelector('meta[property="og:image"]')?.content || "",
        breadcrumbs: unique(
          Array.from(document.querySelectorAll('[data-testid="breadcrumbs-list"] li'))
            .map((item) => item.innerText || item.textContent || "")
        ),
        keyFeatureItems: unique(
          Array.from(document.querySelectorAll('[data-testid="property_show.key_features.list_element"]'))
            .map((item) => item.innerText || item.textContent || "")
        ),
        agentSection: clean(document.querySelector('[data-testid="property_show.agent-section"]')?.innerText),
        reference: referenceMatch ? clean(referenceMatch[1]) : "",
        fechaPublicadoTexto: addedMatch ? clean(addedMatch[0]) : "",
        aeropuertoTexto: clean(document.querySelector('[data-testid="airports-card"]')?.innerText),
        imagenes: unique(
          Array.from(document.querySelectorAll("main img"))
            .map((img) => img.currentSrc || img.src || img.getAttribute("src") || "")
            .filter((src) => src && src.includes("property-images") && !src.includes("/agents/"))
        ),
        jsonLd: Array.from(document.querySelectorAll('script[type="application/ld+json"]'))
          .map((script) => script.textContent || "")
      });
    })()
    """
    page_data = await read_json(tab, script)
    if not isinstance(page_data, dict):
        return None

    schema = read_schema_data(page_data.get("jsonLd") or [])
    html_breadcrumbs = unique_texts(page_data.get("breadcrumbs") or [])
    breadcrumbs = schema.get("breadcrumbs") or html_breadcrumbs
    location = location_parts(breadcrumbs, schema["ciudad"], schema["provincia"])
    url = clean_text(page_data.get("canonical")) or url
    images = property_images(page_data.get("imagenes") or [])
    features = parse_features(page_data.get("keyFeatureItems") or [])
    flags = feature_flags(features)
    airport = nearest_airport(page_data.get("aeropuertoTexto") or "", schema["ciudad"])

    vivienda = {
        "id": property_id(url),
        "titulo": clean_text(page_data.get("titulo")),
        "url": url,
        "tipo": property_type(clean_text(page_data.get("titulo")), html_breadcrumbs),
        "precio": schema["precio"],
        "moneda": schema["moneda"],
        "precioM2": price_m2(schema["precio"], schema["superficieM2"]),
        "habitaciones": schema["habitaciones"],
        "banos": schema["banos"],
        "superficieM2": schema["superficieM2"],
        "ciudad": schema["ciudad"],
        "provincia": schema["provincia"],
        "region": location["region"],
        "zona": location["zona"],
        "pais": location["pais"],
        "latitud": schema["latitud"],
        "longitud": schema["longitud"],
        "referencia": clean_text(page_data.get("reference"))
        or fallback_reference(page_data.get("descripcionMeta") or ""),
        "fechaPublicadoTexto": clean_text(page_data.get("fechaPublicadoTexto")),
        "agencia": schema["agencia"] or agent_name(page_data.get("agentSection") or ""),
        "aeropuertoMasCercano": airport["aeropuertoMasCercano"],
        "distanciaAeropuertoKm": airport["distanciaAeropuertoKm"],
        "tienePiscina": flags["tienePiscina"],
        "tieneParking": flags["tieneParking"],
        "tieneTerraza": flags["tieneTerraza"],
        "tieneJardin": flags["tieneJardin"],
        "aireAcondicionado": flags["aireAcondicionado"],
        "amueblado": flags["amueblado"],
        "cercaPlaya": flags["cercaPlaya"],
        "imagenPrincipal": images[0]
        if images
        else schema["imagenPrincipal"] or clean_text(page_data.get("ogImage")),
        "imagenes": images,
        "caracteristicas": features,
        "descripcion": schema["descripcion"]
        or clean_text(page_data.get("descripcion"))
        or clean_text(page_data.get("descripcionMeta")),
    }

    return vivienda if is_house(vivienda) else None


async def scrape_description(browser, url: str) -> str:
    tab = await browser.get(url)
    await tab.sleep(0.7)
    await accept_cookies(tab)

    script = """
    (() => {
      const clean = (value) => (value || "").replace(/\\s+/g, " ").trim();
      return JSON.stringify({
        descripcion: clean(document.querySelector('#expandable-text, .property-description')?.innerText),
        descripcionMeta: document.querySelector('meta[name="description"]')?.content || "",
        jsonLd: Array.from(document.querySelectorAll('script[type="application/ld+json"]'))
          .map((script) => script.textContent || "")
      });
    })()
    """
    data = await read_json(tab, script)
    if not isinstance(data, dict):
        return ""

    schema = read_schema_data(data.get("jsonLd") or [])
    return (
        schema["descripcion"]
        or clean_text(data.get("descripcion"))
        or clean_text(data.get("descripcionMeta"))
    )


def load_viviendas(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    return data if isinstance(data, list) else []


def save_viviendas(path: Path, viviendas: list[dict[str, Any]]) -> None:
    path.write_text(
        json.dumps(viviendas, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def vivienda_keys(vivienda: dict[str, Any]) -> set[str]:
    url = clean_text(vivienda.get("url"))
    result = set()

    id_value = clean_text(vivienda.get("id")) or property_id(url)
    if id_value:
        result.add(f"id:{id_value}")

    url_value = normalize_url(url)
    if url_value:
        result.add(f"url:{url_value}")

    return result


def url_keys(url: str) -> set[str]:
    keys = set()
    id_value = property_id(url)
    url_value = normalize_url(url)

    if id_value:
        keys.add(f"id:{id_value}")
    if url_value:
        keys.add(f"url:{url_value}")

    return keys


def spanish_property_url(vivienda: dict[str, Any]) -> str:
    url = clean_text(vivienda.get("url"))
    if "/en/property/" in url:
        return url.replace("/en/property/", "/es/property/", 1)
    if "/es/property/" in url:
        return url

    id_value = clean_text(vivienda.get("id"))
    if id_value:
        return f"https://www.kyero.com/es/property/{id_value}"
    return ""


def remove_duplicates(viviendas: list[dict[str, Any]]) -> list[dict[str, Any]]:
    seen = set()
    result = []

    for vivienda in viviendas:
        if not isinstance(vivienda, dict):
            continue

        keys = vivienda_keys(vivienda)
        if not keys or keys & seen:
            continue

        seen.update(keys)
        result.append(vivienda)

    return result


async def enrich_descriptions(browser, output_path: Path, viviendas, args) -> None:
    missing = [
        (index, vivienda)
        for index, vivienda in enumerate(viviendas)
        if vivienda.get("url") and not clean_text(vivienda.get("descripcion"))
    ]

    if args.description_limit > 0:
        missing = missing[: args.description_limit]

    print(f"[descriptions] faltan {len(missing)} descripciones", flush=True)
    for index, vivienda in missing:
        await asyncio.sleep(random.uniform(args.min_delay, args.max_delay))
        print(f"[descriptions] {vivienda['url']}", flush=True)

        description = await scrape_description(browser, vivienda["url"])
        if description:
            viviendas[index]["descripcion"] = description
            save_viviendas(output_path, viviendas)


async def scrape_existing(browser, output_path: Path, args) -> None:
    original = remove_duplicates(load_viviendas(Path(args.from_existing)))
    viviendas = remove_duplicates(load_viviendas(output_path)) if args.resume else []
    seen = set()
    for vivienda in viviendas:
        seen.update(vivienda_keys(vivienda))

    print(f"[existing] {len(original)} viviendas de entrada", flush=True)
    print(f"[existing] {len(viviendas)} viviendas ya guardadas", flush=True)

    for item in original:
        if args.max_listings > 0 and len(viviendas) >= args.max_listings:
            break

        keys = vivienda_keys(item)
        if keys and keys & seen:
            continue

        url = spanish_property_url(item)
        if not url:
            continue

        await asyncio.sleep(random.uniform(args.min_delay, args.max_delay))
        print(f"[property] {len(viviendas) + 1}/{len(original)} {url}", flush=True)

        vivienda = None
        for attempt in range(3):
            vivienda = await scrape_property(browser, url)
            if vivienda is not None:
                break
            if attempt < 2:
                print(f"[property] reintento {attempt + 1}: {url}", flush=True)
                await asyncio.sleep(10 + attempt * 10)

        if vivienda is None:
            print("[property] descartada", flush=True)
            continue

        keys = vivienda_keys(vivienda)
        if keys & seen:
            print("[property] repetida", flush=True)
            continue

        viviendas.append(vivienda)
        seen.update(keys)
        save_viviendas(output_path, viviendas)

    save_viviendas(output_path, viviendas)
    print(f"[done] guardadas {len(viviendas)} viviendas en {output_path}", flush=True)


async def close_browser(browser) -> None:
    try:
        await browser.aclose()
    except Exception:
        pass


async def run(args) -> int:
    browser_path = args.browser_path or find_browser()
    if not browser_path:
        print("No se encontro Chrome o Edge.", file=sys.stderr, flush=True)
        return 2

    output_path = Path(args.output).resolve()
    profile_path = Path(args.user_data_dir).resolve()
    profile_path.mkdir(parents=True, exist_ok=True)

    browser_args = [
        "--disable-blink-features=AutomationControlled",
        "--window-size=1365,900",
    ]
    if args.proxy_server:
        browser_args.append(f"--proxy-server={args.proxy_server}")
        if args.proxy_bypass:
            browser_args.append(f"--proxy-bypass-list={args.proxy_bypass}")
        print(f"[proxy] usando {args.proxy_server}", flush=True)

    browser = await uc.start(
        headless=not args.headed,
        browser_executable_path=browser_path,
        user_data_dir=str(profile_path),
        lang="es-ES",
        browser_args=browser_args,
    )

    try:
        viviendas = remove_duplicates(load_viviendas(output_path)) if args.resume else []
        seen = set()
        for vivienda in viviendas:
            seen.update(vivienda_keys(vivienda))

        if viviendas:
            print(f"[resume] {len(viviendas)} viviendas cargadas", flush=True)
            save_viviendas(output_path, viviendas)

        if args.enrich_descriptions:
            await enrich_descriptions(browser, output_path, viviendas, args)
            if args.enrich_only:
                return 0

        if args.from_existing:
            await scrape_existing(browser, output_path, args)
            return 0

        sources = args.search_url or [BASE_URL]
        if args.residential_categories:
            sources = RESIDENTIAL_CATEGORY_URLS

        limit = args.max_listings if args.max_listings > 0 else None
        page_limit = args.max_pages if args.max_pages > 0 else None
        tried = set()

        for source_index, source in enumerate(sources):
            page = args.start_page if source_index == 0 and args.start_page > 0 else 1
            empty_pages = 0

            while page_limit is None or page <= page_limit:
                print(f"[search] pagina {page}: {source}", flush=True)
                links = await property_urls(browser, source, page, args.empty_retries)

                new_links = []
                for link in links:
                    keys = url_keys(link)
                    if keys and not (keys & seen) and not (keys & tried):
                        new_links.append(link)

                if not new_links:
                    empty_pages += 1
                    if empty_pages >= args.max_empty_pages:
                        break
                    page += 1
                    await asyncio.sleep(args.search_delay)
                    continue

                saved_before = len(viviendas)

                for link in new_links:
                    if limit is not None and len(viviendas) >= limit:
                        break

                    keys = url_keys(link)
                    tried.update(keys)

                    if is_bad_url(link):
                        print(f"[property] descartada: {link}", flush=True)
                        continue

                    await asyncio.sleep(random.uniform(args.min_delay, args.max_delay))
                    print(f"[property] {len(viviendas) + 1}: {link}", flush=True)

                    vivienda = await scrape_property(browser, link)
                    if vivienda is None:
                        print("[property] descartada", flush=True)
                        continue

                    keys = vivienda_keys(vivienda)
                    if keys & seen:
                        print("[property] repetida", flush=True)
                        continue

                    viviendas.append(vivienda)
                    seen.update(keys)
                    save_viviendas(output_path, viviendas)

                if limit is not None and len(viviendas) >= limit:
                    break

                empty_pages = empty_pages + 1 if len(viviendas) == saved_before else 0
                if empty_pages >= args.max_empty_pages:
                    break

                page += 1
                await asyncio.sleep(args.search_delay)

            if limit is not None and len(viviendas) >= limit:
                break

        save_viviendas(output_path, viviendas)
        print(f"[done] guardadas {len(viviendas)} viviendas en {output_path}", flush=True)
        return 0
    finally:
        await close_browser(browser)


def build_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default=str(DEFAULT_OUTPUT))
    parser.add_argument("--max-listings", type=int, default=0)
    parser.add_argument("--max-pages", type=int, default=0)
    parser.add_argument("--start-page", type=int, default=1)
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--from-existing")
    parser.add_argument("--enrich-descriptions", action="store_true")
    parser.add_argument("--enrich-only", action="store_true")
    parser.add_argument("--description-limit", type=int, default=0)
    parser.add_argument("--empty-retries", type=int, default=3)
    parser.add_argument("--max-empty-pages", type=int, default=5)
    parser.add_argument("--search-url", action="append")
    parser.add_argument("--residential-categories", action="store_true")
    parser.add_argument("--min-delay", type=float, default=0.3)
    parser.add_argument("--max-delay", type=float, default=0.8)
    parser.add_argument("--search-delay", type=float, default=2.0)
    parser.add_argument("--browser-path")
    parser.add_argument("--user-data-dir", default=".kyero-browser-profile")
    parser.add_argument("--headed", action="store_true")
    parser.add_argument(
        "--proxy-server",
        help='Proxy a usar para todas las peticiones del navegador. '
             'Formatos: "http://host:puerto", "socks5://host:puerto". '
             'Para auth Chrome solo acepta IP whitelist en el proveedor (no user:pass en URL).',
    )
    parser.add_argument(
        "--proxy-bypass",
        help='Lista separada por ";" de hosts que no pasan por el proxy '
             '(p.ej. "localhost;127.0.0.1").',
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.min_delay > args.max_delay:
        raise SystemExit("--min-delay debe ser menor o igual que --max-delay")
    return asyncio.run(run(args))


if __name__ == "__main__":
    raise SystemExit(main())
