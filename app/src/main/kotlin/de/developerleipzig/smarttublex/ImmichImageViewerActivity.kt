package de.developerleipzig.smarttublex

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import de.developerleipzig.immichapi.network.ImmichRetrofitHelper
import de.developerleipzig.immichapi.network.ImmichUrlHelper
import de.developerleipzig.immichapi.prefs.ImmichPrefs
import de.developerleipzig.immichserviceinterfaces.data.ImmichAsset
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fullscreen Immich still-image viewer (TV). Photos must not go through ExoPlayer.
 *
 * [launchMode] singleTop + [Intent.FLAG_ACTIVITY_CLEAR_TOP] so a leftover viewer under
 * [PlaybackActivity] does not keep showing the previous asset.
 */
class ImmichImageViewerActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val loadGeneration = AtomicInteger(0)
    private var imageView: ImageView? = null
    private var progress: ProgressBar? = null
    private var status: TextView? = null
    private var loadedBitmap: Bitmap? = null
    private var uiReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceRef = WeakReference(this)
        ensureUi()
        bindFromIntent(intent, "onCreate")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        bindFromIntent(intent, "onNewIntent")
    }

    override fun onDestroy() {
        loadGeneration.incrementAndGet()
        executor.shutdownNow()
        loadedBitmap?.recycle()
        loadedBitmap = null
        imageView?.setImageDrawable(null)
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }

    private fun ensureUi() {
        if (uiReady) return
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 18f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        root.addView(imageView)
        root.addView(progress)
        root.addView(status)
        setContentView(root)
        uiReady = true
    }

    private fun bindFromIntent(intent: Intent, source: String) {
        val assetId = resolveAssetId(intent)
        Log.i(
            SmartTublexApplication.TAG,
            "ImmichImageViewer: $source assetId=$assetId data=${intent.data}"
        )
        if (assetId.isNullOrEmpty()) {
            Log.e(SmartTublexApplication.TAG, "ImmichImageViewer: missing asset id — finishing")
            finish()
            return
        }
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (!title.isNullOrEmpty()) {
            window.decorView.contentDescription = title
        }
        loadImage(assetId)
    }

    private fun loadImage(assetId: String) {
        val generation = loadGeneration.incrementAndGet()
        progress?.visibility = View.VISIBLE
        status?.visibility = View.GONE
        imageView?.setImageDrawable(null)
        executor.execute {
            try {
                val prefs = ImmichPrefs.instance(applicationContext)
                val serverUrl = prefs.serverUrl
                val apiKey = prefs.apiKey
                if (serverUrl.isNullOrEmpty()) {
                    throw IllegalStateException("Immich server URL not configured")
                }
                val url = ImmichUrlHelper.assetOriginalUrl(
                    ImmichUrlHelper.normalizeApiBaseUrl(serverUrl),
                    assetId,
                    apiKey
                ) ?: throw IllegalStateException("Unable to build Immich original URL")
                Log.i(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: fetching assetId=$assetId gen=$generation"
                )

                val response = ImmichRetrofitHelper.client()
                    .newCall(Request.Builder().url(url).get().build())
                    .execute()
                response.use { resp ->
                    if (!resp.isSuccessful()) {
                        throw IllegalStateException("Immich image HTTP ${resp.code()}")
                    }
                    val bytes = resp.body()?.bytes()
                        ?: throw IllegalStateException("Empty Immich image body")
                    val metrics = resources.displayMetrics
                    val bitmap = decodeSampled(
                        bytes,
                        metrics.widthPixels,
                        metrics.heightPixels,
                        cacheDir
                    ) ?: throw IllegalStateException("Unable to decode Immich image")

                    runOnUiThread {
                        if (isFinishing || generation != loadGeneration.get()) {
                            bitmap.recycle()
                            return@runOnUiThread
                        }
                        loadedBitmap?.recycle()
                        loadedBitmap = bitmap
                        imageView?.setImageBitmap(bitmap)
                        progress?.visibility = View.GONE
                        Log.i(
                            SmartTublexApplication.TAG,
                            "ImmichImageViewer: shown assetId=$assetId " +
                                "${bitmap.width}x${bitmap.height}"
                        )
                    }
                }
            } catch (t: Throwable) {
                if (generation != loadGeneration.get()) return@execute
                Log.e(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: load failed assetId=$assetId",
                    t
                )
                runOnUiThread {
                    if (isFinishing || generation != loadGeneration.get()) return@runOnUiThread
                    progress?.visibility = View.GONE
                    status?.text = t.message?.takeIf { it.isNotEmpty() } ?: "Image load failed"
                    status?.visibility = View.VISIBLE
                    MessageHelpers.showMessage(
                        this,
                        t.message?.takeIf { it.isNotEmpty() } ?: "Image load failed"
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_ASSET_ID = "immich_asset_id"
        private const val EXTRA_TITLE = "immich_title"
        private const val ACTION_VIEW_IMAGE =
            "de.developerleipzig.smarttublex.action.VIEW_IMMICH_IMAGE"

        @Volatile
        private var instanceRef: WeakReference<ImmichImageViewerActivity>? = null

        private fun resolveAssetId(intent: Intent): String? {
            val fromExtra = intent.getStringExtra(EXTRA_ASSET_ID)
            if (!fromExtra.isNullOrEmpty()) return fromExtra
            return intent.data?.lastPathSegment
        }

        /**
         * Prefer starting from an [Activity] host (no [Intent.FLAG_ACTIVITY_NEW_TASK]) so Browse
         * stays under the viewer without tearing down Playback state for the next video.
         *
         * @return true when [startActivity] was invoked without throwing
         */
        fun open(context: Context, asset: ImmichAsset): Boolean {
            val host = context
            val intent = Intent(ACTION_VIEW_IMAGE).apply {
                component = ComponentName(
                    host.packageName,
                    ImmichImageViewerActivity::class.java.name
                )
                data = Uri.parse("immich://asset/${asset.id}")
                putExtra(EXTRA_ASSET_ID, asset.id)
                putExtra(EXTRA_TITLE, asset.title)
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                if (host !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            return try {
                host.startActivity(intent)
                Log.i(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: startActivity ok package=${host.packageName} " +
                        "assetId=${asset.id} host=${host.javaClass.simpleName}"
                )
                true
            } catch (e: ActivityNotFoundException) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: activity not in manifest " +
                        "(package=${host.packageName}) — rebuild packageWrapperApk",
                    e
                )
                false
            } catch (t: Throwable) {
                Log.e(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: startActivity failed assetId=${asset.id}",
                    t
                )
                false
            }
        }

        private fun decodeSampled(
            bytes: ByteArray,
            reqWidth: Int,
            reqHeight: Int,
            cacheDir: File
        ): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            // WRAPPER: Immich /original keeps EXIF orientation; BitmapFactory ignores it
            return applyExifOrientation(bytes, decoded, cacheDir)
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val height = options.outHeight
            val width = options.outWidth
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                var halfH = height / 2
                var halfW = width / 2
                while (halfH / inSampleSize >= reqHeight && halfW / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }

        /**
         * Phone JPEGs often store rotation in EXIF while pixel data stays landscape.
         * Immich thumbnails bake orientation in; /original does not.
         */
        private fun applyExifOrientation(bytes: ByteArray, source: Bitmap, cacheDir: File): Bitmap {
            val orientation = readExifOrientation(bytes, cacheDir)
            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED
            ) {
                return source
            }
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.setRotate(180f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return source
            }
            return try {
                val rotated = Bitmap.createBitmap(
                    source, 0, 0, source.width, source.height, matrix, true
                )
                if (rotated !== source) {
                    source.recycle()
                }
                rotated
            } catch (_: OutOfMemoryError) {
                Log.w(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: EXIF rotate OOM — showing unrotated"
                )
                source
            }
        }

        private fun readExifOrientation(bytes: ByteArray, cacheDir: File): Int {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } else {
                    // EXIF lives in the first APP1 segment; avoid writing multi‑MB originals.
                    val prefixLen = minOf(bytes.size, 256 * 1024)
                    val tmp = File.createTempFile("immich_exif_", ".bin", cacheDir)
                    try {
                        tmp.outputStream().use { it.write(bytes, 0, prefixLen) }
                        ExifInterface(tmp.absolutePath).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } finally {
                        tmp.delete()
                    }
                }
            } catch (t: Throwable) {
                Log.w(
                    SmartTublexApplication.TAG,
                    "ImmichImageViewer: EXIF read failed — assuming upright",
                    t
                )
                ExifInterface.ORIENTATION_NORMAL
            }
        }
    }
}
