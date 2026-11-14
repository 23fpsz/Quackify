package com.quackify.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

abstract class QuackifyWidgetReceiver(private val widget: GlanceAppWidget) : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget get() = widget

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetSync.start(context)
    }
}

class QuackifyWidget4x1Receiver : QuackifyWidgetReceiver(QuackifyWidget4x1())

class QuackifyWidget3x2Receiver : QuackifyWidgetReceiver(QuackifyWidget3x2())

class QuackifyWidget3x3Receiver : QuackifyWidgetReceiver(QuackifyWidget3x3())
