package com.example.driverappprototype

import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.driverappprototype.databinding.ActivityMainBinding
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // This will happen after choosing the video
        photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                display_video(uri)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun display_video(uri: Uri){
        contentResolver.openFileDescriptor(uri, "r").use{ fileDescriptor ->
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(fileDescriptor?.fileDescriptor)
            var num = 0

            // Get info about original video
            val fps = MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
            val duration = MediaMetadataRetriever.METADATA_KEY_DURATION

            // Start another coroutine for processing
            GlobalScope.launch(Dispatchers.Main){
                repeat((fps + 1) * (duration - 1) - 1){
                    val timestampBeforeFrame = System.currentTimeMillis()
                    val image = retriever.getFrameAtIndex(num)
                    if (image != null){
                        val imgAfterConv = image.copy(image.config, true)
                        detect(image, imgAfterConv)
                        binding.imgView.setImageBitmap(imgAfterConv)
                    }
                    num++
                    // delay to give time for processing and to display in an original frame rate
                    delay((1/fps*1000 - timestampBeforeFrame + System.currentTimeMillis()))
                }
            }

            // just for showing that there are two coroutines, DELETE LATER
            val toast = Toast.makeText(this, fps.toString() + "dur : " + duration.toString() + "timestamp : " +  System.currentTimeMillis(), Toast.LENGTH_LONG)
            toast.show()
        }
    }
    fun add_imgview(){
        // not yet used
        val newView: ImageView = ImageView(this)
        binding.mainLayout.addView(newView)
        newView.layoutParams.height = 200
        newView.layoutParams.width = 200
        newView.x = 300F
        newView.y = 500F
        newView.setBackgroundColor(Color.MAGENTA)
    }

    fun btnSource_click(view: View){
        val btnSource = binding.btnSource
        if (btnSource.text == resources.getString(R.string.video)) {
            this.add_imgview()
            photoPickerLauncher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
            btnSource.text = resources.getString(R.string.live)
        } else {
            btnSource.text = resources.getString(R.string.video)
        }
    }

    fun btnFeedback_click(view: View) {
        val btnFeedback = binding.btnFeedback
        if (btnFeedback.text == resources.getString(R.string.visual)) {
            btnFeedback.text = resources.getString(R.string.text)

        } else {
            btnFeedback.text = resources.getString(R.string.visual)
        }
    }

    external fun detect(bitmapIn: Bitmap, bitmapOut: Bitmap): String

    companion object {
        // Used to load the 'driverappprototype' library on application startup.
        init {
            System.loadLibrary("driverappprototype")
        }
    }
}