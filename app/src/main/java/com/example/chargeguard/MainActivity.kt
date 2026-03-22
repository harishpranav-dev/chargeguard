package com.example.chargeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.chargeguard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy {
        getSharedPreferences("chargeguard", MODE_PRIVATE)
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    android.util.Log.d("ChargeGuard", "Power disconnected — firing alert!")
                    prefs.edit().putBoolean("alert_active", true).apply()
                    NotificationHelper.createNotificationChannels(context)
                    val percent = getBatteryPercent()
                    NotificationHelper.showAlertNotification(context, percent)
                    NotificationHelper.startLoopingVibration(context)
                    runOnUiThread { setNotChargingState() }
                    historyItems.add(0, HistoryItem(
                        title = "Charger disconnected",
                        batteryPercent = getBatteryPercent(),
                        timestamp = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date()),
                        isAlert = true
                    ))
                    saveHistory()
                    runOnUiThread { renderHistory() }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    android.util.Log.d("ChargeGuard", "Power connected — clearing alerts")
                    prefs.edit().putBoolean("alert_active", false).apply()
                    NotificationHelper.cancelAllNotifications(context)
                    NotificationHelper.stopVibration(context)
                    runOnUiThread { setIdleState() }
                    historyItems.add(0, HistoryItem(
                        title = "Charging started",
                        batteryPercent = getBatteryPercent(),
                        timestamp = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date()),
                        isAlert = false
                    ))
                    saveHistory()
                    runOnUiThread { renderHistory() }
                    historyItems.add(0, HistoryItem(
                    title = "Charging started",
                    batteryPercent = getBatteryPercent(),
                    timestamp = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date()),
                    isAlert = false
                    ))
                    saveHistory()
                    runOnUiThread { renderHistory() }
                }
            }
        }
    }

    private val stopAlertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.chargeguard.STOP_ALERT") {
                stopActiveAlert()
            }
        }
    }

    private fun getBatteryPercent(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateBatteryUI(intent)
        }
    }

    private val historyItems = mutableListOf<HistoryItem>()
    private var showingAllHistory = false
    private val historyPreviewCount = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupClickListeners()
        registerBatteryReceiver()
        requestBatteryOptimizationExemption()
        requestNotificationPermission()
        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        )
        registerReceiver(
            stopAlertReceiver,
            IntentFilter("com.example.chargeguard.STOP_ALERT"),
            Context.RECEIVER_NOT_EXPORTED
        )
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        // Sync UI with current battery state
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryIntent?.let { updateBatteryUI(it) }
        // Show Stop Alert button if alert is still active
        if (prefs.getBoolean("alert_active", false)) {
            setNotChargingState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(powerReceiver)
        unregisterReceiver(stopAlertReceiver)
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun updateBatteryUI(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        binding.tvBatteryPercent.text = "$percent%"

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        binding.tvVoltage.text = if (voltage > 0) "${voltage / 1000.0f}V" else "—"

        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        binding.tvTemperature.text = if (temp > 0) "${temp / 10.0f}°C" else "—"

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        // Don't override UI if alert is active
        if (prefs.getBoolean("alert_active", false)) return

        when {
            plugged == 0 -> setIdleState()
            status == BatteryManager.BATTERY_STATUS_CHARGING && currentNow > 1500000 -> setFastChargingState()
            status == BatteryManager.BATTERY_STATUS_CHARGING -> setChargingOkState()
            status == BatteryManager.BATTERY_STATUS_FULL -> setChargingOkState()
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING -> setNotChargingState()
            else -> setIdleState()
        }
    }

    private fun setIdleState() {
        binding.tvBatteryPercent.setTextColor(getColor(R.color.text_primary))
        binding.tvStatusLabel.text = getString(R.string.status_idle)
        binding.tvStatusLabel.setTextColor(getColor(R.color.text_secondary))
        binding.tvStatusDetail.text = "Waiting for charger"
        setDotColor(R.color.idle)
        binding.btnStopAlert.visibility = View.GONE
    }

    private fun setChargingOkState() {
        binding.tvBatteryPercent.setTextColor(getColor(R.color.charging_ok))
        binding.tvStatusLabel.text = getString(R.string.status_charging_ok)
        binding.tvStatusLabel.setTextColor(getColor(R.color.charging_ok))
        binding.tvStatusDetail.text = "Charging normally"
        setDotColor(R.color.charging_ok)
        binding.btnStopAlert.visibility = View.GONE
    }

    private fun setFastChargingState() {
        binding.tvBatteryPercent.setTextColor(getColor(R.color.fast_charging))
        binding.tvStatusLabel.text = getString(R.string.status_fast_charging)
        binding.tvStatusLabel.setTextColor(getColor(R.color.fast_charging))
        binding.tvStatusDetail.text = "Fast charging active"
        setDotColor(R.color.fast_charging)
        binding.btnStopAlert.visibility = View.GONE
    }

    private fun setNotChargingState() {
        binding.tvBatteryPercent.setTextColor(getColor(R.color.not_charging))
        binding.tvStatusLabel.text = getString(R.string.status_not_charging)
        binding.tvStatusLabel.setTextColor(getColor(R.color.not_charging))
        binding.tvStatusDetail.text = "Charger disconnected or not working"
        setDotColor(R.color.not_charging)
        binding.btnStopAlert.visibility = View.VISIBLE
    }

    private fun setDotColor(colorRes: Int) {
        binding.statusDot.background.setTint(getColor(colorRes))
    }

    private fun stopActiveAlert() {
        prefs.edit().putBoolean("alert_active", false).apply()
        NotificationHelper.cancelAllNotifications(this)
        NotificationHelper.stopVibration(this)
        runOnUiThread { setIdleState() }
    }

    private fun setupClickListeners() {
        binding.btnStopAlert.setOnClickListener {
            stopActiveAlert()
        }
        binding.tvClearHistory.setOnClickListener {
            historyItems.clear()
            prefs.edit().remove("history_json").apply()
            renderHistory()
        }
        binding.tvShowMore.setOnClickListener {
            showingAllHistory = !showingAllHistory
            binding.tvShowMore.text = if (showingAllHistory)
                getString(R.string.label_show_less)
            else
                getString(R.string.label_show_more)
            renderHistory()
        }
    }

    fun addHistoryItem(item: HistoryItem) {
        historyItems.add(0, item)
        renderHistory()
    }

    private fun renderHistory() {
        binding.historyContainer.removeAllViews()
        if (historyItems.isEmpty()) {
            binding.tvHistoryEmpty.visibility = View.VISIBLE
            binding.tvShowMore.visibility = View.GONE
            return
        }
        binding.tvHistoryEmpty.visibility = View.GONE
        val itemsToShow = if (showingAllHistory || historyItems.size <= historyPreviewCount) {
            historyItems
        } else {
            historyItems.take(historyPreviewCount)
        }
        for (item in itemsToShow) {
            binding.historyContainer.addView(buildHistoryCard(item))
        }
        binding.tvShowMore.visibility = if (historyItems.size > historyPreviewCount)
            View.VISIBLE else View.GONE
    }

    private fun buildHistoryCard(item: HistoryItem): View {
        val dp = resources.displayMetrics.density
        val card = CardView(this).apply {
            radius = 12f * dp
            cardElevation = 0f
            setCardBackgroundColor(getColor(R.color.card_background))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val strip = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (4 * dp).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getColor(if (item.isAlert) R.color.not_charging else R.color.charging_ok))
        }
        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (16 * dp).toInt(), (12 * dp).toInt(),
                (16 * dp).toInt(), (12 * dp).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        textLayout.addView(TextView(this).apply {
            text = item.title
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        textLayout.addView(TextView(this).apply {
            text = "${item.batteryPercent}% · ${item.timestamp}"
            textSize = 12f
            setTextColor(getColor(R.color.text_secondary))
        })
        inner.addView(strip)
        inner.addView(textLayout)
        card.addView(inner)
        return card
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(
                    android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                )
                startActivity(intent)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }
    private fun saveHistory() {
        val json = StringBuilder("[")
        historyItems.forEachIndexed { index, item ->
            json.append("""{"title":"${item.title}","percent":${item.batteryPercent},"time":"${item.timestamp}","alert":${item.isAlert}}""")
            if (index < historyItems.size - 1) json.append(",")
        }
        json.append("]")
        prefs.edit().putString("history_json", json.toString()).apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("history_json", "[]") ?: "[]"
        if (json == "[]") return
        try {
            val trimmed = json.removePrefix("[").removeSuffix("]")
            trimmed.split("},{").forEach { entry ->
                val clean = entry.removePrefix("{").removeSuffix("}")
                val map = mutableMapOf<String, String>()
                clean.split(",\"").forEach { part ->
                    val kv = part.removePrefix("\"").split("\":\"")
                    if (kv.size == 2) map[kv[0]] = kv[1].removeSuffix("\"")
                    else {
                        val kv2 = part.removePrefix("\"").split("\":")
                        if (kv2.size == 2) map[kv2[0]] = kv2[1]
                    }
                }
                val item = HistoryItem(
                    title = map["title"] ?: return@forEach,
                    batteryPercent = map["percent"]?.toIntOrNull() ?: 0,
                    timestamp = map["time"] ?: "",
                    isAlert = map["alert"] == "true"
                )
                historyItems.add(item)
            }
            renderHistory()
        } catch (e: Exception) {
            android.util.Log.e("ChargeGuard", "Failed to load history: ${e.message}")
        }
    }
}