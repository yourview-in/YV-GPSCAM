package `in`.yourview.yvgpscam.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun applyAppImmersiveFullScreen(window: Window, immersive: Boolean = true) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val params = window.attributes
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = params
    }
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (immersive) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun SystemBarsImmersiveEffect(
    immersive: Boolean = true,
    restoreSystemBarsOnDispose: Boolean = false
) {
    val view = LocalView.current
    DisposableEffect(view, immersive, restoreSystemBarsOnDispose) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            applyAppImmersiveFullScreen(window, immersive)
        }
        onDispose {
            if (restoreSystemBarsOnDispose && immersive && window != null) {
                applyAppImmersiveFullScreen(window, immersive = false)
            }
        }
    }
}
