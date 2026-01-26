package com.idverify.reactnative

import java.util.concurrent.atomic.AtomicBoolean

class CameraSessionLock {
    private val locked = AtomicBoolean(false)

    fun acquire(): Boolean {
        return locked.compareAndSet(false, true)
    }

    fun release() {
        locked.set(false)
    }
    
    fun isLocked(): Boolean {
        return locked.get()
    }
}
