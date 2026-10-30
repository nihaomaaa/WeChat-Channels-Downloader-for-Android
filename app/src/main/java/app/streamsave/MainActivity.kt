package app.streamsave

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import app.streamsave.ui.MainViewModel
import app.streamsave.ui.StreamSaveScreen

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.download() }
    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) model.loginReturned()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StreamSaveScreen(model, ::paste, ::download) {
            login.launch(Intent(this, app.streamsave.login.TencentLoginActivity::class.java))
        } }
        if (savedInstanceState == null) handle(intent)
    }
    override fun onStart() { super.onStart(); model.checkWebsite() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { model.receive(it) }
            Intent.ACTION_VIEW -> intent.dataString?.let { model.receive(it) }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && model.settings.value.clipboard) paste(true)
    }
    private fun paste(auto: Boolean = false) {
        // Android only permits clipboard reads while focused. Never poll from background.
        try {
            val clip = getSystemService(ClipboardManager::class.java).primaryClip
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
            if (text != null) model.receive(text, clipboard = auto)
            else if (!auto) android.widget.Toast.makeText(this, "剪贴板中没有文本链接", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            if (!auto) android.widget.Toast.makeText(this, "系统暂不允许读取剪贴板，请长按输入框粘贴", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    private fun download() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else model.download()
    }
}
