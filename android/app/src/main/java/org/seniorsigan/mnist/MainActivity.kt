package org.seniorsigan.mnist

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.otaliastudios.cameraview.*
import org.seniorsigan.mnist.classifier.Classifier
import org.seniorsigan.mnist.classifier.TensorFlowClassifier
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private val PIXEL_WIDTH = 28
    private val width = PIXEL_WIDTH
    private val height = PIXEL_WIDTH
    private val TAG = "MNIST"
    private var classifier: Classifier? = null
    private lateinit var cameraView: CameraView
    private lateinit var preview: ImageView
    private lateinit var previewOrigin: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val text: TextView = findViewById(R.id.prediction)
        previewOrigin = findViewById(R.id.preview_origin)
        preview = findViewById(R.id.preview)
        cameraView = findViewById(R.id.camera)
        cameraView.mapGesture(Gesture.PINCH, GestureAction.ZOOM)
        cameraView.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER)
        cameraView.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(jpeg: ByteArray?) {
                Log.i(TAG, "Photo captured of size: ${jpeg?.size}")
                CameraUtils.decodeBitmap(jpeg, { bitmap ->
                    getPixels(bitmap, { pixels ->
                        val prediction = classifier?.recognize(pixels)
                        runOnUiThread {
                            text.text = "${prediction?.label} ${prediction?.conf}"
                        }
                        Log.i(TAG, "Predicted class $prediction")
                    })
                })
            }
        })

        loadModel()

        val btn: FloatingActionButton = findViewById(R.id.take_picture)
        btn.setOnClickListener {
            cameraView.capturePicture()
        }
    }

    private fun getPixels(bitmap: Bitmap, callback: (FloatArray) -> Unit) {
        Log.i(TAG, "Photo shape: ${bitmap.height}x${bitmap.width}")
        ImageConverter.prepare(bitmap, width, applicationContext, { bitmapScaled ->
            val pixels = IntArray(width * height)
            bitmapScaled.getPixels(pixels, 0, width, 0, 0, width, height)

            runOnUiThread {
                previewOrigin.setImageBitmap(bitmapScaled)
            }

            val p = ImageConverter.normalize(pixels)

            Log.d(TAG, p.joinToString(","))

            Log.d(TAG, "Update preview")

            val tmpBitmap = Bitmap.createBitmap(bitmapScaled)
            tmpBitmap.setPixels(p.map {
                val c = (it * 255).toInt()
                Color.argb(0, c, c, c)
            }.toIntArray(), 0, width, 0, 0, width, height)

            runOnUiThread {
                preview.setImageBitmap(tmpBitmap)
            }

            callback(p)
        })
    }

    override fun onResume() {
        super.onResume()
        cameraView.start()
    }

    override fun onPause() {
        super.onPause()
        cameraView.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView.destroy()
    }

    private fun loadModel() {
        thread {
            classifier = TensorFlowClassifier(
                    assets, "Keras", "opt_mnist_convnet.pb",
                    PIXEL_WIDTH.toLong(), "conv2d_1_input", "dense_2/Softmax",
                    false)
        }
    }
}
