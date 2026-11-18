import os
import re
import glob


def fix_packages_and_imports():
    NEW_PKG_RAW = "in.yourview.yvgpscam"
    NEW_PKG_KT = "`in`.yourview.yvgpscam"

    all_kt = glob.glob("app/src/**/*.kt", recursive=True)
    all_java = glob.glob("app/src/**/*.java", recursive=True)

    PREFIXES = (
        NEW_PKG_RAW, "android", "androidx", "com.google", "com.android", "com.squareup",
        "okhttp3", "okio", "retrofit2", "kotlin", "kotlinx", "java", "javax",
        "org", "io", "dagger", "hilt", "net", "coil", "bumptech", "glide"
    )

    old_pkgs = set()
    for p in all_kt + all_java:
        txt = open(p, encoding="utf-8", errors="ignore").read()
        m = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', txt, re.M)
        if m and not m.group(1).replace("`", "").startswith(PREFIXES):
            old_pkgs.add(".".join(m.group(1).replace("`", "").split(".")[:3]))

    for old in old_pkgs:
        for root, _, files in os.walk("app"):
            for f in files:
                if f.endswith((".kt", ".java", ".xml", ".gradle", ".kts", ".pro")):
                    fp = os.path.join(root, f)
                    c = open(fp, encoding="utf-8", errors="ignore").read()
                    if old in c:
                        open(fp, "w", encoding="utf-8").write(c.replace(old, NEW_PKG_RAW))

    for p in all_kt:
        t = open(p, encoding="utf-8", errors="ignore").read()
        fx = re.sub(r'(^\s*(?:package|import)\s+)in\.yourview\.yvgpscam', r'\1`in`.yourview.yvgpscam', t, flags=re.M)
        if fx != t:
            open(p, "w", encoding="utf-8").write(fx)

    cls_map = {}
    for p in all_kt:
        t = open(p, encoding="utf-8", errors="ignore").read()
        mp = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', t, re.M)
        pkg = mp.group(1) if mp else NEW_PKG_KT
        for mc in re.finditer(r'\b(?:class|interface|object|enum\s+class|annotation\s+class|data\s+class|sealed\s+class)\s+([A-Za-z0-9_]+)', t):
            cls_map[mc.group(1)] = pkg

    for p in all_kt:
        t = open(p, encoding="utf-8", errors="ignore").read()
        lines, chg = t.splitlines(), False
        mp = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', t, re.M)
        cur = mp.group(1) if mp else NEW_PKG_KT
        for i, l in enumerate(lines):
            mi = re.match(r'^(\s*import\s+)([`a-zA-Z0-9_.]+)\.([A-Z][A-Za-z0-9_]*)(.*)$', l)
            if mi and mi.group(2).replace("`", "").startswith(NEW_PKG_RAW) and mi.group(3) in cls_map:
                if cls_map[mi.group(3)] != mi.group(2):
                    lines[i] = f"{mi.group(1)}{cls_map[mi.group(3)]}.{mi.group(3)}{mi.group(4)}"
                    chg = True
        if p.endswith("AppDatabase.kt"):
            for tgt in ["CapturedPhotoEntity", "OfflineRegionEntity", "CapturedPhotoDao", "OfflineRegionDao"]:
                if tgt in cls_map and cls_map[tgt] != cur and f"import {cls_map[tgt]}.{tgt}" not in "\n".join(lines):
                    for idx, l in enumerate(lines):
                        if l.startswith("package "):
                            lines.insert(idx + 1, f"import {cls_map[tgt]}.{tgt}")
                            chg = True
                            break
        if chg:
            open(p, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def inject_keys_and_google_maps():
    mk = (os.environ.get("MAPS_API_KEY") or "placeholder").strip()
    dk = (os.environ.get("GOOGLE_DRIVE_API_KEY") or "placeholder").strip()
    gk = (os.environ.get("GEMINI_API_KEY") or "placeholder").strip()
    cid = (os.environ.get("WEB_CLIENT_ID") or "000000000000-placeholder.apps.googleusercontent.com").strip()
    if cid.startswith("://") or not cid:
        cid = "000000000000-placeholder.apps.googleusercontent.com"

    project_num = cid.split("-")[0] if "-" in cid and cid.split("-")[0].isdigit() else "000000000000"

    # 1. Write valid google-services.json in app/ so Google Services plugin generates default_web_client_id
    gs_json = f"""{{
  "project_info": {{
    "project_number": "{project_num}",
    "project_id": "yvgpscam-{project_num}"
  }},
  "client": [
    {{
      "client_info": {{
        "mobilesdk_app_id": "1:{project_num}:android:0000000000000000",
        "android_client_info": {{
          "package_name": "in.yourview.yvgpscam"
        }}
      }},
      "oauth_client": [
        {{
          "client_id": "{cid}",
          "client_type": 3
        }}
      ],
      "api_key": [
        {{
          "current_key": "{dk}"
        }}
      ],
      "services": {{
        "appinvite_service": {{
          "other_platform_oauth_client": [
            {{
              "client_id": "{cid}",
              "client_type": 3
            }}
          ]
        }}
      }}
    }}
  ],
  "configuration_version": "1"
}}
"""
    open("app/google-services.json", "w", encoding="utf-8").write(gs_json)

    # 2. ALWAYS add default_web_client_id to strings.xml so R.string.default_web_client_id NEVER fails to resolve
    strings_path = "app/src/main/res/values/strings.xml"
    os.makedirs(os.path.dirname(strings_path), exist_ok=True)
    if os.path.exists(strings_path):
        s_txt = open(strings_path, encoding="utf-8").read()
        if "default_web_client_id" in s_txt:
            s_txt = re.sub(
                r'(<string\s+name="default_web_client_id"[^>]*>)[^<]*(</string>)',
                rf'\g<1>{cid}\g<2>',
                s_txt
            )
        elif "</resources>" in s_txt:
            s_txt = s_txt.replace(
                "</resources>",
                f'    <string name="default_web_client_id" translatable="false">{cid}</string>\n</resources>',
                1
            )
        open(strings_path, "w", encoding="utf-8").write(s_txt)
    else:
        open(strings_path, "w", encoding="utf-8").write(
            f'<resources>\n    <string name="default_web_client_id" translatable="false">{cid}</string>\n</resources>\n'
        )

    # 3. Inject keys ONLY into app/build.gradle(.kts) — NEVER replace BuildConfig.MAPS_API_KEY inside .kt files
    #    (Replacing BuildConfig.MAPS_API_KEY in SettingsScreen.kt broke Kotlin string interpolation: "$BuildConfig.MAPS_API_KEY")
    for gf in ["app/build.gradle.kts", "app/build.gradle"]:
        if os.path.exists(gf):
            g = open(gf, encoding="utf-8").read()
            if mk != "placeholder":
                g = g.replace("MY_MAPS_API_KEY", mk)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:MAPS_API_KEY|GOOGLE_MAPS_API_KEY)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{mk}\\""\g<2>',
                    g
                )
                g = re.sub(
                    r'(manifestPlaceholders\["(?:MAPS_API_KEY|GOOGLE_MAPS_API_KEY)"\]\s*=\s*)[^\r\n]+',
                    rf'\g<1>"{mk}"',
                    g
                )
            if dk != "placeholder":
                g = g.replace("MY_DRIVE_API_KEY", dk).replace("MY_GOOGLE_DRIVE_API_KEY", dk)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:GOOGLE_DRIVE_API_KEY|DRIVE_API_KEY)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{dk}\\""\g<2>',
                    g
                )
            if gk != "placeholder":
                g = g.replace("MY_GEMINI_API_KEY", gk)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"GEMINI_API_KEY"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{gk}\\""\g<2>',
                    g
                )
            if "googleusercontent.com" in cid:
                g = g.replace("MY_WEB_CLIENT_ID", cid)
                g = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:WEB_CLIENT_ID|GOOGLE_WEB_CLIENT_ID|SERVER_CLIENT_ID)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{cid}\\""\g<2>',
                    g
                )
            open(gf, "w", encoding="utf-8").write(g)

    # 4. Inject Maps API key into AndroidManifest.xml
    if mk != "placeholder":
        mp = "app/src/main/AndroidManifest.xml"
        if os.path.exists(mp):
            m = open(mp, encoding="utf-8").read().replace("MY_MAPS_API_KEY", mk).replace("${MAPS_API_KEY}", mk)
            if "com.google.android.geo.API_KEY" not in m and "</application>" in m:
                m = m.replace("</application>", f'        <meta-data android:name="com.google.android.geo.API_KEY" android:value="{mk}" />\n    </application>', 1)
            open(mp, "w", encoding="utf-8").write(m)

    # 5. Patch GoogleMapsComponents.kt cleanly
    gmc_path = "app/src/main/java/in/yourview/yvgpscam/map/GoogleMapsComponents.kt"
    if os.path.exists(gmc_path) and mk != "placeholder":
        gmc_txt = open(gmc_path, encoding="utf-8").read()
        gmc_txt = re.sub(
            r'fun\s+isApiKeyConfigured\s*\([^)]*\)\s*:\s*Boolean\s*\{',
            'fun isApiKeyConfigured(rawKey: String = "configured"): Boolean { return true;',
            gmc_txt
        )
        open(gmc_path, "w", encoding="utf-8").write(gmc_txt)

    # 6. Route OfflineMapRepository.kt to official Google Maps with SHA-1 headers & fresh cache
    rp = "app/src/main/java/in/yourview/yvgpscam/map/OfflineMapRepository.kt"
    if os.path.exists(rp) and mk != "placeholder":
        r = open(rp, encoding="utf-8").read()
        old_d = """private val tilesRootDir: File by lazy {
        File(context.filesDir, "offline_tiles").apply { mkdirs() }
    }"""
        new_d = """private val tilesRootDir: File by lazy {
        val dir = File(context.filesDir, "offline_tiles").apply { mkdirs() }
        val marker = File(dir, ".google_maps_migrated_v3")
        if (!marker.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dir.mkdirs()
            runCatching { marker.createNewFile() }
        }
        dir
    }"""
        r = r.replace(old_d, new_d, 1)
        r = r.replace("BuildConfig.MAPS_API_KEY.trim()", f'"{mk}"')

        fn_re = re.compile(
            r'fun\s+fetchTileFromNetworkAndCache\s*\([^)]*\)\s*:\s*ByteArray\?\s*\{.*?(?=\n\s*private\s+fun\s+executeTileRequest)',
            re.DOTALL
        )
        new_fn = f"""fun fetchTileFromNetworkAndCache(z: Int, x: Int, y: Int, mapStyle: MapStyle = MapStyle.STANDARD): ByteArray? {{
        val n = 2.0.pow(z.toDouble())
        val tileLon = ((x + 0.5) / n) * 360.0 - 180.0
        val tileLat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * (y + 0.5) / n))))
        val mt = when (mapStyle) {{
            MapStyle.STANDARD -> "roadmap"
            MapStyle.SATELLITE -> "satellite"
            MapStyle.TERRAIN -> "terrain"
            MapStyle.HYBRID -> "hybrid"
        }}
        val url = String.format(
            Locale.US,
            "https://maps.googleapis.com/maps/api/staticmap?center=%.6f,%.6f&zoom=%d&size=256x256&scale=2&maptype=%s&key=%s",
            tileLat,
            tileLon,
            z.coerceIn(1, 20),
            mt,
            "{mk}"
        )
        val bytes = executeTileRequest(url)
        if (bytes != null && isValidImageBytes(bytes)) {{
            saveTileBytes(z, x, y, bytes, mapStyle)
            return bytes
        }}
        return null
    }}

    """
        r = fn_re.sub(new_fn, r, count=1)

        old_req = """val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YVGPSCAM-Android/1.0 (Geotag Camera; Android)")
            .build()"""
        new_req = """val sha1 = runCatching {
            val sig = if (android.os.Build.VERSION.SDK_INT >= 28) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            }
            if (sig != null) {
                java.security.MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
            } else ""
        }.getOrDefault("")
        val rb = Request.Builder()
            .url(url)
            .header("User-Agent", "YVGPSCAM-Android/1.0 (Geotag Camera; Android)")
        if (url.contains("googleapis.com")) {
            rb.header("X-Android-Package", context.packageName)
            if (sha1.isNotEmpty()) rb.header("X-Android-Cert", sha1)
        }
        val request = rb.build()"""
        if "X-Android-Package" not in r:
            r = r.replace(old_req, new_req, 1)
        open(rp, "w", encoding="utf-8").write(r)

    # 7. Remove R.string.default_web_client_id lookup from GoogleDriveAuthHelper.kt so it never throws Unresolved reference
    ah = "app/src/main/java/in/yourview/yvgpscam/data/cloud/GoogleDriveAuthHelper.kt"
    if os.path.exists(ah):
        a = open(ah, encoding="utf-8").read()
        a = re.sub(
            r'runCatching\s*\{\s*(?:context|activity)\.getString\(R\.string\.default_web_client_id\)\s*\}\.getOrNull\(\)',
            f'"{cid}".takeIf {{ it.endsWith(".apps.googleusercontent.com") && !it.startsWith("000000000000-") }}',
            a
        )
        open(ah, "w", encoding="utf-8").write(a)


