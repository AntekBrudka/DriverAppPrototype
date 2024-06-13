package com.example.driverAppPrototype

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.LifecycleOwner

import com.example.driverAppPrototype.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume


class MainActivity : AppCompatActivity() { // user choices and changing the main activity components
    private lateinit var binding: ActivityMainBinding
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var fileDescriptor : ParcelFileDescriptor? = null
    private val mediaRetriever = MediaMetadataRetriever()
    private var module: Module? = null
    private var feedbackType : Boolean = false // 0 meaning visual, 1 meaning text
    private var sourceType : Boolean = false // 0 meaning video, 1 meaning camera
    private var isVideoPlaying : Boolean = false
    private var modelList : List<List<String>> = listOf(listOf("classesSigns.txt", "modelSigns.torchscript.ptl"), listOf("classesLanes.txt", "modelLanes.torchscript.ptl"), listOf("classesPCA.txt", "modelPCA.torchscript.ptl"))
    private var modelChoice : Int = 0 // 0 for signs, 1 for lanes
    private var videoFrame : Int = 0
    private val skipFrames : Int = 1
    private val cameraHelper = CameraHelper(this)
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100 // You can choose any integer value
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up the main view
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.requestFeature(Window.FEATURE_NO_TITLE) // Set fullscreen

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup video picker
        photoPickerLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
                    mediaRetriever.setDataSource(fileDescriptor?.fileDescriptor)
                    streamFeedback(this)
                }
            }

        // Check camera permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // Request CAMERA permission if not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            cameraExecutor = Executors.newSingleThreadExecutor()
            startCameraSetup()
            cameraHelper.startCamera(this)
        }

        // Choose model
        val models = arrayOf("Signs", "Lanes", "People, cars and animals", "Same as before")
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle("What do you want to detect?")
        builder.setItems(models) { dialog, which ->
            modelChoice = which

            val prefs = getSharedPreferences("driverAppPrefs", MODE_PRIVATE)
            if(modelChoice == modelList.size){
                modelChoice = prefs.getInt("modelChoiceKey", 0) // 0 is the default value
            }
            val editor = prefs.edit()
            editor.putInt("modelChoiceKey", modelChoice)
            editor.apply()

            PrePostProcessor.mClasses =
                FileLoader.loadClasses(applicationContext, modelList[modelChoice][0]).toTypedArray()
            module = FileLoader.loadModel(applicationContext, modelList[modelChoice][1])
        }
        builder.show()

        setupButtons()
        //setNumThreads(6)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                // Check if the CAMERA permission has been granted
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraExecutor = Executors.newSingleThreadExecutor()
                    startCameraSetup()
                    cameraHelper.startCamera(this)

                } else {
                    // Permission denied, handle accordingly (e.g., show a message or disable camera functionality)
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startCameraSetup() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            // Set up image capture
            imageCapture = ImageCapture.Builder()
                .build()

            // Bind use cases to camera
            cameraHelper.bindCameraUseCases(cameraProvider, imageCapture)

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun getPredictions(image: Bitmap): ArrayList<Result>? {
        val resizedBitmap = Bitmap.createScaledBitmap(image, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            PrePostProcessor.noMeanRGB,
            PrePostProcessor.noStdRGB)
        val outputTuple = module?.forward(IValue.from(inputTensor))?.toTuple()

        val outputTensor = outputTuple?.get(0)?.toTensor()
        val outputs = outputTensor?.dataAsFloatArray
        val imgSizeX = image.width.toFloat()
        val imgSizeY = image.height.toFloat()
        val results = outputs?.let {
            PrePostProcessor.outputsToNMSPredictions(
                it,
                imgSizeX,
                imgSizeY
            )
        }
        return results
    }
    private fun yieldFrameFromVideo(): Bitmap? {
        val image = try {
            mediaRetriever.getFrameAtIndex(videoFrame)
        } catch (e: IllegalArgumentException) {
            null
        }
        videoFrame += 1*skipFrames
        return image
    }
    private suspend fun captureImage(cameraHelper: CameraHelper): Bitmap? {
        return suspendCancellableCoroutine { continuation ->
            cameraHelper.takePhotoAsBitmap { bitmap ->
                continuation.resume(bitmap)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun streamFeedback(lifecycleOwner: LifecycleOwner) {
        binding.btnSource.isEnabled = false
        binding.btnFeedback.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            while (true) {

                if(!isVideoPlaying)
                {
                    break
                }

                var detNum = 0
                val startTime = System.currentTimeMillis()
                var image: Bitmap? = null

                if (sourceType) {
                    image = withContext(Dispatchers.IO) {return@withContext captureImage(cameraHelper) }
                    val matrix = Matrix().apply { postRotate(90F) }
                    if (image != null) {
                        image = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
                    }
                } else {
                    image = yieldFrameFromVideo()
                }

                if (image != null) {
                    val imgAfterConv = image.copy(image.config, true)

                    val results = withContext(Dispatchers.IO) {return@withContext getPredictions(imgAfterConv) }
                    if (results != null) {
                        detNum = results.size
                    }
                    if(feedbackType){
                        val resultMessage = StringBuilder()
                        if (results != null) {
                            for (result in results) {
                                val stringToAppend =
                                    PrePostProcessor.mClasses[result.classIndex] + " " + result.score + "\n"
                                resultMessage.append(stringToAppend)
                            }
                        }
                        async{ binding.textView.text = resultMessage }.await()
                    }
                    else
                    {
                        val drawOnImg = Canvas(imgAfterConv)
                        if (results != null) {
                            PaintResults.draw(drawOnImg, results)
                        }
                        async{ binding.imgView.setImageBitmap(imgAfterConv) }.await()
                    }
                } else {
                    break
                }

                val perfTime = (System.currentTimeMillis() - startTime)/1000.0
                val perfMessage = "FPS : " + "%.2f".format(1/perfTime) + "\nTime per frame : " + "%.2f".format(perfTime) + "s\nDetections : " + detNum
                binding.perfView.text = perfMessage
            }

            if(!sourceType){
                fileDescriptor?.close()
            }

            isVideoPlaying = false
            binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(null, resources.getDrawable(R.drawable.playvec), null, null)
            binding.btnSource.isEnabled = true
            binding.btnFeedback.isEnabled = true
        }
    }

    private fun setupButtons(){
        binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.playvec), null, null)
        binding.btnFeedback.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.visualvec), null, null)
        binding.btnSource.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.videovec), null, null)
    }
    fun btnSourceClick(view: View) {
        if (!sourceType) {
            sourceType = true
            binding.btnSource.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.cameravec), null, null)
        } else {
            sourceType = false
            binding.btnSource.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.videovec), null, null)
        }
    }
    fun btnFeedbackClick(view: View) {
        if (!feedbackType) {
            feedbackType = true
            binding.imgView.setImageBitmap(resources.getDrawable(R.drawable.trafficjam).toBitmap())
            binding.btnFeedback.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.textvec), null, null)
        } else {
            feedbackType = false
            binding.btnFeedback.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.visualvec), null, null)
        }
    }
    fun btnStopClick(view: View) {
        if (!isVideoPlaying) {
            isVideoPlaying = true
            binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(
                null,
                AppCompatResources.getDrawable(this, R.drawable.cancelvec),
                null,
                null
            )
            if(sourceType){
                streamFeedback(this)
            } else {
                photoPickerLauncher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
            }
        } else {
            isVideoPlaying = false
            binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(
                null,
                AppCompatResources.getDrawable(this, R.drawable.playvec),
                null,
                null
            )
        }
    }
}