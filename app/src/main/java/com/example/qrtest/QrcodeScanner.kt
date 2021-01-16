package com.example.qrtest

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class QrcodeScanner(
        private val onQrCapture: Barcode.() -> Unit,
        private val onFailure: Throwable.() -> Unit,
        private val lifecycleOwner: LifecycleOwner,
        private val context: Context,
        private val previewView: PreviewView
) {

    private var cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null

    fun startCamera() {
        val cameraProviderFuture =
                ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
                {
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        startPreview()
                        startAnalysis()
                    }.onFailure {
                        onFailure(it)
                    }
                },
                ContextCompat.getMainExecutor(context)
        )
    }

    private fun startPreview() {
        if (previewUseCase != null) {
            cameraProvider?.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
        previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)

        runCatching {
            cameraProvider?.bindToLifecycle(lifecycleOwner,
                    cameraSelector,
                    previewUseCase
            )
        }.onFailure {
            onFailure(it)
        }
    }

    private fun startAnalysis() {
        val options = BarcodeScannerOptions.Builder()
             .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
             .build()
        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider?.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase?.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
            processImageProxy(barcodeScanner, imageProxy)
        })

        runCatching {
            cameraProvider?.bindToLifecycle(lifecycleOwner,
                    cameraSelector,
                    analysisUseCase
            )
        }.onFailure {
            onFailure(it)
        }

    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun processImageProxy(
            barcodeScanner: BarcodeScanner,
            imageProxy: ImageProxy
    ) {
        runCatching {
            val img = imageProxy.image
            if (img != null) {
                val inputImage =
                        InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)

                barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            barcodes.forEach {
                                onQrCapture(it)
                            }
                        }
                        .addOnFailureListener {
                            onFailure(it)
                        }.addOnCompleteListener {
                            imageProxy.close()
                        }
            } else {
                throw Exception("Falha ao processar a imagem")
            }
        }.onFailure {
            onFailure(it)
        }

    }

}