import os
import re
import glob
import json


def fix_packages_and_imports():
    NEW_PKG_RAW = "in.yourview.yvgpscam"
    NEW_PKG_KT = "`in`.yourview.yvgpscam"

    all_kt = glob.glob("app/src/**/*.kt", recursive=True)
    all_java = glob.glob("app/src/**/*.java", recursive=True)

    THIRD_PARTY_PREFIXES = (
        NEW_PKG_RAW, "android", "androidx", "com.google", "com.android", "com.squareup",
        "okhttp3", "okio", "retrofit2", "kotlin", "kotlinx", "java", "javax",
        "org", "io", "dagger", "hilt", "net", "coil", "bumptech", "glide"
    )

    old_pkgs = set()
    for path in all_kt + all_java:
        txt = open(path, encoding="utf-8", errors="ignore").read()
        m_pkg = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', txt, re.M)
        if m_pkg:
            ref = m_pkg.group(1).replace("`", "")
            if not ref.startswith(THIRD_PARTY_PREFIXES):
                old_pkgs.add(".".join(ref.split(".")[:3]))

    for old in old_pkgs:
        for root, _, files in os.walk("app"):
            for f in files:
                if f.endswith((".kt", ".java", ".xml", ".gradle", ".kts", ".pro")):
                    fp = os.path.join(root, f)
                    content = open(fp, encoding="utf-8", errors="ignore").read()
                    if old in content:
                        open(fp, "w", encoding="utf-8").write(content.replace(old, NEW_PKG_RAW))

    for path in all_kt:
        txt = open(path, encoding="utf-8", errors="ignore").read()
        fixed = re.sub(r'(^\s*(?:package|import)\s+)in\.yourview\.yvgpscam', r'\1`in`.yourview.yvgpscam', txt, flags=re.M)
        if fixed != txt:
            open(path, "w", encoding="utf-8").write(fixed)

    class_to_pkg = {}
    for path in all_kt:
        txt = open(path, encoding="utf-8", errors="ignore").read()
        m_pkg = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', txt, re.M)
        pkg = m_pkg.group(1) if m_pkg else NEW_PKG_KT
        for m_cls in re.finditer(r'\b(?:class|interface|object|enum\s+class|annotation\s+class|data\s+class|sealed\s+class)\s+([A-Za-z0-9_]+)', txt):
            class_to_pkg[m_cls.group(1)] = pkg

    for path in all_kt:
        txt = open(path, encoding="utf-8", errors="ignore").read()
        lines = txt.splitlines()
        changed = False
        m_pkg = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', txt, re.M)
        cur_pkg = m_pkg.group(1) if m_pkg else NEW_PKG_KT

        for i, line in enumerate(lines):
            m_imp = re.match(r'^(\s*import\s+)([`a-zA-Z0-9_.]+)\.([A-Z][A-Za-z0-9_]*)(.*)$', line)
            if m_imp:
                prefix, imp_pkg, cls_name, suffix = m_imp.groups()
                if imp_pkg.replace("`", "").startswith(NEW_PKG_RAW) and cls_name in class_to_pkg:
                    real_pkg = class_to_pkg[cls_name]
                    if real_pkg != imp_pkg:
                        lines[i] = f"{prefix}{real_pkg}.{cls_name}{suffix}"
                        changed = True

        if path.endswith("AppDatabase.kt"):
            for target in ["CapturedPhotoEntity", "OfflineRegionEntity", "CapturedPhotoDao", "OfflineRegionDao"]:
                if target in class_to_pkg:
                    real_pkg = class_to_pkg[target]
                    imp_stmt = f"import {real_pkg}.{target}"
                    if real_pkg != cur_pkg and imp_stmt not in "\n".join(lines):
                        for idx, l in enumerate(lines):
                            if l.startswith("package "):
                                lines.insert(idx + 1, imp_stmt)
                                changed = True
                                break

        if changed:
            open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def inject_keys_and_google_maps():
    maps_key = (os.environ.get("MAPS_API_KEY") or "").strip()
    drive_key = (os.environ.get("GOOGLE_DRIVE_API_KEY") or "").strip()
    gemini_key = (os.environ.get("GEMINI_API_KEY") or "").strip()
    client_id = (os.environ.get("WEB_CLIENT_ID") or "").strip()
    cert_sha1 = (os.environ.get("CERT_SHA1") or "").strip().lower()

    if maps_key and maps_key != "placeholder" and not maps_key.startswith("MY_"):
        print(f"MAPS_API_KEY loaded (length: {len(maps_key)}). Configuring Google Maps!")
    else:
        maps_key = ""
        print("INFO: MAPS_API_KEY secret not set; interactive in-app tile maps remain active.")

    valid_client_id = client_id.endswith(".apps.googleusercontent.com") and not client_id.startswith("://")
    if valid_client_id:
        print(f"WEB_CLIENT_ID loaded (length: {len(client_id)}).")
    else:
        print("INFO: WEB_CLIENT_ID secret not set; preserving checked-in app/google-services.json.")

    # Non-destructively update app/google-services.json when WEB_CLIENT_ID or CERT_SHA1 is provided
    gs_path = "app/google-services.json"
    if os.path.exists(gs_path) and (valid_client_id or cert_sha1 or (drive_key and drive_key != "placeholder")):
        try:
            gs = json.loads(open(gs_path, encoding="utf-8").read())
            for c in gs.get("client", []):
                c.setdefault("client_info", {}).setdefault("android_client_info", {})["package_name"] = "in.yourview.yvgpscam"
                oauth_list = c.setdefault("oauth_client", [])
                if valid_client_id:
                    updated_web = False
                    for o in oauth_list:
                        if o.get("client_type") == 3:
                            o["client_id"] = client_id
                            updated_web = True
                    if not updated_web:
                        oauth_list.append({"client_id": client_id, "client_type": 3})
                if cert_sha1:
                    for o in oauth_list:
                        if o.get("client_type") == 1:
                            o.setdefault("android_info", {})["package_name"] = "in.yourview.yvgpscam"
                            o["android_info"]["certificate_hash"] = cert_sha1
                if drive_key and drive_key != "placeholder":
                    c["api_key"] = [{"current_key": drive_key}]
            open(gs_path, "w", encoding="utf-8").write(json.dumps(gs, indent=2) + "\n")
            print("Updated app/google-services.json non-destructively (preserving client_type 1 & 3).")
        except Exception as exc:
            print(f"WARNING: Could not patch app/google-services.json cleanly: {exc}")

    for gradle_file in ["app/build.gradle.kts", "app/build.gradle"]:
        if os.path.exists(gradle_file):
            g_txt = open(gradle_file, encoding="utf-8").read()
            if maps_key:
                g_txt = g_txt.replace("MY_MAPS_API_KEY", maps_key)
                g_txt = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:MAPS_API_KEY|GOOGLE_MAPS_API_KEY)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{maps_key}\\""\g<2>',
                    g_txt
                )
                g_txt = re.sub(
                    r'(manifestPlaceholders\["(?:MAPS_API_KEY|GOOGLE_MAPS_API_KEY)"\]\s*=\s*)[^\r\n]+',
                    rf'\g<1>"{maps_key}"',
                    g_txt
                )
            if drive_key and drive_key != "placeholder":
                g_txt = g_txt.replace("MY_DRIVE_API_KEY", drive_key).replace("MY_GOOGLE_DRIVE_API_KEY", drive_key)
                g_txt = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:GOOGLE_DRIVE_API_KEY|DRIVE_API_KEY)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{drive_key}\\""\g<2>',
                    g_txt
                )
            if gemini_key and gemini_key != "placeholder":
                g_txt = g_txt.replace("MY_GEMINI_API_KEY", gemini_key)
                g_txt = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"GEMINI_API_KEY"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{gemini_key}\\""\g<2>',
                    g_txt
                )
            if valid_client_id:
                g_txt = g_txt.replace("MY_WEB_CLIENT_ID", client_id)
                g_txt = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:WEB_CLIENT_ID|GOOGLE_WEB_CLIENT_ID|SERVER_CLIENT_ID)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{client_id}\\""\g<2>',
                    g_txt
                )

            if "play-services-maps" not in g_txt and "dependencies {" in g_txt:
                if gradle_file.endswith(".kts"):
                    maps_deps = '\n    implementation("com.google.android.gms:play-services-maps:19.0.0")\n    implementation("com.google.maps.android:maps-compose:6.1.0")\n'
                else:
                    maps_deps = "\n    implementation 'com.google.android.gms:play-services-maps:19.0.0'\n    implementation 'com.google.maps.android:maps-compose:6.1.0'\n"
                g_txt = g_txt.replace("dependencies {", "dependencies {" + maps_deps, 1)

            open(gradle_file, "w", encoding="utf-8").write(g_txt)

    if maps_key:
        manifest_path = "app/src/main/AndroidManifest.xml"
        if os.path.exists(manifest_path):
            m_txt = open(manifest_path, encoding="utf-8").read()
            m_txt = m_txt.replace("MY_MAPS_API_KEY", maps_key).replace("${MAPS_API_KEY}", maps_key)
            if "com.google.android.geo.API_KEY" not in m_txt and "</application>" in m_txt:
                meta_tag = f'        <meta-data android:name="com.google.android.geo.API_KEY" android:value="{maps_key}" />\n    </application>'
                m_txt = m_txt.replace("</application>", meta_tag, 1)
            open(manifest_path, "w", encoding="utf-8").write(m_txt)

        for fp in glob.glob("app/src/main/**/*.kt", recursive=True):
            k_txt = open(fp, encoding="utf-8", errors="ignore").read()
            orig_k = k_txt
            k_txt = k_txt.replace("MY_MAPS_API_KEY", maps_key)
            k_txt = k_txt.replace("BuildConfig.MAPS_API_KEY", f'"{maps_key}"')
            if k_txt != orig_k:
                open(fp, "w", encoding="utf-8").write(k_txt)

    repo_path = "app/src/main/java/in/yourview/yvgpscam/map/OfflineMapRepository.kt"
    if os.path.exists(repo_path) and maps_key:
        r_txt = open(repo_path, encoding="utf-8").read()

        if "X-Android-Package" not in r_txt:
            req_pattern = re.compile(
                r'(\s*)val request = Request\.Builder\(\)\s*\.url\(url\)\s*\.header\("User-Agent",\s*"YVGPSCAM-Android/1\.0 \(Geotag Camera; Android\)"\)\s*\.build\(\)'
            )
            if req_pattern.search(r_txt):
                def _repl(m):
                    ind = m.group(1)
                    return (
                        f"{ind}val reqBuilder = Request.Builder()\n"
                        f"{ind}    .url(url)\n"
                        f"{ind}    .header(\"User-Agent\", \"YVGPSCAM-Android/1.0 (Geotag Camera; Android)\")\n"
                        f"{ind}if (url.contains(\"googleapis.com\")) {{\n"
                        f"{ind}    reqBuilder.header(\"X-Android-Package\", context.packageName)\n"
                        f"{ind}    if (androidCertSha1.isNotEmpty()) reqBuilder.header(\"X-Android-Cert\", androidCertSha1)\n"
                        f"{ind}}}\n"
                        f"{ind}val request = reqBuilder.build()"
                    )
                r_txt = req_pattern.sub(_repl, r_txt, count=1)
                print("Attached X-Android-Package & X-Android-Cert headers for Google Maps!")

        open(repo_path, "w", encoding="utf-8").write(r_txt)

    auth_helper_path = "app/src/main/java/in/yourview/yvgpscam/data/cloud/GoogleDriveAuthHelper.kt"
    if os.path.exists(auth_helper_path) and valid_client_id:
        a_txt = open(auth_helper_path, encoding="utf-8").read()
        a_txt = a_txt.replace(
            'runCatching { context.getString(R.string.default_web_client_id) }\n            .getOrNull()\n            ?.trim()\n            ?.takeIf { it.endsWith(".apps.googleusercontent.com") && !it.startsWith("://") }',
            f'runCatching {{ context.getString(R.string.default_web_client_id) }}.getOrNull()?.trim()?.takeIf {{ it.endsWith(".apps.googleusercontent.com") && !it.startsWith("://") }} ?: "{client_id}"'
        )
        open(auth_helper_path, "w", encoding="utf-8").write(a_txt)


def inject_auto_updater():
    repo = (os.environ.get("REPO") or "").strip()
    if not repo:
        return
    feed_url = f"https://github.com/{repo}/releases/latest/download/update-manifest.json"
    gp_path = "gradle.properties"
    if os.path.exists(gp_path):
        lines = [
            l for l in open(gp_path, encoding="utf-8").read().splitlines()
            if not l.startswith("UPDATE_MANIFEST_URL=")
        ]
        lines.append(f"UPDATE_MANIFEST_URL={feed_url}")
        open(gp_path, "w", encoding="utf-8").write("\n".join(lines) + "\n")
        print(f"Configured default in-app update manifest feed: {feed_url}")


if __name__ == "__main__":
    fix_packages_and_imports()
    inject_keys_and_google_maps()
    inject_auto_updater()
