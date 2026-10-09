package dev.ikna

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.widget.Toast
import dev.ikna.ui.text.S

/** Foreground handoff in a separate process, without opening either profile. */
class ProfileRestartActivity : Activity() {
    private var started = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        val pid = intent.getIntExtra(PARENT_PID, -1)
        val manager = getSystemService(ActivityManager::class.java) ?: run {
            S.apply("system")
            Toast.makeText(this, S.t("dev.tools.009"), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val parent = manager.runningAppProcesses?.firstOrNull { it.pid == pid }
        if (pid <= 0 || pid == Process.myPid() ||
            (parent != null && (parent.uid != Process.myUid() || parent.processName != packageName))) {
            finish()
            return
        }
        Process.killProcess(pid)
        val deadline = SystemClock.elapsedRealtime() + 10_000
        fun launchWhenStopped() {
            if (manager.runningAppProcesses?.any { it.pid == pid } == true) {
                if (SystemClock.elapsedRealtime() < deadline) {
                    handler.postDelayed({ launchWhenStopped() }, 50)
                } else {
                    S.apply("system")
                    Toast.makeText(this, S.t("dev.tools.009"), Toast.LENGTH_LONG).show()
                    finish()
                }
                return
            }
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            Process.killProcess(Process.myPid())
        }
        launchWhenStopped()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val PARENT_PID = "dev.ikna.RESTART_PARENT_PID"
        fun restart(context: Context) {
            context.startActivity(Intent(context, ProfileRestartActivity::class.java)
                .putExtra(PARENT_PID, Process.myPid()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
