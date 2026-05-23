package com.example.adobongkangkong.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.adobongkangkong.R
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.settings.UserPreferencesRepository
import com.example.adobongkangkong.domain.usecase.LogFoodUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayCaffeineMgUseCase
import com.example.adobongkangkong.domain.usecase.ObserveTodayMacrosUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen caffeine widget provider.
 *
 * MVP behavior:
 * - Shows today's caffeine total from normal log snapshot nutrients.
 * - Shows today's macro totals from normal log snapshot nutrients.
 * - Shows exactly 3 configurable quick-log slots.
 * - Tapping a configured slot logs 1 serving of that food through the normal AK logging use case.
 * - No separate caffeine-only records are created.
 *
 * Refresh behavior:
 * - Refreshes after normal widget update.
 * - Refreshes after widget quick-log taps.
 * - Refreshes when app-side logging asks the widget to refresh via [requestRefresh].
 * - Schedules a one-shot alarm for the next local midnight so the displayed total rolls over
 *   to the new day even if the app is closed.
 */
@AndroidEntryPoint
class CaffeineWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var observeTodayCaffeineMgUseCase: ObserveTodayCaffeineMgUseCase

    @Inject
    lateinit var observeTodayMacrosUseCase: ObserveTodayMacrosUseCase

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var foodRepository: FoodRepository

    @Inject
    lateinit var logFoodUseCase: LogFoodUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextMidnightRefresh(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelNextMidnightRefresh(context.applicationContext)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        scope.launch {
            updateAllWidgets(
                context = context.applicationContext,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds
            )
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_LOG_SLOT -> {
                val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
                if (slotIndex !in 1..3) return

                scope.launch {
                    val appContext = context.applicationContext
                    val foodId = foodIdForSlot(slotIndex) ?: return@launch

                    logFoodUseCase.logFoodByServings(
                        foodId = foodId,
                        servings = 1.0,
                        logDateIso = LocalDate.now(ZoneId.systemDefault()).toString()
                    )

                    updateAllWidgets(appContext)
                }
            }

            ACTION_REFRESH_WIDGET,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                scope.launch {
                    updateAllWidgets(context.applicationContext)
                }
            }
        }
    }

    private suspend fun updateAllWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
        appWidgetIds: IntArray = appWidgetManager.getAppWidgetIds(
            ComponentName(context, CaffeineWidgetProvider::class.java)
        )
    ) {
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)

        val caffeineMg =
            observeTodayCaffeineMgUseCase(
                date = today,
                zoneId = zoneId
            ).first()

        val macros =
            observeTodayMacrosUseCase(
                date = today,
                zoneId = zoneId
            ).first()

        val slots = listOf(
            CaffeineWidgetSlot(
                slotIndex = 1,
                fallbackLabel = "Coffee",
                foodId = userPreferencesRepository.caffeineWidgetSlot1FoodId.first()
            ),
            CaffeineWidgetSlot(
                slotIndex = 2,
                fallbackLabel = "Soda",
                foodId = userPreferencesRepository.caffeineWidgetSlot2FoodId.first()
            ),
            CaffeineWidgetSlot(
                slotIndex = 3,
                fallbackLabel = "Monster",
                foodId = userPreferencesRepository.caffeineWidgetSlot3FoodId.first()
            )
        ).map { slot ->
            val food = slot.foodId?.let { foodRepository.getById(it) }
            slot.copy(
                buttonLabel = food?.name ?: slot.fallbackLabel,
                isConfigured = food != null
            )
        }

        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(
                context.packageName,
                R.layout.widget_caffeine
            )

            views.setTextViewText(
                R.id.caffeine_widget_total,
                "${caffeineMg.roundToWidgetInt()} mg"
            )

            views.setTextViewText(
                R.id.caffeine_widget_status,
                if (slots.any { it.isConfigured }) {
                    "Quick log one serving"
                } else {
                    "Configure slots in Dashboard Settings"
                }
            )

            views.setTextViewText(
                R.id.caffeine_widget_macro_calories,
                "${macros.caloriesKcal.roundToWidgetInt()} kcal"
            )
            views.setTextViewText(
                R.id.caffeine_widget_macro_protein,
                "P ${macros.proteinG.roundToWidgetInt()}g"
            )
            views.setTextViewText(
                R.id.caffeine_widget_macro_carbs,
                "C ${macros.carbsG.roundToWidgetInt()}g"
            )
            views.setTextViewText(
                R.id.caffeine_widget_macro_fat,
                "F ${macros.fatG.roundToWidgetInt()}g"
            )

            bindSlotButton(
                context = context,
                views = views,
                slot = slots[0],
                buttonViewId = R.id.caffeine_widget_slot_1
            )

            bindSlotButton(
                context = context,
                views = views,
                slot = slots[1],
                buttonViewId = R.id.caffeine_widget_slot_2
            )

            bindSlotButton(
                context = context,
                views = views,
                slot = slots[2],
                buttonViewId = R.id.caffeine_widget_slot_3
            )

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        scheduleNextMidnightRefresh(context)
    }

    private fun bindSlotButton(
        context: Context,
        views: RemoteViews,
        slot: CaffeineWidgetSlot,
        buttonViewId: Int
    ) {
        views.setTextViewText(buttonViewId, slot.buttonLabel)

        if (!slot.isConfigured) {
            views.setOnClickPendingIntent(buttonViewId, null)
            return
        }

        val intent = Intent(context, CaffeineWidgetProvider::class.java).apply {
            action = ACTION_LOG_SLOT
            putExtra(EXTRA_SLOT_INDEX, slot.slotIndex)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            slot.slotIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(buttonViewId, pendingIntent)
    }

    private suspend fun foodIdForSlot(slotIndex: Int): Long? =
        when (slotIndex) {
            1 -> userPreferencesRepository.caffeineWidgetSlot1FoodId.first()
            2 -> userPreferencesRepository.caffeineWidgetSlot2FoodId.first()
            3 -> userPreferencesRepository.caffeineWidgetSlot3FoodId.first()
            else -> null
        }

    private fun scheduleNextMidnightRefresh(context: Context) {
        val zoneId = ZoneId.systemDefault()
        val nextMidnightMillis =
            LocalDate.now(zoneId)
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()

        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnightMillis,
            midnightRefreshPendingIntent(context)
        )
    }

    private fun cancelNextMidnightRefresh(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.cancel(midnightRefreshPendingIntent(context))
    }

    private fun midnightRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CaffeineWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_WIDGET
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT_REFRESH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class CaffeineWidgetSlot(
        val slotIndex: Int,
        val fallbackLabel: String,
        val foodId: Long?,
        val buttonLabel: String = fallbackLabel,
        val isConfigured: Boolean = false
    )

    companion object {
        /**
         * Requests a widget refresh from app-side flows such as Quick Add or Dashboard logging.
         *
         * This does not log anything. It only asks the widget provider to re-read normal log
         * snapshots and redraw the home-screen widget.
         */
        fun requestRefresh(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, CaffeineWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            appContext.sendBroadcast(intent)
        }

        private const val ACTION_LOG_SLOT =
            "com.example.adobongkangkong.widget.ACTION_LOG_CAFFEINE_SLOT"

        private const val ACTION_REFRESH_WIDGET =
            "com.example.adobongkangkong.widget.ACTION_REFRESH_CAFFEINE_WIDGET"

        private const val EXTRA_SLOT_INDEX =
            "com.example.adobongkangkong.widget.EXTRA_SLOT_INDEX"

        private const val REQUEST_CODE_MIDNIGHT_REFRESH = 1001
    }
}

private fun Double.roundToWidgetInt(): String =
    "%.0f".format(this)