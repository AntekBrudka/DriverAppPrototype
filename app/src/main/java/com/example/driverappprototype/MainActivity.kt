package com.example.driverappprototype

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.example.driverappprototype.databinding.ActivityMainBinding
import android.media.MediaMetadataRetriever
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var btnSource: Button
    private lateinit var btnFeedback: Button
    private lateinit var imgv: ImageView
    private lateinit var photoPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        btnSource = findViewById(R.id.btn_source)
        btnFeedback = findViewById(R.id.btn_feedback)
        imgv = findViewById(R.id.imgView)

        photoPickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                contentResolver.openFileDescriptor(uri, "r").use{ fileDescriptor ->
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(fileDescriptor?.fileDescriptor)
                    val image = retriever.getFrameAtIndex(1)
                    if (image != null){
                        val imgAfterConv = image.copy(image.config, true)
                        detect(image, imgAfterConv)
                        imgv.setImageBitmap(imgAfterConv)}
                }
            }
        }


        btnSource.setOnClickListener {
            if (btnSource.text == resources.getString(R.string.video)) {
                btnSource.text = resources.getString(R.string.live)

                photoPickerLauncher.launch(PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.VideoOnly))
            } else {
                btnSource.text = resources.getString(R.string.video)
            }
        }

        btnFeedback.setOnClickListener {
            if (btnFeedback.text == resources.getString(R.string.visual)) {
                btnFeedback.text = resources.getString(R.string.text)

            } else {
                btnFeedback.text = resources.getString(R.string.visual)
            }
        }

        // Example of a call to a native method
        binding.sampleText.text = stringFromJNI()
    }

    /**
     * A native method that is implemented by the 'driverappprototype' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
    external fun detect(bitmapIn: Bitmap, bitmapOut: Bitmap)

    companion object {
        // Used to load the 'driverappprototype' library on application startup.
        init {
            System.loadLibrary("driverappprototype")
        }
    }
}