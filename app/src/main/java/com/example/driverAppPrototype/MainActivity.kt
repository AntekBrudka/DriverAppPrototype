package com.example.driverAppPrototype

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.example.driverAppPrototype.databinding.ActivityMainBinding

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module

import org.pytorch.torchvision.TensorImageUtils

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private var mModule: Module? = null
    private var classes: MutableList<String> = ArrayList()
    private var feedbackType : Boolean = false // 0 meaning visual, 1 meaning text
    private var sourceType : Boolean = false // 0 meaning video, 1 meaning camera
    private var modelChoice : Int = 0 // 0 for signs, 1 for lanes
    private var isVideoPlaying : Boolean = false
    private var modelList : List<List<String>> = listOf(listOf("classesSigns.txt", "modelSigns.torchscript.ptl"), listOf("classesLanes.txt", "modelLanes.torchscript.ptl"))


    @Throws(IOException::class)
    fun assetFilePath(context: Context, assetName: String): String? {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { `is` ->
            FileOutputStream(file).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
            return file.absolutePath
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.requestFeature(Window.FEATURE_NO_TITLE)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        photoPickerLauncher =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    displayVideo(uri)
                }
            }

        val models = arrayOf("Signs", "Lanes", "Same as before")
        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle("What do you want to detect?")
        builder.setItems(models) { _, which ->
            modelChoice = which
            loadModelAndClasses()
        }
        builder.show()
        setupButtons()

        //setNumThreads(6)
    }

    private fun loadModelAndClasses(){
        if(modelChoice == 2){
            val prefs = getSharedPreferences("driverAppPrefs", MODE_PRIVATE)
            modelChoice = prefs.getInt("modelChoiceKey", 0) // 0 is the default value
        }

        val prefs = getSharedPreferences("driverAppPrefs", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("modelChoiceKey", modelChoice)
        editor.apply()

        mModule = LiteModuleLoader.load(this.assetFilePath(applicationContext, modelList[modelChoice][1]))
        val br = BufferedReader(InputStreamReader(assets.open(modelList[modelChoice][0])))
        val iterator = br.lineSequence().iterator()
        while(iterator.hasNext())
        {
            classes.add(iterator.next())
        }
        PostProcessor.assignClasses(classes.toTypedArray())
        br.close()
    }

    private fun getPredictions(image: Bitmap): ArrayList<Result>? {
        val resizedBitmap = Bitmap.createScaledBitmap(image, PostProcessor.mInputWidth, PostProcessor.mInputHeight, true)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            PostProcessor.NO_MEAN_RGB,
            PostProcessor.NO_STD_RGB)
        val outputTuple = mModule?.forward(IValue.from(inputTensor))?.toTuple()

        val outputTensor = outputTuple?.get(0)?.toTensor()
        val outputs = outputTensor?.dataAsFloatArray
        val imgSizeX = image.width.toFloat()
        val imgSizeY = image.height.toFloat()
        val results = outputs?.let {
            PostProcessor.outputsToNMSPredictions(
                it,
                imgSizeX,
                imgSizeY
            )
        }
        return results
    }
    @OptIn(DelicateCoroutinesApi::class)
    fun displayVideo(uri: Uri) {
        binding.btnSource.isEnabled = false
        binding.btnFeedback.isEnabled = false

        contentResolver.openFileDescriptor(uri, "r").use { fileDescriptor ->
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(fileDescriptor?.fileDescriptor)
            var num = 0
            GlobalScope.launch(Dispatchers.Main) {
                while (true) {
                    var detNum = 0
                    val startTime = System.currentTimeMillis()
                    val image = try {
                        retriever.getFrameAtIndex(num * 30)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                    if(!isVideoPlaying)
                    {
                        break
                    }
                    if (image != null) {
                        val imgAfterConv = image.copy(image.config, true)

                        val results = getPredictions(imgAfterConv)
                        if (results != null) {
                            detNum = results.size
                        }
                        if(feedbackType){
                            val resultMessage = StringBuilder()
                            if (results != null) {
                                for (result in results) {
                                    val stringToAppend =
                                        PostProcessor.mClasses[result.classIndex] + " " + result.score * 100 + "%\n"
                                    resultMessage.append(stringToAppend)
                                }
                            }
                            async{ binding.textView.text = resultMessage }.await()
                        }
                        else
                        {
                            val drawOnImg = Canvas(imgAfterConv)
                            if (results != null) {
                                PaintResults().draw(drawOnImg, results)
                            }
                            async{ binding.imgView.setImageBitmap(imgAfterConv) }.await()
                        }
                    } else {
                        break
                    }
                    num++

                    val perfTime = (System.currentTimeMillis() - startTime)/1000.0
                    val perfMessage = "FPS : " + "%.2f".format(1/perfTime) + "\nTime per frame : " + "%.2f".format(perfTime) + "s\nDetections : " + detNum
                    binding.perfView.text = perfMessage
                }
                isVideoPlaying = false
                binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(null, resources.getDrawable(R.drawable.playvec), null, null)
                binding.btnSource.isEnabled = true
                binding.btnFeedback.isEnabled = true
            }
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
        if(!isVideoPlaying){
            isVideoPlaying = true
            binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.cancelvec), null, null)
            photoPickerLauncher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
        } else {
            isVideoPlaying = false
            binding.btnStop.setCompoundDrawablesWithIntrinsicBounds(null, AppCompatResources.getDrawable(this, R.drawable.playvec), null, null)
        }
    }
}