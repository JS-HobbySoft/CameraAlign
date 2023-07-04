/*
 * Copyright (c) 2022 JS HobbySoft.
 * All rights reserved.
 */

package org.jshobbysoft.cameraalign

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
//import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.exifinterface.media.ExifInterface
import org.jshobbysoft.cameraalign.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

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

                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
//                    Log.d(TAG, msg)
                }
            }
        )
    }
}
