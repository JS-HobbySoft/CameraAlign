/*
 * Copyright (c) 2022 JS HobbySoft.
 * All rights reserved.
 */

package org.jshobbysoft.cameraalign

//import android.util.Log

import android.Manifest
import android.annotation.SuppressLint
//import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
//import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.exifinterface.media.ExifInterface
import com.google.android.material.snackbar.Snackbar
import org.jshobbysoft.cameraalign.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.core.content.edit
import androidx.core.graphics.get
import androidx.core.view.doOnLayout


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageUri: Uri? = null
    private var imageCapture: ImageCapture? = null
    private var cameraSel = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private var touchPixel: Int? = null
    private var vflipState = 1
    private var hflipState = 1

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        val useGreenScreen =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("greenScreenEffectKey", false)
        val greenScreenEffectTarget =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString("greenScreenEffectTargetKey", "transparentColorPreview")

        // trivial change to test Git commits
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera(cameraSel, greenScreenEffectTarget)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        //      use default image if no shared preference has been saved
        val backgroundUriString = androidx.preference.PreferenceManager
            .getDefaultSharedPreferences(this).getString("background_uri_key", "")
        if (backgroundUriString == "") {
            viewBinding.basisImage.setImageResource(R.drawable.ic_launcher_background)
        } else {
            try {
                val backgroundUri = backgroundUriString?.toUri()
                viewBinding.basisImage.setImageURI(backgroundUri)
                viewBinding.foundation.setImageURI(backgroundUri)
                val readOnlyMode = "r"
                contentResolver.openFileDescriptor(
                    backgroundUri!!, readOnlyMode
                ).use { pfd ->
//                  Perform operations on "pfd".
//                  https://www.geeksforgeeks.org/what-is-exifinterface-in-android/
                    try {
                        val gfgExif = ExifInterface(pfd!!.fileDescriptor)
                        val zoomStateString = gfgExif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO)
                        viewBinding.zoomSeekBar.progress = zoomStateString!!.toFloat().toInt()
                        camera!!.cameraControl.setLinearZoom(zoomStateString.toFloat() / 100.toFloat())
                    } catch (e: Exception) {
                        println("Error: $e")
                    }
                }
            } catch (errorUri: Exception) {
                viewBinding.basisImage.setImageResource(R.drawable.ic_launcher_background)
                Toast.makeText(
                    this,
                    "Background file does not exist.  Please choose a different file.",
                    Toast.LENGTH_LONG
                ).show()
                println("Error: $errorUri")
            }
        }


