/*
 * Copyright (c) 2022 JS HobbySoft.
 * All rights reserved.
 */

package org.jshobbysoft.cameraalign

//import android.util.Log

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.DatabaseUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
//import com.ianhanniballake.localstorage.LocalStorageProvider
import org.jshobbysoft.cameraalign.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageUri: Uri? = null
    private var imageCapture: ImageCapture? = null
    private var cameraSel = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // trivial change to test Git commits
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera(cameraSel)
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
            val backgroundUri = Uri.parse(backgroundUriString)
            viewBinding.basisImage.setImageURI(backgroundUri)
            val readOnlyMode = "r"
            contentResolver.openFileDescriptor(backgroundUri!!
                , readOnlyMode).use { pfd ->
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
        }

        //      set image transparency using value from SharedPrefs
        val transparencyValue = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            .getString("textTransparencyKey", "125")!!.toFloat()
        val transparencyValueFloat = transparencyValue/255
        viewBinding.basisImage.alpha = transparencyValueFloat

        viewBinding.buttonLoadPicture.setOnClickListener {
            val gallery =
                Intent(Intent.ACTION_OPEN_DOCUMENT, MediaStore.Images.Media.INTERNAL_CONTENT_URI)
            resultLauncher.launch(gallery)
        }

        // Set up the listeners for take photo button
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }

        // Set up image rotation button
        viewBinding.imageRotateButton.setOnClickListener {
            viewBinding.basisImage.rotation = viewBinding.basisImage.rotation+90
        }

        viewBinding.cameraFlipButton.setOnClickListener {
            if (cameraSel == CameraSelector.DEFAULT_FRONT_CAMERA) { cameraSel = CameraSelector.DEFAULT_BACK_CAMERA }
            else if (cameraSel == CameraSelector.DEFAULT_BACK_CAMERA) { cameraSel = CameraSelector.DEFAULT_FRONT_CAMERA }
            startCamera(cameraSel)
        }
//    https://stackoverflow.com/questions/72339792/how-to-implement-zoom-with-seekbar-on-camerax
//    https://github.com/Pinkal7600/camera-samples/blob/master/CameraXBasic/app/src/main/java/com/android/example/cameraxbasic/fragments/CameraFragment.kt
        viewBinding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                imageUri = data?.data
                val contentResolver = applicationContext.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                imageUri?.let { contentResolver.takePersistableUriPermission(it, takeFlags) }
                viewBinding.basisImage.setImageURI(imageUri)
                val sharedPref =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                with(sharedPref.edit()) {
                    putString("background_uri_key", imageUri.toString())
                    apply()
                }
//                  "rw" for read-and-write.
//                  "rwt" for truncating or overwriting existing file contents.
                val readOnlyMode = "r"
                contentResolver.openFileDescriptor(imageUri!!
                    , readOnlyMode).use { pfd ->
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

    private fun startCamera(cameraSelector:CameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
//                Log.e(TAG, "Use case binding failed", exc)
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
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(cameraSel)
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
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
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
                    resolver.openFileDescriptor(output.savedUri!!
                        , readOnlyMode).use { pfd ->
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
                    val msg = "Photo capture succeeded: ${photoPath}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(TAG, msg)
                }
            }
        )
    }

//    https://github.com/iPaulPro/aFileChooser/blob/master/aFileChooser/src/com/ipaulpro/afilechooser/utils/FileUtils.java
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.<br></br>
     * <br></br>
     * Callers should check whether the path is local before assuming it
     * represents a local file.
     *
     * @author paulburke
     */

    private val DEBUG = false
    fun getPath(context: Context?, uri: Uri): String? {
        if (DEBUG) Log.d(
            TAG + " File -",
            "Authority: " + uri.authority +
                    ", Fragment: " + uri.fragment +
                    ", Port: " + uri.port +
                    ", Query: " + uri.query +
                    ", Scheme: " + uri.scheme +
                    ", Host: " + uri.host +
                    ", Segments: " + uri.pathSegments.toString()
        )
//        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

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
                    Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id)
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

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     * @author paulburke
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     * @author paulburke
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     * @author paulburke
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

//    private fun isLocalStorageDocument(uri: Uri): Boolean {
//        return LocalStorageProvider.AUTHORITY.equals(uri.authority)
//    }

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
                if (DEBUG) DatabaseUtils.dumpCursor(cursor)
                val column_index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(column_index)
            }
        } finally {
            cursor?.close()
        }
        return null
    }


}
