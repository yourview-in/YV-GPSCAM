import glob
import json
import os
import re


def fix_packages_and_imports():
    new_pkg_raw = "in.yourview.yvgpscam"
    new_pkg_kt = "`in`.yourview.yvgpscam"

    all_kt = glob.glob("app/src/**/*.kt", recursive=True)
    all_java = glob.glob("app/src/**/*.java", recursive=True)

    prefixes = (
        new_pkg_raw,
        "android",
        "androidx",
        "com.google",
        "com.android",
        "com.squareup",
        "okhttp3",
        "okio",
        "retrofit2",
        "kotlin",
        "kotlinx",
        "java",
        "javax",
        "org",
        "io",
        "dagger",
        "hilt",
        "net",
        "coil",
        "bumptech",
        "glide",
    )

    old_pkgs = set()
    for p in all_kt + all_java:
        txt = open(p, encoding="utf-8", errors="ignore").read()
        m = re.search(r"^\s*package\s+([`a-zA-Z0-9_.]+)", txt, re.M)
        if m and not m.group(1).replace("`", "").startswith(prefixes):
            old_pkgs.add(".".join(m.group(1).replace("`", "").split(".")[:3]))

    for old in old_pkgs:
        for root, _, files in os.walk("app"):
            for f in files:
                if f.endswith((".kt", ".java", ".xml", ".gradle", ".kts", ".pro")):
                    fp = os.path.join(root, f)
                    c = open(fp, encoding="utf-8", errors="ignore").read()
                    if old in c:
                        open(fp, "w", encoding="utf-8").write(c.replace(old, new_pkg_raw))

    for p in all_kt:
        t = open(p, encoding="utf-8", errors="ignore").read()
        fx = re.sub(
            r"(^\s*(?:package|import)\s+)in\.yourview\.yvgpscam",
            r"\1`in`.yourview.yvgpscam",
            t,
            flags=re.M,
        )
        if fx != t:
            open(p, "w", encoding="utf-8").write(fx)

    cls_map = {}
    for p in all_kt:
        t = open(p, encoding="utf-8", errors="ignore").read()
        mp = re.search(r"^\s*package\s+([`a-zA-Z0-9_.]+)", t, re.M)
        pkg = mp.group(1) if mp else new_pkg_kt
        for mc in re.finditer(
            r"\b(?:class|interface|object|enum\s+class|annotation\s+class|data\s+class|sealed\s+class)\s+([A-Za-z0-9_]+)",
            t,
        ):
            cls_map[mc.group(1)] = pkg

    for p in all_kt:
        t = open(p, encoding="utf-8", errors="ignore").read()
        lines, chg = t.splitlines(), False
        mp = re.search(r"^\s*package\s+([`a-zA-Z0-9_.]+)", t, re.M)
        cur = mp.group(1) if mp else new_pkg_kt
        for i, line in enumerate(lines):
            mi = re.match(r"^(\s*import\s+)([`a-zA-Z0-9_.]+)\.([A-Z][A-Za-z0-9_]*)(.*)$", line)
            if (
                mi
                and mi.group(2).replace("`", "").startswith(new_pkg_raw)
                and mi.group(3) in cls_map
                and cls_map[mi.group(3)] != mi.group(2)
            ):
                lines[i] = f"{mi.group(1)}{cls_map[mi.group(3)]}.{mi.group(3)}{mi.group(4)}"
                chg = True
        if p.endswith("AppDatabase.kt"):
            for tgt in ["CapturedPhotoEntity", "OfflineRegionEntity", "CapturedPhotoDao", "OfflineRegionDao"]:
                if tgt in cls_map and cls_map[tgt] != cur and f"import {cls_map[tgt]}.{tgt}" not in "\n".join(lines):
                    for idx, line in enumerate(lines):
                        if line.startswith("package "):
                            lines.insert(idx + 1, f"import {cls_map[tgt]}.{tgt}")
                            chg = True
                            break
        if chg:
            open(p, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def _is_real_secret(value, placeholder="placeholder"):
    if not value:
        return False
    stripped = value.strip()
    return (
        bool(stripped)
        and stripped != placeholder
        and not stripped.startswith("000000000000-")
        and not stripped.startswith("MY_")
    )


def inject_keys_and_google_maps():
    mk = (os.environ.get("MAPS_API_KEY") or "").strip()
    dk = (os.environ.get("GOOGLE_DRIVE_API_KEY") or "").strip()
    gk = (os.environ.get("GEMINI_API_KEY") or "").strip()
    cid = (os.environ.get("WEB_CLIENT_ID") or "").strip()
    if cid.startswith("://"):
        cid = ""

    # 1. Preserve checked-in app/google-services.json (including Android OAuth SHA-1 clients);
    #    only patch web client / API key in-place when real secrets are supplied, or bootstrap if missing.
    gs_path = "app/google-services.json"
    if os.path.exists(gs_path):
        if _is_real_secret(cid) or _is_real_secret(dk):
            try:
                data = json.load(open(gs_path, encoding="utf-8"))
                clients = data.get("client", [])
                for client in clients:
                    if _is_real_secret(cid):
                        oauth_clients = client.setdefault("oauth_client", [])
                        web_entry = next((c for c in oauth_clients if c.get("client_type") == 3), None)
                        if web_entry is not None:
                            web_entry["client_id"] = cid
                        else:
                            oauth_clients.append({"client_id": cid, "client_type": 3})
                        other_clients = (
                            client.setdefault("services", {})
                            .setdefault("appinvite_service", {})
                            .setdefault("other_platform_oauth_client", [])
                        )
                        other_web = next((c for c in other_clients if c.get("client_type") == 3), None)
                        if other_web is not None:
                            other_web["client_id"] = cid
                        else:
                            other_clients.append({"client_id": cid, "client_type": 3})
                    if _is_real_secret(dk):
                        api_keys = client.setdefault("api_key", [])
                        if api_keys:
                            api_keys[0]["current_key"] = dk
                        else:
                            api_keys.append({"current_key": dk})
                with open(gs_path, "w", encoding="utf-8") as f:
                    json.dump(data, f, indent=2)
                    f.write("\n")
            except Exception:
                pass
    else:
        safe_cid = cid if _is_real_secret(cid) else "000000000000-placeholder.apps.googleusercontent.com"
        safe_dk = dk if _is_real_secret(dk) else "placeholder"
        project_num = safe_cid.split("-")[0] if "-" in safe_cid and safe_cid.split("-")[0].isdigit() else "000000000000"
        gs_obj = {
            "project_info": {
                "project_number": project_num,
                "project_id": f"yvgpscam-{project_num}",
            },
            "client": [
                {
                    "client_info": {
                        "mobilesdk_app_id": f"1:{project_num}:android:0000000000000000",
                        "android_client_info": {"package_name": "in.yourview.yvgpscam"},
                    },
                    "oauth_client": [{"client_id": safe_cid, "client_type": 3}],
                    "api_key": [{"current_key": safe_dk}],
                    "services": {
                        "appinvite_service": {
                            "other_platform_oauth_client": [{"client_id": safe_cid, "client_type": 3}]
                        }
                    },
                }
            ],
            "configuration_version": "1",
        }
        with open(gs_path, "w", encoding="utf-8") as f:
            json.dump(gs_obj, f, indent=2)
            f.write("\n")

    # 2. Ensure default_web_client_id exists in strings.xml; only overwrite it if a real WEB_CLIENT_ID is provided
    strings_path = "app/src/main/res/values/strings.xml"
    os.makedirs(os.path.dirname(strings_path), exist_ok=True)
    if os.path.exists(strings_path):
        s_txt = open(strings_path, encoding="utf-8").read()
        if "default_web_client_id" in s_txt:
            if _is_real_secret(cid):
                s_txt = re.sub(
                    r'(<string\s+name="default_web_client_id"[^>]*>)[^<]*(</string>)',
                    rf"\g<1>{cid}\g<2>",
                    s_txt,
                )
                open(strings_path, "w", encoding="utf-8").write(s_txt)
        elif "</resources>" in s_txt:
            fallback_cid = cid if _is_real_secret(cid) else "000000000000-placeholder.apps.googleusercontent.com"
            s_txt = s_txt.replace(
                "</resources>",
                f'    <string name="default_web_client_id" translatable="false">{fallback_cid}</string>\n</resources>',
                1,
            )
            open(strings_path, "w", encoding="utf-8").write(s_txt)

    # 3. Inject real API keys ONLY into app/build.gradle(.kts)
    for gf in ["app/build.gradle.kts", "app/build.gradle"]:
        if os.path.exists(gf):
            g = open(gf, encoding="utf-8").read()
            if _is_real_secret(mk):
                g = g.replace("MY_MAPS_API_KEY", mk)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:MAPS_API_KEY|GOOGLE_MAPS_API_KEY)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{mk}\\""\g<2>',
                    g,
                )
                g = re.sub(
                    r'(manifestPlaceholders\["(?:MAPS_API_KEY|GOOGLE_MAPS_API_KEY)"\]\s*=\s*)[^\r\n]+',
                    rf'\g<1>"{mk}"',
                    g,
                )
            if _is_real_secret(dk):
                g = g.replace("MY_DRIVE_API_KEY", dk).replace("MY_GOOGLE_DRIVE_API_KEY", dk)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:GOOGLE_DRIVE_API_KEY|DRIVE_API_KEY)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{dk}\\""\g<2>',
                    g,
                )
            if _is_real_secret(gk):
                g = g.replace("MY_GEMINI_API_KEY", gk)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"GEMINI_API_KEY"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{gk}\\""\g<2>',
                    g,
                )
            if _is_real_secret(cid) and "googleusercontent.com" in cid:
                g = g.replace("MY_WEB_CLIENT_ID", cid)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:WEB_CLIENT_ID|GOOGLE_WEB_CLIENT_ID|SERVER_CLIENT_ID)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{cid}\\""\g<2>',
                    g,
                )
            open(gf, "w", encoding="utf-8").write(g)

    # 4. Inject Maps API key into AndroidManifest.xml only if a real key was supplied
    if _is_real_secret(mk):
        mp = "app/src/main/AndroidManifest.xml"
        if os.path.exists(mp):
            m = open(mp, encoding="utf-8").read().replace("MY_MAPS_API_KEY", mk).replace("${MAPS_API_KEY}", mk)
            if "com.google.android.geo.API_KEY" not in m and "</application>" in m:
                m = m.replace(
                    "</application>",
                    f'        <meta-data android:name="com.google.android.geo.API_KEY" android:value="{mk}" />\n    </application>',
                    1,
                )
            open(mp, "w", encoding="utf-8").write(m)


if __name__ == "__main__":
    fix_packages_and_imports()
    inject_keys_and_google_maps()
