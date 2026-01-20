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
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            currentPreviewView = this
        }
    }
    
    /**
     * Start/stop camera based on active prop
     */
    @ReactProp(name = "active")
    fun setActive(view: PreviewView, active: Boolean) {
        val module = reactContext.getNativeModule(IDScannerModule::class.java)
            ?: return
        
        if (active) {
            // Start scanning with this preview view
            val scanner = module.getScanner()
            val callback = module.getScanCallback()
            scanner.startScanning(view, callback)
        } else {
            // Stop scanning
            module.getScanner().stopScanning()
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
        // Clean up when view is removed
        currentPreviewView = null
    }
    
    companion object {
        const val REACT_CLASS = "IDScannerView"
    }
}
