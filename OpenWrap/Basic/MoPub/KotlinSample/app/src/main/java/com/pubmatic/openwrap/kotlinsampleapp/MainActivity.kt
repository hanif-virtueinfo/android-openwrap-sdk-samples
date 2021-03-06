package com.pubmatic.openwrap.kotlinsampleapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mopub.common.MoPub
import com.mopub.common.SdkConfiguration
import com.mopub.common.SdkInitializationListener

import kotlinx.android.synthetic.main.activity_home.*


class MainActivity : AppCompatActivity()  {

    var recycler: RecyclerView? = null
    var list: ArrayList<AdType> ? = null

    companion object {

        private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        private fun hasPermissions(context: Context?, vararg permissions: String): Boolean {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null) {
                for (permission in permissions) {
                    if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                        return false
                    }
                }
            }
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        setSupportActionBar(toolbar)

        initMoPub()

        recycler = findViewById(R.id.recycler_view)
        recycler?.layoutManager = LinearLayoutManager(this)
        recycler?.setHasFixedSize(true)

        list = ArrayList()
        list?.addAll(AdType.values())

        val recyclerAdapter = RecyclerAdapter(list, RecyclerItemListener())
        recycler?.adapter = recyclerAdapter

        // Ask permission from user for location and write external storage
        if (!hasPermissions(this@MainActivity, *PERMISSIONS)) {
            val MULTIPLE_PERMISSIONS_REQUEST_CODE = 123
            ActivityCompat.requestPermissions(this@MainActivity, PERMISSIONS, MULTIPLE_PERMISSIONS_REQUEST_CODE)
        }

    }

    private fun initMoPub() {
        val sdkConfiguration = SdkConfiguration.Builder("1a4a0c6b94ad4217af017c932c3c898e")
                .build()

        MoPub.initializeSdk(this, sdkConfiguration, initSdkListener())
    }

    private fun initSdkListener(): SdkInitializationListener {
        return SdkInitializationListener {
            /* MoPub SDK initialized.
           Check if you should show the consent dialog here, and make your ad requests. */
        }
    }

    /**
     * Navigates respective Activity from list info
     */
    fun displayActivity(position: Int){
        if(null != list?.get(position)?.activity){
            val intent = Intent(this, list?.get(position)?.activity)
            startActivity(intent)
        }
    }


    inner class RecyclerItemListener : RecyclerAdapter.OnItemClickListener{
        override fun onItemClick(position: Int) {
            displayActivity(position)
        }

    }

    /**
     * Constant to represents AdType
     */
    enum class AdType constructor(val activity: Class<*>?, val displayName: String) {
        BANNER(BannerActivity::class.java, "Banner"),
        INTERSTITIAL(InterstitialActivity::class.java, "Interstitial"),
        VIDEO_INTERSTITIAL(VideoInterstitialActivity::class.java, "Video Interstitial"),
        IN_BANNER_VIDEO(InBannerVideoActivity::class.java, "In-Banner Video")
    }

}