def inject_auto_updater():
    repo = os.environ.get("REPO", "")
    if not repo:
        return
    mp = "app/src/main/AndroidManifest.xml"
    if os.path.exists(mp):
        t = open(mp, encoding="utf-8").read()
        if "android.permission.INTERNET" not in t:
            open(mp, "w", encoding="utf-8").write(
                t.replace("<application", '<uses-permission android:name="android.permission.INTERNET" />\n    <application', 1)
            )
    fs = glob.glob("app/src/main/**/MainActivity.kt", recursive=True) + glob.glob("app/src/main/**/MainActivity.java", recursive=True)
    if not fs:
        return
    tf = fs[0]
    src = open(tf, encoding="utf-8").read()
    pm = re.search(r'^\s*(package\s+[^\r\n;]+)', src, re.M)
    pkg = pm.group(1).strip() if pm else "package `in`.yourview.yvgpscam"
    code = f"""{pkg}
import android.app.AlertDialog
import android.content.*
import android.net.Uri
import android.os.*
import org.json.JSONObject
import java.net.*
import kotlin.concurrent.thread

object AutoAppUpdater {{
    private const val URL_STR = "https://github.com/{repo}/releases/latest/download/update-manifest.json"
    private var done = false
    @JvmStatic fun checkForUpdate(ctx: Context) {{
        if (done) return; done = true
        thread {{
            try {{
                val c = (URL(URL_STR).openConnection() as HttpURLConnection).apply {{ connectTimeout = 5000; readTimeout = 5000 }}
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                val lc = j.getInt("versionCode")
                val ln = j.getString("versionName")
                val notes = j.optString("notes", "New update available.")
                val apk = j.getString("apkUrl")
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                val cc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
                if (lc > cc) Handler(Looper.getMainLooper()).post {{
                    runCatching {{
                        AlertDialog.Builder(ctx)
                            .setTitle("Update Available (v$ln)")
                            .setMessage("What's New:\\n$notes\\n\\nTap Update Now to install.")
                            .setPositiveButton("Update Now") {{ _, _ -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk))) }}
                            .setNegativeButton("Later", null)
                            .show()
                    }}
                }}
            }} catch (_: Exception) {{}}
        }}
    }}
}}
"""
    open(os.path.join(os.path.dirname(tf), "AutoAppUpdater.kt"), "w", encoding="utf-8").write(code)
    if "AutoAppUpdater.checkForUpdate" not in src:
        rep = r'\1\n        AutoAppUpdater.checkForUpdate(this)' if tf.endswith(".kt") else r'\1\n        AutoAppUpdater.checkForUpdate(this);'
        s2, n = re.subn(r'(super\.onCreate\([^\)]*\);?)', rep, src, count=1)
        if n > 0:
            open(tf, "w", encoding="utf-8").write(s2)


if __name__ == "__main__":
    fix_packages_and_imports()
    inject_keys_and_google_maps()
    inject_auto_updater()
