package de.developerleipzig.smarttublex

import android.os.Bundle
import android.util.Log
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity

/**
 * Wrapper [SplashActivity]. Launch entry for the TV APK; behavior delegated to upstream.
 */
class SmartTublexSplashActivity : SplashActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(SmartTublexApplication.TAG, "SmartTublexSplashActivity.onCreate")
        super.onCreate(savedInstanceState)
    }
}
