package com.idverify.reactnative

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

/**
 * React Native ViewManager for Camera Preview
 * 
 * Manages the PreviewView that displays camera feed.
 * Camera binding is handled by IdVerifyModule.
 */
class IdVerifyCameraViewManager : SimpleViewManager<PreviewView>() {

    companion object {
        private const val REACT_CLASS = "IdVerifyCameraView"
        private const val TAG = "IdVerifyCameraView"
    }

    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): PreviewView {
        val previewView = PreviewView(reactContext).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            
            // CRITICAL: Set explicit layout params for React Native
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return previewView
    }

    @ReactProp(name = "isBackSide")
    fun setIsBackSide(view: PreviewView, isBackSide: Boolean) {
        // This will be handled when binding camera
    }

    @ReactProp(name = "active")
    fun setActive(view: PreviewView, active: Boolean) {
        android.util.Log.d(TAG, "setActive called: active=$active")
        
        if (!active) {
            android.util.Log.d(TAG, "Camera view deactivated")
            return
        }

        val reactContext = view.context as? ThemedReactContext ?: run {
            android.util.Log.e(TAG, "Failed to get ThemedReactContext")
            return
        }
        
        val activity = reactContext.currentActivity ?: run {
            android.util.Log.e(TAG, "No current activity")
            return
        }
        
        // Get module instance
        val appContext = reactContext.reactApplicationContext
        val module = try {
            appContext.getNativeModule(IdVerifyModule::class.java)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to get IdVerifyModule", e)
            null
        }

        if (module == null) {
            android.util.Log.e(TAG, "IdVerifyModule not found - is it registered?")
            return
        }

        android.util.Log.d(TAG, "Passing PreviewView to IdVerifyModule...")
        module.setPreviewView(view, activity)
    }
}
