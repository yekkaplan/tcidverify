package com.idverify.reactnative

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp

/**
 * React Native ViewManager for Camera Preview
 * 
 * Manages the PreviewView that displays camera feed.
 * Camera binding is handled by IdVerifyModule.
 */
class IdVerifyCameraViewManager : SimpleViewManager<IdVerifyCameraView>() {

    companion object {
        private const val REACT_CLASS = "IdVerifyCameraView"
    }

    override fun getName(): String = REACT_CLASS

    override fun createViewInstance(reactContext: ThemedReactContext): IdVerifyCameraView {
        return IdVerifyCameraView(reactContext)
    }

    @ReactProp(name = "isBackSide")
    fun setIsBackSide(view: IdVerifyCameraView, isBackSide: Boolean) {
        view.setBackSide(isBackSide)
    }

    @ReactProp(name = "active")
    fun setActive(view: IdVerifyCameraView, active: Boolean) {
        // Active state is now primarily handled by mounting/unmounting (attach/detach)
        // But if needed, we could add logic here. 
        // For now, attaching is enough.
    }
}
