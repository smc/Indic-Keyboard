package org.smc.inputtest

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Host for the [PAGES]. Each page is a screenful of fields to exercise Indic Keyboard against;
 * [NavDrawer] switches between them.
 *
 * Automated tests should jump straight to a page instead of driving the drawer:
 *
 *     adb shell am start -n org.smc.inputtest/.MainActivity --es page text
 *     adb shell am start -n org.smc.inputtest/.MainActivity --es page rich
 *     adb shell am start -n org.smc.inputtest/.MainActivity --es page compose
 *
 * The activity is singleTop, so this switches the page in place rather than restarting.
 */
class MainActivity : ComponentActivity() {
    private lateinit var nav: NavDrawer
    private lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        nav = NavDrawer(this, PAGES) { showPage(it) }

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nav.topBar)
            addView(container)
        }
        setContentView(nav.wrap(shell))
        applyInsets(shell)
        showPage(pageFromIntent(intent) ?: PAGES.first())

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = nav.close()
        }.also { callback -> nav.onOpenStateChanged = { callback.isEnabled = it } })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pageFromIntent(intent)?.let { showPage(it) }
    }

    private fun pageFromIntent(intent: Intent?): Page? {
        val id = intent?.getStringExtra(EXTRA_PAGE) ?: return null
        return PAGES.firstOrNull { it.id == id }
            ?: null.also { Log.w(LOG_TAG, "unknown page \"$id\", keeping current") }
    }

    private fun showPage(page: Page) {
        container.removeAllViews()
        container.addView(page.createView(this),
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        nav.onPageShown(page)
        Log.i(LOG_TAG, "page=${page.id}")
    }

    private fun applyInsets(shell: View) {
        shell.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                if (insets.getInsets(WindowInsets.Type.ime()).bottom > 0) {
                    v.post { scrollFocusedAboveKeyboard() }
                }
            }
            insets
        }
    }

    private fun scrollFocusedAboveKeyboard() {
        val scroll = container.getChildAt(0) as? ScrollView ?: return
        val focused = currentFocus ?: return
        val scrollLoc = IntArray(2).also { scroll.getLocationInWindow(it) }
        val focusedLoc = IntArray(2).also { focused.getLocationInWindow(it) }
        val viewportBottom = scrollLoc[1] + scroll.height - scroll.paddingBottom
        val overlap = focusedLoc[1] + focused.height - viewportBottom + dp(16)
        if (overlap > 0) scroll.smoothScrollBy(0, overlap)
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}
