package vn.vigo.nepnha

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.vigo.nepnha.data.repo.AppSettings
import vn.vigo.nepnha.ui.nav.NepNhaRoot
import vn.vigo.nepnha.ui.theme.NepNhaTheme
import vn.vigo.nepnha.ui.theme.PerformanceConfig

class MainActivity : ComponentActivity() {

    private var openTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_NepNha)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTab = intent?.getStringExtra(EXTRA_OPEN_TAB)

        val container = (application as NepNhaApp).container
        val am = getSystemService(ActivityManager::class.java)
        // Máy RAM thấp hoặc ≤ 3 GB: tự giảm hiệu ứng để giữ khung hình mượt.
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val lowEnd = am?.isLowRamDevice == true || memInfo.totalMem in 1..(3L * 1024 * 1024 * 1024)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            val perf = remember(settings.reduceMotion, lowEnd) {
                PerformanceConfig(reduceMotion = settings.reduceMotion || lowEnd, lightDecor = lowEnd)
            }
            NepNhaTheme(userFontScale = settings.fontScale, performance = perf) {
                NepNhaRoot(
                    container = container,
                    openTab = openTab,
                    onTabOpened = { openTab = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTab = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "open_tab"
    }
}