//        viewBinding.viewFinder.doOnLayout {
//
//
//        }
////         Scale the camera preview to match the basis image
//        val bIHeight = viewBinding.basisImage.height
//        val bIWidth = viewBinding.basisImage.width
//        val vFHeight = viewBinding.viewFinder.height
//        val vFWidth = viewBinding.viewFinder.width
//
//        println("Dimensions: $bIHeight $bIWidth $vFHeight $vFWidth")
//
//        if (bIHeight < 30) {
//            viewBinding.viewFinder.layoutParams.height = 300
//        } else if (bIWidth < 30) {
//            viewBinding.viewFinder.layoutParams.width = 300
//        } else {
//            viewBinding.viewFinder.layoutParams.height = viewBinding.basisImage.height
//            viewBinding.viewFinder.layoutParams.width = viewBinding.basisImage.width
//        }
//
        //      set image transparency using value from SharedPrefs
        val transparencyValue =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString("textTransparencyKey", "125")!!.toFloat()
        val transparencyValueFloat = transparencyValue / 255
        viewBinding.basisImage.alpha = transparencyValueFloat

        if (useGreenScreen) {
            if (greenScreenEffectTarget == "transparentColorPreview") {
                viewBinding.viewFinder.alpha = 0f
            } else if (greenScreenEffectTarget == "transparentColorImage") {
                viewBinding.basisImage.alpha = 0f

                val rawImageDrawable = viewBinding.basisImage.drawable
                val rawImageBitmap = rawImageDrawable.toBitmap()
                val transparentImageBitmap = toTransparency(rawImageBitmap)
                viewBinding.transparentView.setImageBitmap(transparentImageBitmap)
            }

            //        https://stackoverflow.com/questions/57139275/how-to-get-colour-of-touched-area-in-canvas-android
            viewBinding.transparentView.setOnTouchListener { _, event ->
//                https://stackoverflow.com/questions/47170075/kotlin-ontouchlistener-called-but-it-does-not-override-performclick
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        var red: Int
                        var green: Int
                        var blue: Int
                        val drawable = viewBinding.transparentView.drawable
                        val bitmap = drawable.toBitmap()
//                        touchPixel = bitmap.getPixel(event.x.toInt(), event.y.toInt())
                        touchPixel = bitmap[event.x.toInt(), event.y.toInt()]
//            https://stackoverflow.com/questions/46701042/kotlin-smart-cast-is-impossible-because-the-property-could-have-been-changed-b
                        touchPixel.let {
                            red = Color.red(it!!)
                            green = Color.green(it)
                            blue = Color.blue(it)
                        }

                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                            .edit { putString("textRedKey", red.toString()) }
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                            .edit { putString("textGreenKey", green.toString()) }
                        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                            .edit { putString("textBlueKey", blue.toString()) }

                        Toast.makeText(
                            this,
                            "Chosen color was red: $red / green: $green / blue: $blue",
                            Toast.LENGTH_SHORT
                        ).show()

                        val rawImageDrawable = viewBinding.basisImage.drawable
                        val rawImageBitmap = rawImageDrawable.toBitmap()
                        val transparentImageBitmap = toTransparency(rawImageBitmap)
                        viewBinding.transparentView.setImageBitmap(transparentImageBitmap)
                    }
                }
                true
            }
        } else {
            viewBinding.viewFinder.visibility = View.VISIBLE
            viewBinding.basisImage.visibility = View.VISIBLE
            viewBinding.transparentView.visibility = View.INVISIBLE
        }

        viewBinding.buttonLoadPicture.setOnClickListener {
            val gallery =
                Intent(Intent.ACTION_OPEN_DOCUMENT, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            resultLauncher.launch(gallery)
        }

        // Set up the listeners for take photo button
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }

        // Set up image rotation button
        viewBinding.imageRotateButton.setOnClickListener {
            viewBinding.basisImage.rotation = viewBinding.basisImage.rotation + 90
        }

        // Set up image vertical flip button
        viewBinding.imageVflipButton.setOnClickListener {
            if (vflipState == 1) {
                viewBinding.basisImage.scaleX = -1f
                vflipState = -1
            } else if (vflipState == -1) {
                viewBinding.basisImage.scaleX = 1f
                vflipState = 1
            }
        }

        // Set up image horizontal flip button
        viewBinding.imageHflipButton.setOnClickListener {
            if (hflipState == 1) {
                viewBinding.basisImage.scaleY = -1f
                hflipState = -1
            } else if (hflipState == -1) {
                viewBinding.basisImage.scaleY = 1f
                hflipState = 1
            }
        }

        viewBinding.cameraFlipButton.setOnClickListener {
            if (cameraSel == CameraSelector.DEFAULT_FRONT_CAMERA) {
                cameraSel = CameraSelector.DEFAULT_BACK_CAMERA
            } else if (cameraSel == CameraSelector.DEFAULT_BACK_CAMERA) {
                cameraSel = CameraSelector.DEFAULT_FRONT_CAMERA
            }
            startCamera(cameraSel, greenScreenEffectTarget)
        }
//    https://stackoverflow.com/questions/72339792/how-to-implement-zoom-with-seekbar-on-camerax
//    https://github.com/Pinkal7600/camera-samples/blob/master/CameraXBasic/app/src/main/java/com/android/example/cameraxbasic/fragments/CameraFragment.kt
        viewBinding.zoomSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                camera!!.cameraControl.setLinearZoom(progress / 100.toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                imageUri = data?.data
                val contentResolver = applicationContext.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                imageUri?.let { contentResolver.takePersistableUriPermission(it, takeFlags) }
                viewBinding.basisImage.setImageURI(imageUri)
//                val sharedPref =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
//                with(sharedPref.edit()) {
//                    putString("background_uri_key", imageUri.toString())
//                    apply()
//                }
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .edit { putString("background_uri_key", imageUri.toString()) }

//                  "rw" for read-and-write.
//                  "rwt" for truncating or overwriting existing file contents.
                val readOnlyMode = "r"
                contentResolver.openFileDescriptor(
                    imageUri!!, readOnlyMode
                ).use { pfd ->
//                  Perform operations on "pfd".
//                  https://www.geeksforgeeks.org/what-is-exifinterface-in-android/
                    try {
                        val gfgExif = ExifInterface(pfd!!.fileDescriptor)
                        val zoomStateString =
                            gfgExif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO)
                        viewBinding.zoomSeekBar.progress = zoomStateString!!.toFloat().toInt()
                        camera!!.cameraControl.setLinearZoom(zoomStateString.toFloat() / 100.toFloat())
                    } catch (e: Exception) {
                        println("Error: $e")
                    }
                }
            }
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val settingsIntent = Intent(this, SettingsActivity::class.java)
                startActivity(settingsIntent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startCamera(cameraSelector: CameraSelector, gSET: String?) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                }

            if (gSET == "transparentColorPreview") {
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
//                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    runOnUiThread {
                        val rawPreview = viewBinding.viewFinder.bitmap
                        val bitmapTransparency = toTransparency(rawPreview)
                        viewBinding.transparentView.setImageBitmap(bitmapTransparency)
                    }
                    imageProxy.close()
                }
                imageCapture = ImageCapture.Builder().build()
                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalysis
                    )
                } catch (exc: Exception) {
                    println("Exception: $exc")
//                Log.e(TAG, "Use case binding failed", exc)
                }
            } else {
                imageCapture = ImageCapture.Builder().build()

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                    )
                } catch (exc: Exception) {
                    println("Exception: $exc")
//                Log.e(TAG, "Use case binding failed", exc)
                }
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        //        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val greenScreenEffectTarget =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString("greenScreenEffectTargetKey", "transparentColorPreview")

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(cameraSel, greenScreenEffectTarget)
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
//                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
//                  save zoom in exif data
//                  https://developer.android.com/training/data-storage/shared/media
//                  Open a specific media item using ParcelFileDescriptor.
                    val resolver = applicationContext.contentResolver
