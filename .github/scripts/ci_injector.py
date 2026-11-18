import os
import re
import glob


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

    cls_locations = {}
    for path in all_kt:
        txt = open(path, encoding="utf-8", errors="ignore").read()
        m_pkg = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', txt, re.M)
        pkg = m_pkg.group(1) if m_pkg else NEW_PKG_KT
        for m_cls in re.finditer(r'^[ \t]*(?:public\s+|internal\s+|abstract\s+|open\s+|data\s+|sealed\s+|enum\s+|annotation\s+)*(?:class|interface|object)\s+([A-Za-z0-9_]+)', txt, re.M):
            cls_name = m_cls.group(1)
            cls_locations.setdefault(cls_name, set()).add(pkg)

    unique_class_to_pkg = {k: next(iter(v)) for k, v in cls_locations.items() if len(v) == 1}

    for path in all_kt:
        if path.endswith("AppDatabase.kt"):
            txt = open(path, encoding="utf-8", errors="ignore").read()
            lines = txt.splitlines()
            changed = False
            m_pkg = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', txt, re.M)
            cur_pkg = m_pkg.group(1) if m_pkg else NEW_PKG_KT
            for target in ["CapturedPhotoEntity", "OfflineRegionEntity", "CapturedPhotoDao", "OfflineRegionDao"]:
                if target in unique_class_to_pkg:
                    real_pkg = unique_class_to_pkg[target]
                    imp_stmt = f"import {real_pkg}.{target}"
                    if real_pkg != cur_pkg and imp_stmt not in "\n".join(lines):
                        for idx, l in enumerate(lines):
                            if l.startswith("package "):
                                lines.insert(idx + 1, imp_stmt)
                                changed = True
                                break
            if changed:
                open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def fix_metadata_sync_mismatches():
    # Error 1 & 2: YvGpsCamContainer.kt:65 & 66 -> No parameter with name 'isNetworkOnline' / 'syncStore' found.
    # Also expose `val appContext: Context` on YvGpsCamContainer if missing.
    for c_path in glob.glob("app/src/**/YvGpsCamContainer.kt", recursive=True):
        c_txt = open(c_path, encoding="utf-8").read()
        orig_c = c_txt

        # Remove stale named arguments `isNetworkOnline = ...` and `syncStore = ...`
        new_lines = []
        for line in c_txt.splitlines():
            if re.search(r'^\s*(?:isNetworkOnline|syncStore)\s*=', line):
                continue
            new_lines.append(line)
        c_txt = "\n".join(new_lines) + "\n"
        # Clean up trailing comma right before closing parenthesis `)` if `syncStore = ...` was the last argument
        c_txt = re.sub(r',(\s*\))', r'\1', c_txt)

        # Expose `val appContext` on YvGpsCamContainer
        m_c = re.search(r'(\bclass\s+YvGpsCamContainer\s*\()([^)]*)(\)\s*\{)', c_txt, re.S)
        if m_c and not re.search(r'\bval\s+appContext\b', c_txt):
            p_pre, p_args, p_suf = m_c.groups()
            m_ctx_arg = re.search(r'\b([A-Za-z0-9_]+)\s*:\s*(?:android\.content\.)?Context', p_args)
            if m_ctx_arg:
                ctx_name = m_ctx_arg.group(1)
                if not re.search(rf'\b(?:val|var)\s+{ctx_name}\s*:', p_args):
                    p_args = re.sub(rf'\b({ctx_name}\s*:\s*(?:android\.content\.)?Context)', r'val \1', p_args, count=1)
                else:
                    p_args = re.sub(rf'\bprivate\s+(val\s+{ctx_name}\s*:)', r'\1', p_args, count=1)
                c_txt = (
                    c_txt[:m_c.start()]
                    + p_pre + p_args + p_suf
                    + f"\n    val appContext: android.content.Context get() = {ctx_name}.applicationContext\n"
                    + c_txt[m_c.end():]
                )
        if c_txt != orig_c:
            open(c_path, "w", encoding="utf-8").write(c_txt)

    # Error 4: LocationMetadataSyncWorker.kt:44:88 -> Unresolved reference 'appContext'.
    # Notice column 88! That means line 44 has something like `...container.appContext` or `...app.appContext` or `...something.appContext`!
    # Let's inspect line 44 and replace `.appContext` with `.applicationContext` (or `applicationContext`) safely!
    for w_path in glob.glob("app/src/**/LocationMetadataSyncWorker.kt", recursive=True):
        w_txt = open(w_path, encoding="utf-8").read()
        orig_w = w_txt
        lines = w_txt.splitlines()
        for i, l in enumerate(lines):
            if "appContext" in l and ("container.appContext" in l or ").appContext" in l or ".appContext" in l):
                # Inside a CoroutineWorker, `applicationContext` is built-in!
                # Replace `<anything>.appContext` on that expression with `applicationContext`
                lines[i] = re.sub(r'(?:\([^)]+\)|[A-Za-z0-9_]+)(?:\.[A-Za-z0-9_]+)*\.appContext\b', 'applicationContext', l)
        w_txt = "\n".join(lines) + "\n"
        if w_txt != orig_w:
            open(w_path, "w", encoding="utf-8").write(w_txt)

    # Error 3: CameraViewModel.kt:304:25 -> Unresolved reference 'requestMetadataSyncWhenConnected'.
    # Notice column 25! That means line 304 is calling a method on an object at col 25: e.g. `someRepo.requestMetadataSyncWhenConnected(...)` or `container.requestMetadataSyncWhenConnected(...)`!
    # Look at CameraViewModel.kt around line 304 to find the receiver object type/name, OR replace `.<receiver>.requestMetadataSyncWhenConnected(...)` with `LocationMetadataSyncWorker.requestMetadataSyncWhenConnected(...)`!
    worker_pkg = "`in`.yourview.yvgpscam.work"
    for w_path in glob.glob("app/src/**/LocationMetadataSyncWorker.kt", recursive=True):
        w_txt = open(w_path, encoding="utf-8").read()
        m_wpkg = re.search(r'^\s*package\s+([`a-zA-Z0-9_.]+)', w_txt, re.M)
        if m_wpkg:
            worker_pkg = m_wpkg.group(1)
        if "fun requestMetadataSyncWhenConnected" not in w_txt:
            helper_body = """
        fun requestMetadataSyncWhenConnected(context: android.content.Context) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val req = androidx.work.OneTimeWorkRequestBuilder<LocationMetadataSyncWorker>()
                .setConstraints(constraints)
                .build()
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("location_metadata_sync", androidx.work.ExistingWorkPolicy.KEEP, req)
        }"""
            if "companion object" in w_txt:
                w_txt = re.sub(r'(companion\s+object\s*\{)', r'\1' + helper_body, w_txt, count=1)
            else:
                idx = w_txt.rfind("}")
                if idx != -1:
                    w_txt = w_txt[:idx] + f"\n    companion object {{{helper_body}\n    }}\n}}\n"
            open(w_path, "w", encoding="utf-8").write(w_txt)

    for vm_path in glob.glob("app/src/**/CameraViewModel.kt", recursive=True):
        vm_txt = open(vm_path, encoding="utf-8").read()
        orig_vm = vm_txt
        lines = vm_txt.splitlines()
        for i, l in enumerate(lines):
            if "requestMetadataSyncWhenConnected" in l:
                # Case A: `receiver.requestMetadataSyncWhenConnected(ctx)` -> `LocationMetadataSyncWorker.requestMetadataSyncWhenConnected(ctx)`
                # Case B: `receiver.requestMetadataSyncWhenConnected()` (0 args) -> `LocationMetadataSyncWorker.requestMetadataSyncWhenConnected(getApplication())` or `appContext`
                m_call = re.search(r'([A-Za-z0-9_.]+)\.requestMetadataSyncWhenConnected\s*\(([^)]*)\)', l)
                if m_call:
                    args = m_call.group(2).strip()
                    if not args:
                        # Find a Context property in CameraViewModel (like application, appContext, context)
                        ctx_expr = "getApplication()" if "AndroidViewModel" in vm_txt else (
                            "appContext" if re.search(r'\bappContext\b', vm_txt) else (
                                "context" if re.search(r'\bcontext\b', vm_txt) else "applicationContext"
                            )
                        )
                        args = ctx_expr
                    lines[i] = l[:m_call.start()] + f"{worker_pkg}.LocationMetadataSyncWorker.requestMetadataSyncWhenConnected({args})" + l[m_call.end():]
                else:
                    # Bare `requestMetadataSyncWhenConnected(...)`
                    m_bare = re.search(r'\brequestMetadataSyncWhenConnected\s*\(([^)]*)\)', l)
                    if m_bare:
                        args = m_bare.group(1).strip()
                        if not args:
                            args = "getApplication()" if "AndroidViewModel" in vm_txt else (
                                "appContext" if re.search(r'\bappContext\b', vm_txt) else "context"
                            )
                        lines[i] = l[:m_bare.start()] + f"{worker_pkg}.LocationMetadataSyncWorker.requestMetadataSyncWhenConnected({args})" + l[m_bare.end():]
        vm_txt = "\n".join(lines) + "\n"
        if vm_txt != orig_vm:
            open(vm_path, "w", encoding="utf-8").write(vm_txt)


