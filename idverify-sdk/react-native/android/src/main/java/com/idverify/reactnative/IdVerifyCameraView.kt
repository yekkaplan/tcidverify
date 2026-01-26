package com.idverify.reactnative

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ThemedReactContext
import com.idverify.sdk.autocapture.AutoCaptureAnalyzer
import java.util.concurrent.Executors

/**
 * Custom Camera View that fully manages its own CameraX lifecycle.
 * Binds camera ONLY when attached to window (Surface guaranteed).
 */
class IdVerifyCameraView(context: Context) : FrameLayout(context) {

    companion object {
        private const val TAG = "IdVerifyCameraView"
    }
    private val previewView: PreviewView = PreviewView(context)
    private val TAG = "IdVerifyCameraView"
    private var isBackSide = true // Default

    init {
        // Setup UI Layout
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        
        // Important: Ensure PreviewView is set up securely
        previewView.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        
        addView(previewView)

        // Lifecycle Listeners
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Log.i(TAG, "✅ onViewAttachedToWindow: Registering with CameraController")
                registerWithController()
            }

            override fun onViewDetachedFromWindow(v: View) {
                Log.i(TAG, "🛑 onViewDetachedFromWindow: Detaching from CameraController")
                CameraController.getInstance().detachView()
            }
        })
        
        // Keep Layout Hack for React Native 0x0 issue
        setupChoreographerHack()
    }

    private fun registerWithController() {
        val reactContext = context as? ReactContext
        val activity = reactContext?.currentActivity
        
        if (activity != null && reactContext != null) {
            // Update Side preference
            CameraController.getInstance().setSide(isBackSide)
            
            // Attach View
            CameraController.getInstance().attachView(previewView, activity, reactContext)
            
            // Auto-start? The V2 architecture suggests explicit start/stop from Module.
            // But if the view just mounted, we might want to ensure it's ready.
            // The user's architecture says: "Surface destroy -> stopCamera() zorunlu"
            // So we rely on Module commands strictly.
        } else {
            Log.e(TAG, "Failed to register: Activity or Context null")
        }
    }

    fun setBackSide(isBack: Boolean) {
        this.isBackSide = isBack
        CameraController.getInstance().setSide(isBack)
    }

    private fun setupChoreographerHack() {
        val choreographer = android.view.Choreographer.getInstance()
        val callback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (width == 0 || height == 0) {
                    measure(
                        View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.heightPixels, View.MeasureSpec.EXACTLY)
                    )
                    layout(left, top, right, bottom)
                    choreographer.postFrameCallback(this) 
                }
            }
        }
        choreographer.postFrameCallback(callback)
    }
    // ==================== Event Emission ====================

    private fun emitStateChange(context: ReactContext, state: AutoCaptureAnalyzer.CaptureState, message: String) {
        val params = Arguments.createMap().apply {
            putString("state", state.name)
            putString("message", message)
        }
        sendEvent(context, "onStateChange", params)
    }

    private fun emitQualityUpdate(context: ReactContext, metrics: AutoCaptureAnalyzer.QualityMetrics) {
        val params = Arguments.createMap().apply {
            putInt("cardConfidence", metrics.cardConfidence)
            putDouble("blurScore", metrics.blurScore.toDouble())
            putDouble("stability", metrics.stability.toDouble())
            putInt("glareScore", metrics.glareScore)
            putString("state", metrics.state.name)
            putString("message", metrics.message)
        }
        sendEvent(context, "onQualityUpdate", params)
    }

    private fun emitCaptured(context: ReactContext, result: AutoCaptureAnalyzer.CaptureResult, isBackSide: Boolean) {
        val params = Arguments.createMap().apply {
            putBoolean("isBackSide", isBackSide)
            putMap("extractedData", convertMapToWritableMap(result.extractedData))
            putInt("mrzScore", result.mrzScore)
            putBoolean("isValid", result.isValid)
        }
        sendEvent(context, "onCaptured", params)
    }

    private fun sendEvent(context: ReactContext, eventName: String, params: WritableMap) {
        context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    private fun convertMapToWritableMap(map: Map<String, String>): WritableMap {
        val writableMap = Arguments.createMap()
        map.forEach { (key, value) ->
            writableMap.putString(key, value)
        }
        return writableMap
    }
}