//                  "rw" for read-and-write.
//                  "rwt" for truncating or overwriting existing file contents.
                    val readOnlyMode = "rw"
                    resolver.openFileDescriptor(
                        output.savedUri!!, readOnlyMode
                    ).use { pfd ->
                        // Perform operations on "pfd".
//                  https://www.geeksforgeeks.org/what-is-exifinterface-in-android/
                        val zS = viewBinding.zoomSeekBar.progress
                        val gfgExif = ExifInterface(pfd!!.fileDescriptor)
                        gfgExif.setAttribute(
                            ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
                            zS.toString()
                        )
                        gfgExif.saveAttributes()
                    }

                    val sUri = output.savedUri!!
                    val photoPath = getPath(applicationContext, sUri)
                    val msg = "Photo capture succeeded: $photoPath"
                    val sb = Snackbar.make(
                        viewBinding.root,
                        msg,
                        Snackbar.LENGTH_LONG
                    )
//                    val sbView: View = sb.view
//                    val textView: TextView =
//                        sbView.findViewById(com.google.android.material.R.id.snackbar_text)
////                        sbView.findViewById(R.id.)
//                    textView.maxLines = 4
                    sb.show()
                }
            }
        )
    }

    //    https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
    fun getPath(context: Context?, uri: Uri): String? {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    "content://downloads/public_downloads".toUri(), java.lang.Long.valueOf(id)
                )
                return getDataColumn(applicationContext, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if (("image" == type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if (("video" == type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if (("audio" == type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs: Array<String?> = arrayOf(
                    split[1]
                )
                return getDataColumn(applicationContext, contentUri, selection, selectionArgs)
            } else {
                // LocalStorageProvider
                // The path is the id
                return DocumentsContract.getDocumentId(uri)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {

            // Return the remote address
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(
                applicationContext,
                uri,
                null,
                null
            )
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    private fun getDataColumn(
        context: Context, uri: Uri?, selection: String?,
        selectionArgs: Array<String?>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(
            column
        )
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    //    https://github.com/Faisal-FS/CameraX-In-Java/blob/real_time_grayscale/app/src/main/java/com/palfs/cameraxinjava/MainActivity.java
    private fun toTransparency(bmpOriginal: Bitmap?): Bitmap {
        val width: Int = bmpOriginal?.width ?: 100
        val height: Int = bmpOriginal?.height ?: 100

        val transparentRed =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString("textRedKey", "0")?.toInt()
        val transparentGreen =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString("textGreenKey", "0")?.toInt()
        val transparentBlue =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getString("textBlueKey", "0")?.toInt()

//        https://stackoverflow.com/questions/7237915/replace-black-color-in-bitmap-with-red
        val bmpTransparent = createBitmap(width, height)
//        https://stackoverflow.com/questions/20347591/changing-the-pixel-color-in-android
        val allPixels = IntArray(height * width)
        bmpOriginal?.getPixels(allPixels, 0, width, 0, 0, width, height)
        allPixels.forEachIndexed { index, i ->
            val red = Color.red(i)
            val green = Color.green(i)
            val blue = Color.blue(i)
            if (red == transparentRed && green == transparentGreen && blue == transparentBlue) {
                allPixels[index] = Color.argb(0x00, red, green, blue)
            }
        }
        bmpTransparent.setPixels(allPixels, 0, width, 0, 0, width, height)
        return bmpTransparent

        /*
                val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmpGrayscale)
                val paint = Paint()
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val f = ColorMatrixColorFilter(cm)
                paint.colorFilter = f
                c.drawColor(0, PorterDuff.Mode.CLEAR)
                paint.alpha = 0
                c.drawBitmap(bmpOriginal, 0f, 0f, paint)
                return bmpGrayscale
        */
    }
}
