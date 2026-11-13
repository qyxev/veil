package com.qyxev.veil.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.qyxev.veil.ui.TranslationViewportView
import kotlinx.coroutines.*

class ScreenTranslatorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var viewportView: TranslationViewportView
    private lateinit var windowParams: WindowManager.LayoutParams

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var isInitialized = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        initScreenMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("DATA", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("DATA")
        }

        if (resultCode == Activity.RESULT_OK && data != null && !isInitialized) {
            isInitialized = true

            // 1. Start foreground notification safely with MediaProjection token ready
            startForegroundNotification()

            // 2. Initialize screen capture projection
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            setupVirtualDisplay()

            // 3. Render the glassy viewfinder overlay onto the screen
            setupViewportOverlay()
        } else if (!isInitialized) {
            // If permission was denied or missing, shut down cleanly immediately
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun initScreenMetrics() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    private fun setupVirtualDisplay() {
        // 1. Register a callback FIRST (Required for Android 14+)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                terminateServiceCompletely()
            }
        }, Handler(Looper.getMainLooper()))

        // 2. Now it's safe to create the virtual display
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "VeilCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun setupViewportOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        windowParams = WindowManager.LayoutParams(
            54, 150,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 54
            y = screenHeight / 3
        }

        viewportView = TranslationViewportView(
            context = this,
            windowManager = windowManager,
            layoutParams = windowParams,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onCaptureRequested = { cropRect -> captureAndCropText(cropRect) },
            onDismissRequested = { terminateServiceCompletely() }
        )

        windowManager.addView(viewportView, windowParams)
    }

    private fun captureAndCropText(cropRect: Rect) {
        viewportView.visibility = View.INVISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (::viewportView.isInitialized) {
                viewportView.visibility = View.VISIBLE
            }

            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)

                val safeLeft = cropRect.left.coerceIn(0, cleanBitmap.width - 10)
                val safeTop = cropRect.top.coerceIn(0, cleanBitmap.height - 10)
                val safeWidth = cropRect.width().coerceAtMost(cleanBitmap.width - safeLeft)
                val safeHeight = cropRect.height().coerceAtMost(cleanBitmap.height - safeTop)

                if (safeWidth > 0 && safeHeight > 0) {
                    val croppedBitmap = Bitmap.createBitmap(cleanBitmap, safeLeft, safeTop, safeWidth, safeHeight)
                    runOnDeviceOCR(croppedBitmap)
                }
            }
        }, 100)
    }

    private fun runOnDeviceOCR(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text.ifEmpty { "No text found in region" }
                val translatedMock = "[ $extractedText ]"
                if (::viewportView.isInitialized) {
                    viewportView.setTranslatedResult(translatedMock)
                }
            }
            .addOnFailureListener {
                if (::viewportView.isInitialized) {
                    viewportView.setTranslatedResult("Translation Error")
                }
            }
    }

    private fun terminateServiceCompletely() {
        try {
            if (::viewportView.isInitialized) {
                windowManager.removeView(viewportView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        virtualDisplay?.release()
        mediaProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val channelId = "veil_service_channel"
        val channel = NotificationChannel(channelId, "Veil Viewfinder Active", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Veil Viewfinder Active")
            .setContentText("Drag box over text and tap to translate.")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        terminateServiceCompletely()
    }
}