package com.chap.zrec.service

import android.content.Intent
import android.util.Log

object MediaProjectionCache {
    var resultCode: Int = 0
    var data: Intent? = null
    
    fun clear() {
        Log.d("ZRecorder", "MediaProjectionCache cleared")
        resultCode = 0
        data = null
    }
}
