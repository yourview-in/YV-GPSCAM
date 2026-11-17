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
    maps_key = (os.environ.get("MAPS_API_KEY") or "placeholder").strip()
    drive_key = (os.environ.get("GOOGLE_DRIVE_API_KEY") or "placeholder").strip()
    gemini_key = (os.environ.get("GEMINI_API_KEY") or "placeholder").strip()
    client_id = (os.environ.get("WEB_CLIENT_ID") or "://googleusercontent.com").strip()

    if maps_key and maps_key != "placeholder":
        print(f"MAPS_API_KEY loaded (length: {len(maps_key)}). Configuring official Google Maps!")
    else:
        print("WARNING: MAPS_API_KEY is empty!")

    if client_id == "://googleusercontent.com" or not client_id:
        print("WARNING: WEB_CLIENT_ID secret is empty!")
    else:
        print(f"WEB_CLIENT_ID loaded (length: {len(client_id)}).")

    # 1. Inject real API keys and WEB_CLIENT_ID into build.gradle.kts / build.gradle
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
            if "googleusercontent.com" in client_id and not client_id.startswith("://"):
                g_txt = g_txt.replace("MY_WEB_CLIENT_ID", client_id)
                g_txt = re.sub(
                    r'(buildConfigField\s*\(\s*"String"\s*,\s*"(?:WEB_CLIENT_ID|GOOGLE_WEB_CLIENT_ID|SERVER_CLIENT_ID)"\s*,\s*)[^)]+(\))',
                    rf'\g<1>"\\"{client_id}\\""\g<2>',
                    g_txt
                )
            open(gradle_file, "w", encoding="utf-8").write(g_txt)

    # 2. Inject MAPS_API_KEY directly into GoogleMapsComponents.kt & OfflineMapRepository.kt
    if maps_key and maps_key != "placeholder":
        for fp in glob.glob("app/src/main/**/*.kt", recursive=True):
            k_txt = open(fp, encoding="utf-8", errors="ignore").read()
            orig_k = k_txt
            k_txt = k_txt.replace("MY_MAPS_API_KEY", maps_key)
            k_txt = k_txt.replace("BuildConfig.MAPS_API_KEY", f'"{maps_key}"')
            if k_txt != orig_k:
                print(f"Injected Google Maps API key into {fp}")
                open(fp, "w", encoding="utf-8").write(k_txt)

        manifest_path = "app/src/main/AndroidManifest.xml"
        if os.path.exists(manifest_path):
            m_txt = open(manifest_path, encoding="utf-8").read()
            m_txt = m_txt.replace("MY_MAPS_API_KEY", maps_key)
            m_txt = m_txt.replace("${MAPS_API_KEY}", maps_key)
            open(manifest_path, "w", encoding="utf-8").write(m_txt)

    # 3. Attach Android Package + SHA-1 headers to Google Static Maps HTTP requests
    repo_path = "app/src/main/java/in/yourview/yvgpscam/map/OfflineMapRepository.kt"
    if os.path.exists(repo_path):
        r_txt = open(repo_path, encoding="utf-8").read()
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
                print("Added X-Android-Package and X-Android-Cert headers for Google Static Maps API!")

    # 4. Patch GoogleDriveAuthHelper.kt with direct WEB_CLIENT_ID fallback
    auth_helper_path = "app/src/main/java/in/yourview/yvgpscam/data/cloud/GoogleDriveAuthHelper.kt"
    if os.path.exists(auth_helper_path) and "googleusercontent.com" in client_id and not client_id.startswith("://"):
        a_txt = open(auth_helper_path, encoding="utf-8").read()
        a_txt = a_txt.replace(
            'val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()\n            ?: return null',
            f'val clientId = runCatching {{ context.getString(R.string.default_web_client_id) }}.getOrNull()?.takeIf {{ it.endsWith(".apps.googleusercontent.com") }} ?: "{client_id}"'
        )
        a_txt = a_txt.replace(
            'val clientId = runCatching { activity.getString(R.string.default_web_client_id) }.getOrNull()\n            ?: return Result.failure(IllegalStateException("Google Sign-In configuration missing (default_web_client_id)."))',
            f'val clientId = runCatching {{ activity.getString(R.string.default_web_client_id) }}.getOrNull()?.takeIf {{ it.endsWith(".apps.googleusercontent.com") }} ?: "{client_id}"'
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
    tf = files.pop(0)
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
    inject_keys_and_google_maps()
    inject_auto_updater()
