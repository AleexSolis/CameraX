package com.example.camerax

import android.R.attr.bitmap
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Preview.OnPreviewOutputUpdateListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(
        "android.permission.CAMERA",
        "android.permission.WRITE_EXTERNAL_STORAGE"
    )
    var textureView: TextureView? = null
    var rectangleView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar!!.hide()
        textureView = findViewById(R.id.viewFinder)
        rectangleView = findViewById(R.id.rectangleView)

        if (allPermissionGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        CameraX.unbindAll()
        val aspectRatio = Rational(textureView!!.width, textureView!!.height)
        val screen = Size(textureView!!.width, textureView!!.height)
        val pConfig = PreviewConfig.Builder().setTargetAspectRatio(aspectRatio).setTargetResolution(screen).build()
        val preview = Preview(pConfig)
        preview.onPreviewOutputUpdateListener =
            OnPreviewOutputUpdateListener { output ->
                val parent = textureView!!.parent as ViewGroup
                parent.removeView(textureView)
                parent.addView(textureView, 0)
                textureView!!.surfaceTexture = output.surfaceTexture
                updateTransform()
            }
        val imageCaptureConfig = ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setTargetRotation(windowManager.defaultDisplay.rotation).build()

        val imgCap = ImageCapture(imageCaptureConfig)

        findViewById<ImageButton>(R.id.imgCapture).setOnClickListener(View.OnClickListener {
            val file = File( this.getExternalFilesDir(null)!!.getAbsolutePath()+ "/CameraX_" + System.currentTimeMillis() + ".jpg")
            val cropFile = File( this.getExternalFilesDir(null)!!.getAbsolutePath()+ "/CameraXCROP_" + System.currentTimeMillis() + ".jpg")
            imgCap.takePicture(
                object : ImageCapture.OnImageCapturedListener() {
                    override fun onCaptureSuccess(image: ImageProxy?, rotationDegrees: Int) {
                        val bitmapCrop = cropBitmap(image!!.image, rectangleView!!)
                        try {

                            val fOut = FileOutputStream(cropFile)
                            bitmapCrop!!.compress(Bitmap.CompressFormat.PNG, 90, fOut)
                            fOut.flush()
                            fOut.close()
                        }
                        catch (e:Exception) {
                            e.printStackTrace();
                            Log.i(null, "Save file error!");
                        }
                        image.close();
                    }
                }
            )

            imgCap.takePicture(file, object : ImageCapture.OnImageSavedListener {
                override fun onImageSaved(file: File) {
                    val msg = "Pic captured at " + file.absolutePath
                    Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                }

                override fun onError(
                    useCaseError: ImageCapture.UseCaseError,
                    message: String,
                    cause: Throwable?
                ) {
                    val msg = "Pic capture failed : $message"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                    cause?.printStackTrace()
                }
            })
        })
        CameraX.bindToLifecycle(this as LifecycleOwner, preview, imgCap)
    }

    private fun updateTransform() {
        val mx = Matrix()
        val w = textureView!!.measuredWidth.toFloat()
        val h = textureView!!.measuredHeight.toFloat()
        val cX = w / 2f
        val cY = h / 2f
        val rotationDgr: Int
        val rotation = textureView!!.rotation.toInt()
        rotationDgr = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        mx.postRotate(rotationDgr.toFloat(), cX, cY)
        textureView!!.setTransform(mx)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun allPermissionGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    fun cropBitmap(image: Image?, rView:View): Bitmap? {

        //Convert image to Bitmap
        val buffer: ByteBuffer = image!!.getPlanes().get(0).getBuffer()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

//        val stretchX = bitmap.getWidth() / image.getWidth()
//        val stretchY = bitmap.getHeight() / image.getHeight()

        val outLocation = IntArray(2)
        rView.getLocationOnScreen(outLocation)

        Log.i("ImagenCrop", "Position X: " + outLocation[0] )
        Log.i("ImagenCrop", "Position Y: " + outLocation[1] )
        Log.i("ImagenCrop", "Width: " + rView!!.width )
        Log.i("ImagenCrop", "Height: " + rView!!.height )

        var croppedImage = Bitmap.createBitmap(
            bitmap,
            ((image.getHeight() / 3) ) as Int,
            ((image.getWidth() / 4) - 50) as Int,
            ((rView!!.height * 2) + 100) as Int,
            ((rView!!.width * 2) + 100) as Int
        )
        return croppedImage
    }
}
