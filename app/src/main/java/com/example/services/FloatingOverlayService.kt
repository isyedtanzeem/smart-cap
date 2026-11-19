package com.example.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.domain.model.OrderEvaluation
import java.util.Locale
import kotlin.math.abs

/**
 * Compact, draggable, non-obstructive recommendation overlay positioned near the top-right
 * so it never covers Rapido Captain's bottom Accept button. Never auto-clicks Rapido UI.
 */
class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var titleView: TextView? = null
    private var payoutView: TextView? = null
    private var metricsView: TextView? = null
    private var reasonView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        ensureOverlayCreated()

        val orderNum = intent?.getIntExtra(EXTRA_ORDER_NUMBER, 1) ?: 1
        val orderType = intent?.getStringExtra(EXTRA_ORDER_TYPE) ?: "Delivery"
        val payout = intent?.getStringExtra(EXTRA_PAYOUT) ?: "—"
        val distance = intent?.getStringExtra(EXTRA_DISTANCE) ?: "—"
        val rate = intent?.getStringExtra(EXTRA_RATE) ?: "—"
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "Top ranked order"

        titleView?.text = "BEST VALUE • #$orderNum $orderType"
        payoutView?.text = payout
        metricsView?.text = "$distance  •  $rate"
        reasonView?.text = reason

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureOverlayCreated() {
        if (overlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(110)
        }
        layoutParams = params

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0B3D26"))
                setStroke(dp(2), Color.parseColor("#39D98A"))
                cornerRadius = dp(16).toFloat()
            }
            elevation = dp(8).toFloat()
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            setTextColor(Color.parseColor("#9DF5C4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            text = "BEST VALUE"
        }
        titleView = title

        val closeBtn = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, dp(2), 0)
            text = "✕"
            contentDescription = "Dismiss recommendation overlay"
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }

        headerRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(closeBtn)

        val payout = TextView(this).apply {
            setTextColor(Color.parseColor("#FFD147"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }
        payoutView = payout

        val metrics = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
        }
        metricsView = metrics

        val reason = TextView(this).apply {
            setTextColor(Color.parseColor("#D4ECE0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 2
            maxWidth = dp(215)
        }
        reasonView = reason

        container.addView(headerRow)
        container.addView(payout)
        container.addView(metrics)
        container.addView(reason)

        // Allow dragging to reposition and tap to open Smart Captain dashboard
        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchStartX - event.rawX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(dp(40))
                    wm.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val openIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(openIntent)
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(container, params)
        overlayView = container
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val ACTION_SHOW = "com.example.action.SHOW_OVERLAY"
        private const val ACTION_HIDE = "com.example.action.HIDE_OVERLAY"
        private const val EXTRA_ORDER_NUMBER = "extra_order_number"
        private const val EXTRA_ORDER_TYPE = "extra_order_type"
        private const val EXTRA_PAYOUT = "extra_payout"
        private const val EXTRA_DISTANCE = "extra_distance"
        private const val EXTRA_RATE = "extra_rate"
        private const val EXTRA_REASON = "extra_reason"

        fun showOrUpdate(context: Context, evaluation: OrderEvaluation) {
            if (!Settings.canDrawOverlays(context)) return
            val payout = evaluation.totalExpectedFare?.let { "₹${fmt(it)}" } ?: "—"
            val distance = evaluation.totalDistanceKm?.let { "${fmt(it)} km" } ?: "? km"
            val rate = evaluation.farePerKm?.let { "₹${fmt(it)}/km" } ?: "—"

            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_ORDER_NUMBER, evaluation.order.orderNumber)
                putExtra(EXTRA_ORDER_TYPE, evaluation.order.orderType.displayLabel)
                putExtra(EXTRA_PAYOUT, payout)
                putExtra(EXTRA_DISTANCE, distance)
                putExtra(EXTRA_RATE, rate)
                putExtra(EXTRA_REASON, evaluation.recommendationReason)
            }
            runCatching { context.startService(intent) }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            runCatching { context.startService(intent) }
        }

        private fun fmt(v: Double): String =
            if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
    }
}
