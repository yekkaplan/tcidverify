package com.idverify.reactnative

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * CameraController (Singleton)
 * V2 Architecture: Deadlock Safe, State Machine Driven, Samsung Crash Proof.
 */
class CameraController private constructor() {

    enum class CameraState {
        IDLE,
        SURFACE_READY,
        CAMERA_BINDING,
        CAMERA_ACTIVE,
        CAMERA_RELEASING
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val sessionLock = CameraSessionLock()
    private var state = CameraState.IDLE
    
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var activity: Activity? = null
    private var reactContext: ReactContext? = null
    private var isBackSide = true // Default

    // Samsung Workaround Helpers
    private val isSamsung = android.os.Build.MANUFACTURER.lowercase() == "samsung"

    companion object {
        const val TAG = "CameraControllerV2"
        @Volatile
        private var instance: CameraController? = null

        fun getInstance(): CameraController {
            return instance ?: synchronized(this) {
                instance ?: CameraController().also { instance = it }
            }
        }
    }

    fun attachView(view: PreviewView, activity: Activity, reactContext: ReactContext) {
        this.previewView = view
        this.activity = activity
        this.reactContext = reactContext
        
        // Ensure View properties for Samsung
        view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        view.scaleType = PreviewView.ScaleType.FILL_CENTER
        
        Log.d(TAG, "View Attached. State: $state")
        if (state == CameraState.IDLE) {
            state = CameraState.SURFACE_READY
        }
    }

    fun detachView() {
        Log.d(TAG, "View Detached. Releasing camera...")
        // If view is gone, we must stop the camera to prevent surface crashes
        // But we do it safely via stop() flow if needed, or just nullify reference
        if (state == CameraState.CAMERA_ACTIVE || state == CameraState.CAMERA_BINDING) {
             // Optional: Auto-stop or just release UI ref?
             // Architecture says: Surface destroy -> stopCamera() zorunlu
             forceStop() 
        }
        this.previewView = null
        this.activity = null
        this.reactContext = null
        state = CameraState.IDLE
    }
    
    fun setSide(isBack: Boolean) {
        this.isBackSide = isBack
    }

    fun start(promise: Promise?) {
        Log.d(TAG, "Start Request. State: $state, Locked: ${sessionLock.isLocked()}")
        
        if (!sessionLock.acquire()) {
            val msg = "Camera session locked/busy. State: $state"
            Log.w(TAG, msg)
            promise?.reject("CAMERA_BUSY", msg)
            return
        }

        if (previewView == null || activity == null) {
            sessionLock.release()
            Log.e(TAG, "Start Failed: View or Activity null")
            promise?.reject("VIEW_ERROR", "PreviewView not attached")
            return
        }

        // Watchdog to detect Deadlocks during binding
        val watchdog = Handler(Looper.getMainLooper())
        val watchdogRunnable = Runnable {
            if (state == CameraState.CAMERA_BINDING) {
                Log.e(TAG, "🚨 DEADLOCK DETECTED! Force resetting...")
                forceReset()
                promise?.reject("DEADLOCK", "Camera binding timed out")
            }
        }
        watchdog.postDelayed(watchdogRunnable, 6000)

        cameraExecutor.execute {
            try {
                Log.d(TAG, "Executor: Starting binding process...")
                state = CameraState.CAMERA_BINDING

                bindCameraSync()

                state = CameraState.CAMERA_ACTIVE
                Log.d(TAG, "Camera Active!")
                watchdog.removeCallbacks(watchdogRunnable) // Success!
                promise?.resolve(true)

            } catch (e: Exception) {
                Log.e(TAG, "Camera Start Failed", e)
                watchdog.removeCallbacks(watchdogRunnable)
                forceReset() // Clean up ANY partial state
                promise?.reject("CAMERA_ERROR", e)
            }
        }
    }

    fun stop(promise: Promise?) {
        Log.d(TAG, "Stop Request. State: $state")
        cameraExecutor.execute {
            try {
                state = CameraState.CAMERA_RELEASING
                
                provider?.unbindAll()
                
                // Samsung Workaround: Explicitly clear/shutdown executors if needed
                // But generally unbindAll is enough for standard stop.
                // ForceReset does the heavy lifting for crashes.
                
                state = CameraState.IDLE
                sessionLock.release()
                Log.d(TAG, "Camera Stopped and Lock Released")
                promise?.resolve(true)
            } catch (e: Exception) {
                Log.e(TAG, "Stop Failed", e)
                forceReset() // Fallback
                promise?.reject("STOP_ERROR", e)
            }
        }
    }

    private fun bindCameraSync() {
        // Ensure provider
        if (provider == null) {
            val future = ProcessCameraProvider.getInstance(activity!!)
            provider = future.get(5, TimeUnit.SECONDS) // Blocks thread, OK in Executor
        }

        val cameraProvider = provider ?: throw IllegalStateException("CameraProvider null")
        
        // Critical: Unbind everything first (On Main Thread to be safe)
        runOnUiThreadSync { 
             cameraProvider.unbindAll()
        }

        // Samsung Safe Config
        val targetResolution = if (isSamsung) Size(1280, 720) else Size(1920, 1080)
        
        // Rotation: Force 0 on Samsung
        val rotation = if (isSamsung) Surface.ROTATION_0 else (activity?.display?.rotation ?: Surface.ROTATION_0)

        Log.i(TAG, "Binding with: Samsung=$isSamsung, Res=$targetResolution, Rot=$rotation")

        // Preview
        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .setTargetResolution(targetResolution)
            .build()
        
        // Must run on UI thread to access surfaceProvider
        runOnUiThreadSync {
             preview.setSurfaceProvider(previewView!!.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val lifecycleOwner = activity as LifecycleOwner
        
        // Setup Image Analysis
        var imageAnalysis: ImageAnalysis? = null
        if (!isSamsung) {
             imageAnalysis = ImageAnalysis.Builder()
                 .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                 .setTargetResolution(targetResolution)
                 .setTargetRotation(rotation)
                 .build()
        }

        // Bind Logic (MUST BE ON MAIN THREAD)
        runOnUiThreadSync {
            if (isSamsung) {
                 Log.w(TAG, "Samsung Mode: Binding PREVIEW ONLY")
                 cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            } else {
                 cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis!!)
            }
        }
    }
    
    private fun runOnUiThreadSync(action: () -> Unit) {
        val future = java.util.concurrent.CompletableFuture<Unit>()
        activity?.runOnUiThread {
            try {
                action()
                future.complete(Unit)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        // Block until done
        try {
            future.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
             Log.e(TAG, "MainThread operation timed out or failed", e)
             throw e
        }
    }

    private fun forceStop() {
         cameraExecutor.execute {
             try {
                state = CameraState.CAMERA_RELEASING
                provider?.unbindAll()
             } catch(e: Exception) {
                 Log.e(TAG, "Force Stop Warning", e)
             } finally {
                state = CameraState.IDLE
                sessionLock.release()
             }
         }
    }

    private fun forceReset() {
        Log.e(TAG, "⚠️ FORCE RESET TRIGGERED")
        try {
            provider?.unbindAll()
        } catch (ignored: Exception) {}

        // Kill Executors (Brutal kill for Samsung Deadlocks)
        cameraExecutor.shutdownNow()
        analysisExecutor.shutdownNow()
        
        // Re-create executors
        cameraExecutor = Executors.newSingleThreadExecutor()
        analysisExecutor = Executors.newSingleThreadExecutor()

        state = CameraState.IDLE
        sessionLock.release()
        Log.e(TAG, "✅ Force Reset Complete. System Clean.")
    }
}