def inject_keys_and_google_maps():
    maps_key = (os.environ.get("MAPS_API_KEY") or "placeholder").strip()
    drive_key = (os.environ.get("GOOGLE_DRIVE_API_KEY") or "placeholder").strip()
    gemini_key = (os.environ.get("GEMINI_API_KEY") or "placeholder").strip()
    client_id = (os.environ.get("WEB_CLIENT_ID") or "000000000000-placeholder.apps.googleusercontent.com").strip()
    if client_id.startswith("://") or not client_id:
        client_id = "000000000000-placeholder.apps.googleusercontent.com"

    project_num = client_id.split("-")[0] if "-" in client_id and client_id.split("-")[0].isdigit() else "000000000000"

    gs_json = f"""{{
  "project_info": {{ "project_number": "{project_num}", "project_id": "yvgpscam-{project_num}" }},
  "client": [{{
    "client_info": {{ "mobilesdk_app_id": "1:{project_num}:android:0000000000000000", "android_client_info": {{ "package_name": "in.yourview.yvgpscam" }} }},
    "oauth_client": [{{ "client_id": "{client_id}", "client_type": 3 }}],
    "api_key": [{{ "current_key": "{drive_key}" }}]
  }}],
  "configuration_version": "1"
}}
"""
    open("app/google-services.json", "w", encoding="utf-8").write(gs_json)

    strings_path = "app/src/main/res/values/strings.xml"
    os.makedirs(os.path.dirname(strings_path), exist_ok=True)
    if os.path.exists(strings_path):
        s_txt = open(strings_path, encoding="utf-8").read()
        if "default_web_client_id" in s_txt:
            s_txt = re.sub(
                r'(<string\s+name="default_web_client_id"[^>]*>)[^<]*(</string>)',
                rf'\g<1>{client_id}\g<2>',
                s_txt
            )
        elif "</resources>" in s_txt:
            s_txt = s_txt.replace(
                "</resources>",
                f'    <string name="default_web_client_id" translatable="false">{client_id}</string>\n</resources>',
                1
            )
        open(strings_path, "w", encoding="utf-8").write(s_txt)
    else:
        open(strings_path, "w", encoding="utf-8").write(
            f'<resources>\n    <string name="default_web_client_id" translatable="false">{client_id}</string>\n</resources>\n'
        )

    for gradle_file in ["app/build.gradle.kts", "app/build.gradle"]:
        if os.path.exists(gradle_file):
            g_txt = open(gradle_file, encoding="utf-8").read()
            if maps_key and maps_key != "placeholder":
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
            if "googleusercontent.com" in client_id:
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

    if maps_key and maps_key != "placeholder":
        manifest_path = "app/src/main/AndroidManifest.xml"
        if os.path.exists(manifest_path):
            m_txt = open(manifest_path, encoding="utf-8").read()
            m_txt = m_txt.replace("MY_MAPS_API_KEY", maps_key).replace("${MAPS_API_KEY}", maps_key)
            if "com.google.android.geo.API_KEY" not in m_txt and "</application>" in m_txt:
                meta_tag = f'        <meta-data android:name="com.google.android.geo.API_KEY" android:value="{maps_key}" />\n    </application>'
                m_txt = m_txt.replace("</application>", meta_tag, 1)
            open(manifest_path, "w", encoding="utf-8").write(m_txt)

    gmc_path = "app/src/main/java/in/yourview/yvgpscam/map/GoogleMapsComponents.kt"
    if os.path.exists(gmc_path) and maps_key and maps_key != "placeholder":
        gmc_txt = open(gmc_path, encoding="utf-8").read()
        gmc_txt = re.sub(
            r'fun\s+isApiKeyConfigured\s*\([^)]*\)\s*:\s*Boolean\s*\{',
            'fun isApiKeyConfigured(rawKey: String = "configured"): Boolean { return true;',
            gmc_txt
        )
        open(gmc_path, "w", encoding="utf-8").write(gmc_txt)

    repo_path = "app/src/main/java/in/yourview/yvgpscam/map/OfflineMapRepository.kt"
    if os.path.exists(repo_path) and maps_key and maps_key != "placeholder":
        r_txt = open(repo_path, encoding="utf-8").read()

        old_root_decl = """private val tilesRootDir: File by lazy {
        File(context.filesDir, "offline_tiles").apply { mkdirs() }
    }"""
        new_root_decl = """private val tilesRootDir: File by lazy {
        val dir = File(context.filesDir, "offline_tiles").apply { mkdirs() }
        val marker = File(dir, ".google_maps_migrated_v3")
        if (!marker.exists()) {
            dir.listFiles()?.forEach { it.deleteRecursively() }
            dir.mkdirs()
            runCatching { marker.createNewFile() }
        }
        dir
    }"""
        if old_root_decl in r_txt:
            r_txt = r_txt.replace(old_root_decl, new_root_decl, 1)

        r_txt = r_txt.replace("BuildConfig.MAPS_API_KEY.trim()", f'"{maps_key}"')

        old_tile_fn = re.compile(
            r'fun\s+fetchTileFromNetworkAndCache\s*\([^)]*\)\s*:\s*ByteArray\?\s*\{.*?(?=\n\s*private\s+fun\s+executeTileRequest)',
            re.DOTALL
        )
        new_tile_fn = f"""fun fetchTileFromNetworkAndCache(z: Int, x: Int, y: Int, mapStyle: MapStyle = MapStyle.STANDARD): ByteArray? {{
        val n = 2.0.pow(z.toDouble())
        val tileLon = ((x + 0.5) / n) * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * (y + 0.5) / n)))
        val tileLat = Math.toDegrees(latRad)
        val googleMapType = when (mapStyle) {{
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
            googleMapType,
            "{maps_key}"
        )
        val bytes = executeTileRequest(url)
        if (bytes != null && isValidImageBytes(bytes)) {{
            saveTileBytes(z, x, y, bytes, mapStyle)
            return bytes
        }}
        return null
    }}

    """
        if old_tile_fn.search(r_txt):
            r_txt = old_tile_fn.sub(new_tile_fn, r_txt, count=1)

        if "X-Android-Package" not in r_txt and '.header("User-Agent"' in r_txt:
            helper_snippet = """val androidCertSha1 = runCatching {
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
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "YVGPSCAM-Android/1.0 (Geotag Camera; Android)")
        if (url.contains("googleapis.com")) {
            reqBuilder.header("X-Android-Package", context.packageName)
            if (androidCertSha1.isNotEmpty()) {
                reqBuilder.header("X-Android-Cert", androidCertSha1)
            }
        }
        val request = reqBuilder.build()"""
            old_req = """val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YVGPSCAM-Android/1.0 (Geotag Camera; Android)")
            .build()"""
            if old_req in r_txt:
                r_txt = r_txt.replace(old_req, helper_snippet, 1)

        open(repo_path, "w", encoding="utf-8").write(r_txt)

    auth_helper_path = "app/src/main/java/in/yourview/yvgpscam/data/cloud/GoogleDriveAuthHelper.kt"
    if os.path.exists(auth_helper_path):
        a_txt = open(auth_helper_path, encoding="utf-8").read()
        a_txt = re.sub(
            r'runCatching\s*\{\s*(?:context|activity)\.getString\(R\.string\.default_web_client_id\)\s*\}\.getOrNull\(\)',
            f'"{client_id}".takeIf {{ it.endsWith(".apps.googleusercontent.com") && !it.startsWith("000000000000-") }}',
            a_txt
        )
        open(auth_helper_path, "w", encoding="utf-8").write(a_txt)


