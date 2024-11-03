package com.example.parcial3_tc100321

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture<Recorder>
    private lateinit var cameraExecutor: ExecutorService
    private var isRecording = false
    private var flashEnabled = false
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa los elementos de la UI
        viewFinder = findViewById(R.id.viewFinder)
        val btnCapturePhoto = findViewById<Button>(R.id.btnCapturePhoto)
        val btnRecordVideo = findViewById<Button>(R.id.btnRecordVideo)
        val btnSwitchCamera = findViewById<Button>(R.id.btnSwitchCamera)
        val btnFlash = findViewById<Button>(R.id.btnFlash)
        val zoomSlider = findViewById<SeekBar>(R.id.zoomSlider)

        // Verifica permisos
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Inicializa el ejecutor de cámara
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Configura el botón de captura de foto
        btnCapturePhoto.setOnClickListener { takePhoto() }

        // Configura el botón de grabación de video
        btnRecordVideo.setOnClickListener { recordVideo() }

        // Configura el botón para alternar entre cámaras
        btnSwitchCamera.setOnClickListener { switchCamera() }

        // Configura el botón de flash
        btnFlash.setOnClickListener { toggleFlash() }

        // Configura el control de zoom
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                cameraProvider.bindToLifecycle(this@MainActivity, lensFacing, imageCapture).cameraControl?.setLinearZoom(progress / 10f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // Configura y empieza la cámara
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, lensFacing, preview, imageCapture, videoCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar la cámara.", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Captura de foto
    private fun takePhoto() {
        val photoFile = File(getOutputDirectory(), "${System.currentTimeMillis()}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(applicationContext, "Foto guardada: ${photoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(applicationContext, "Error al capturar foto", Toast.LENGTH_SHORT).show()
                }
            })
    }

    // Grabación de video
    @SuppressLint("CheckResult")
    private fun recordVideo() {
        val videoFile = File(getOutputDirectory(), "${System.currentTimeMillis()}.mp4")

        if (!isRecording) {
            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            videoCapture.output.prepareRecording(this, outputOptions)
                .start(ContextCompat.getMainExecutor(this), object : Consumer<VideoRecordEvent> {
                    override fun accept(event: VideoRecordEvent) {
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                isRecording = true
                                Toast.makeText(applicationContext, "Grabando video...", Toast.LENGTH_SHORT).show()
                            }
                            is VideoRecordEvent.Finalize -> {
                                isRecording = false
                                Toast.makeText(applicationContext, "Video guardado: ${videoFile.absolutePath}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
        } else {
            //videoCapture.output.stopRecording()
            isRecording = false
        }
    }

    // Alternancia entre cámaras
    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera()
    }

    // Alternancia de flash
    private fun toggleFlash() {
        flashEnabled = !flashEnabled
        imageCapture.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        Toast.makeText(this, "Flash ${if (flashEnabled) "activado" else "desactivado"}", Toast.LENGTH_SHORT).show()
    }

    // Verifica permisos
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permisos no concedidos", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    // Define el directorio de salida para fotos y videos
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    }
}


