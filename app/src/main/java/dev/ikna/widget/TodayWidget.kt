package dev.ikna.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.ikna.MainActivity
import dev.ikna.R

/**
 * One number on the home screen, and one tap into the session behind it.
 *
 * The reason this exists is the distance between deciding to study and being
 * able to: find the icon, wait for the app, look at the deck list, choose. Every
 * one of those steps is a place to be interrupted, and an interrupted intention
 * does not usually come back. The widget removes all of them - the number is
 * already visible without opening anything, and touching it lands in the cards.
 *
 * It renders text the app handed it and nothing else. No database, no scheduler,
 * no localisation: a widget is drawn inside the launcher's process, where none of
 * that is available, and a query there would be a disk read on someone else's
 * main thread. [publish] is called from the app, which already knows the number
 * and already knows the language.
 */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val views = buildViews(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }

    companion object {
        private const val PREFS = "ikna-widget"
        private const val KEY_COUNT = "count"
        private const val KEY_TITLE = "title"
        private const val KEY_LABEL = "label"

        /** Distinct from the reminder's request code, or the two would share one intent. */
        private const val REQUEST_CODE = 2001

        /**
         * Stores what the widget should say and redraws every placed copy.
         *
         * Safe to call when no widget exists - that is the common case - and
         * safe to call often: two writes of the same three values cost less than
         * deciding whether they changed.
         */
        fun publish(context: Context, count: Int, title: String, label: String) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_COUNT, count)
                .putString(KEY_TITLE, title)
                .putString(KEY_LABEL, label)
                .apply()
            refresh(app)
        }

        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(app)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_COUNT, -1)
            val title = prefs.getString(KEY_TITLE, null)
            val label = prefs.getString(KEY_LABEL, null)

            val views = RemoteViews(context.packageName, R.layout.widget_today)
            views.setTextViewText(
                R.id.widget_title,
                if (title.isNullOrBlank()) context.getString(R.string.app_name) else title
            )
            // A dash until the app has run once. Zero would be a claim about
            // today, and the widget has no way to know that yet.
            views.setTextViewText(
                R.id.widget_count,
                if (count >= 0) count.toString() else context.getString(R.string.widget_placeholder)
            )
            views.setTextViewText(R.id.widget_label, label ?: "")
            views.setOnClickPendingIntent(R.id.widget_root, openSession(context))
            return views
        }

        private fun openSession(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_START_SESSION, true)
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
