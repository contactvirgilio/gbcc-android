package com.philj56.gbcc

import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager

abstract class BaseActivity : AppCompatActivity() {

    val inNightMode get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private var themeResource: Int = 0
    private var oledNightMode: Boolean = false
    private lateinit var prefs: SharedPreferences

    /**
     * Activities that draw full screen and manage the system bars themselves
     * (e.g. the emulator screen) override this to skip the default inset handling.
     */
    protected open val handlesWindowInsets: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        themeResource = getThemeResource()
        oledNightMode = getOledNightMode()
        setTheme(themeResource)
        if (inNightMode && oledNightMode) {
            theme.applyStyle(R.style.OledDark, true)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!handlesWindowInsets) {
            applySystemBarInsets()
        }
    }

    /**
     * From Android 15 (targetSdk 35+) every activity is drawn edge-to-edge, behind the
     * status and navigation bars. Layouts whose root has fitsSystemWindows already handle
     * the top inset through their AppBarLayout, so only the bottom and side insets
     * (navigation bar, display cutout) are padded here. Other layouts are padded on all sides.
     * On older versions the window already excludes the system bars, so the insets are zero.
     */
    private fun applySystemBarInsets() {
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return
        val root: View = content.getChildAt(0) ?: return
        val handlesTop = root.fitsSystemWindows
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val top = if (handlesTop) 0 else bars.top
            view.setPadding(bars.left, top, bars.right, bars.bottom)
            WindowInsetsCompat.Builder(insets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(0, if (handlesTop) bars.top else 0, 0, 0)
                )
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(content)
    }

    /** Maps the stored "orientation" preference (see orientation_values_array) to a screen orientation. */
    protected fun orientationFromPreference(value: String?): Int {
        return when (value?.toIntOrNull()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun onStart() {
        super.onStart()
        // If the app theme has changed out from under us
        // (e.g. when coming back to the main screen after changing the theme in settings),
        // we need to recreate the activity in order to apply the change
        if (getThemeResource() != themeResource) {
            recreate()
        } else if (inNightMode && getOledNightMode() != oledNightMode) {
            recreate()
        }
    }

    private fun getThemeResource(): Int {
        return when (prefs.getString("theme", null)) {
            "Berry" -> R.style.Theme_GBCC_Berry
            "Dandelion" -> R.style.Theme_GBCC_Dandelion
            "Grape" -> R.style.Theme_GBCC_Grape
            "Kiwi" -> R.style.Theme_GBCC_Kiwi
            "Teal" -> R.style.Theme_GBCC_Teal
            else -> R.style.Theme_GBCC_Teal
        }
    }

    private fun getOledNightMode(): Boolean {
        return prefs.getBoolean("oled_night_mode", false)
    }
}