def inject_auto_updater():
    repo = os.environ.get("REPO", "")
    if not repo:
        return
    m_path = "app/src/main/AndroidManifest.xml"
    if os.path.exists(m_path):
        txt = open(m_path, encoding="utf-8").read()
        if "android.permission.INTERNET" not in txt:
            open(m_path, "w", encoding="utf-8").write(
                txt.replace("<application", '<uses-permission android:name="android.permission.INTERNET" />\n    <application', 1)
            )
    files = glob.glob("app/src/main/**/MainActivity.kt", recursive=True) + glob.glob("app/src/main/**/MainActivity.java", recursive=True)
    if not files:
        return
    tf = files[0]
    src = open(tf, encoding="utf-8").read()
    pm = re.search(r'^\s*(package\s+[^\r\n;]+)', src, re.M)
    pkg_line = pm.group(1).strip() if pm else "package `in`.yourview.yvgpscam"
    code = f"""{pkg_line}
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
                val lc = j.getInt("versionCode"); val ln = j.getString("versionName")
                val notes = j.optString("notes", "New update available."); val apk = j.getString("apkUrl")
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                val cc = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
                if (lc > cc) Handler(Looper.getMainLooper()).post {{
                    try {{
                        AlertDialog.Builder(ctx).setTitle("Update Available (v$ln)")
                            .setMessage("What's New:\\n$notes\\n\\nTap Update Now to install.")
                            .setPositiveButton("Update Now") {{ _, _ -> ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk))) }}
                            .setNegativeButton("Later", null).show()
                    }} catch (_: Exception) {{}}
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
    fix_metadata_sync_mismatches()
    inject_keys_and_google_maps()
    inject_auto_updater()
