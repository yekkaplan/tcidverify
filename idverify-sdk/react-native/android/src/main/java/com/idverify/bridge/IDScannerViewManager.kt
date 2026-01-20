package com.idverify.bridge

import android.view.View
import androidx.camera.view.PreviewView
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

/**
 * React Native View Manager for ID Scanner Camera Preview
 * Manages the CameraX PreviewView lifecycle in React Native
 */
class IDScannerViewManager(
    private val reactContext: ReactApplicationContext
) : SimpleViewManager<PreviewView>() {
    
    private var currentPreviewView: PreviewView? = null
    
    override fun getName(): String = REACT_CLASS
    
    override fun createViewInstance(reactContext: ThemedReactContext): PreviewView {
        return PreviewView(reactContext).apply {
            // RN view hierarchy + SurfaceView can cause black preview on some devices.
            // COMPATIBLE forces TextureView when needed.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            currentPreviewView = this
            // Use generic tag slot; no resource id required.
            tag = false
        }
    }
    
    /**
     * Start/stop camera based on active prop
     */
    @ReactProp(name = "active")
    fun setActive(view: PreviewView, active: Boolean) {
        android.util.Log.d("IDScannerViewManager", "setActive called with: $active")
        android.util.Log.d("IDScannerViewManager", "View state: W=${view.width}, H=${view.height}, Attached=${view.isAttachedToWindow}, Visibility=${view.visibility}")
        
        val lastActive = (view.tag as? Boolean) ?: false
        if (lastActive == active) {
            android.util.Log.d("IDScannerViewManager", "setActive ignored (no state change): $active")
            return
        }
        view.tag = active

        val module = reactContext.getNativeModule(IDScannerModule::class.java)
            ?: run {
                android.util.Log.e("IDScannerViewManager", "IDScannerModule not found!")
                return
            }
        
        if (active) {
            android.util.Log.d("IDScannerViewManager", "Starting scanner from ViewManager")
            
            // Ensure view is ready before starting camera
            if (view.width == 0 || view.height == 0 || !view.isAttachedToWindow) {
                android.util.Log.d("IDScannerViewManager", "View not ready yet, waiting for layout...")
                view.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: android.view.View?,
                        left: Int, top: Int, right: Int, bottom: Int,
                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                    ) {
                        view.removeOnLayoutChangeListener(this)
                        android.util.Log.d("IDScannerViewManager", "View layout complete: W=${view.width}, H=${view.height}")
                        startScannerWhenReady(view, module)
                    }
                })
            } else {
                startScannerWhenReady(view, module)
            }
        } else {
            android.util.Log.d("IDScannerViewManager", "Stopping scanner from ViewManager")
            // Stop scanning
            module.getScanner().stopScanning()
        }
    }
    
    private fun startScannerWhenReady(view: PreviewView, module: IDScannerModule) {
        android.util.Log.d("IDScannerViewManager", "startScannerWhenReady: W=${view.width}, H=${view.height}, Attached=${view.isAttachedToWindow}")
        
        val scanner = module.getScanner()
        val callback = module.getScanCallback()
        val lifecycleOwner = reactContext.currentActivity as? androidx.lifecycle.LifecycleOwner
        
        if (lifecycleOwner == null) {
            android.util.Log.e("IDScannerViewManager", "LifecycleOwner is null! Cannot start scanner.")
            return
        }
        
        android.util.Log.d("IDScannerViewManager", "LifecycleOwner: $lifecycleOwner, State: ${lifecycleOwner.lifecycle.currentState}")
        
        // Post to ensure view is fully ready
        view.post {
            android.util.Log.d("IDScannerViewManager", "Post-execution: Starting scanner now")
            scanner.startScanning(view, callback, lifecycleOwner)
        }
    }
    
    /**
     * Allow setting style from React Native
     */
    @ReactProp(name = "scaleType")
    fun setScaleType(view: PreviewView, scaleType: String?) {
        view.scaleType = when (scaleType) {
            "fillStart" -> PreviewView.ScaleType.FILL_START
            "fillCenter" -> PreviewView.ScaleType.FILL_CENTER
            "fillEnd" -> PreviewView.ScaleType.FILL_END
            "fitStart" -> PreviewView.ScaleType.FIT_START
            "fitCenter" -> PreviewView.ScaleType.FIT_CENTER
            "fitEnd" -> PreviewView.ScaleType.FIT_END
            else -> PreviewView.ScaleType.FILL_CENTER
        }
    }
    
    override fun onDropViewInstance(view: PreviewView) {
        super.onDropViewInstance(view)
        // Ensure camera is released when RN removes the view
        try {
            val module = reactContext.getNativeModule(IDScannerModule::class.java)
            module?.getScanner()?.stopScanning()
        } catch (_: Exception) {
            // ignore
        }
        // Clean up when view is removed
        currentPreviewView = null
    }
    
    companion object {
        const val REACT_CLASS = "IDScannerView"
    }
}
