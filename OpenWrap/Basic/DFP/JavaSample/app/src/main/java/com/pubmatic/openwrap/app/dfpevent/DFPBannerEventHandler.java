package com.pubmatic.openwrap.app.dfpevent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.doubleclick.AppEventListener;
import com.google.android.gms.ads.doubleclick.PublisherAdRequest;
import com.google.android.gms.ads.doubleclick.PublisherAdView;
import com.pubmatic.sdk.common.POBAdSize;
import com.pubmatic.sdk.common.POBError;
import com.pubmatic.sdk.common.log.PMLog;
import com.pubmatic.sdk.common.ui.POBBannerRendering;
import com.pubmatic.sdk.openwrap.banner.POBBannerEvent;
import com.pubmatic.sdk.openwrap.banner.POBBannerEventListener;
import com.pubmatic.sdk.openwrap.core.POBBid;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class is compatible with OpenWrap SDK v1.5.0.
 * This class implements the communication between the OpenWrap SDK and the DFP SDK for a given ad
 * unit. It implements the PubMatic's OpenWrap interface. OpenWrap SDK notifies (using OpenWrap interface)
 * to make a request to DFP SDK and pass the targeting parameters. This class also creates the DFP's
 * PublisherAdView, initialize it and listen for the callback methods. And pass the DFP ad event to
 * OpenWrap SDK via POBBannerEventListener.
 */
public class DFPBannerEventHandler extends AdListener implements POBBannerEvent, AppEventListener {

    private static final String TAG = "DFPBannerEventHandler";
    /**
     * For every winning bid, a DFP SDK gives callback with below key via AppEventListener (from
     * DFP SDK). This key can be changed at DFP's line item.
     */
    private static final String PUBMATIC_WIN_KEY = "pubmaticdm";
    /**
     * Config listener to check if publisher want to config properties in DFP ad
     */
    private DFPConfigListener dfpConfigListener;
    /**
     * Flag to identify if PubMatic bid wins the current impression
     */
    private Boolean notifiedBidWin;
    private boolean isAppEventExpected;
    /**
     * Timer object to synchronize the onAppEvent() of DFP SDK with onAdLoaded()
     */
    private Timer timer;
    /**
     * DFP Banner ad view
     */
    private PublisherAdView dfpAdView;
    /**
     * Interface to pass the DFP ad event to OpenWrap SDK
     */
    private POBBannerEventListener eventListener;

    /**
     * Constructor
     *
     * @param context  Activity context
     * @param adUnitId DFP ad unit ID
     * @param adSizes  ad sizes for banner
     */
    public DFPBannerEventHandler(Context context, String adUnitId, AdSize... adSizes) {
        dfpAdView = new PublisherAdView(context.getApplicationContext());
        dfpAdView.setAdUnitId(adUnitId);
        dfpAdView.setAdSizes(adSizes);

        // DO NOT REMOVE/OVERRIDE BELOW LISTENERS
        dfpAdView.setAdListener(this);
        dfpAdView.setAppEventListener(this);
    }

    /**
     * Sets the Data listener object. Publisher should implement the DFPConfigListener and override
     * its method only when publisher needs to set the targeting parameters over DFP banner ad view.
     *
     * @param listener DFP data listener
     */
    public void setConfigListener(DFPConfigListener listener) {
        dfpConfigListener = listener;
    }

    private void resetDelay() {
        if (timer != null) {
            timer.cancel();
        }
        timer = null;
    }

    private void scheduleDelay() {

        resetDelay();

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                notifyPOBAboutAdReceived();
            }
        };
        timer = new Timer();
        timer.schedule(task, 400);

    }

    private void notifyPOBAboutAdReceived() {
        // If onAppEvent is not called within 400 milli-sec, consider that DFP wins
        if (notifiedBidWin == null) {
            // Notify POB SDK about DFP ad win state and set the state
            notifiedBidWin = false;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (eventListener != null) {
                        eventListener.onAdServerWin(dfpAdView);
                    }
                }
            });
        }
    }

    private void sendErrorToPOB(POBError error) {
        if (eventListener != null && error != null) {
            eventListener.onFailed(error);
        }
    }

    // ------- Overridden methods from POBBannerEvent -------
    @Override
    public void requestAd(POBBid bid) {
        // Reset the flag
        isAppEventExpected = false;

        PublisherAdRequest.Builder requestBuilder = new PublisherAdRequest.Builder();

        // Check if publisher want to set any targeting data
        if (dfpConfigListener != null) {
            dfpConfigListener.configure(dfpAdView, requestBuilder);
        }

        // Warn publisher if he overrides the DFP listeners
        if (dfpAdView.getAdListener() != this || dfpAdView.getAppEventListener() != this) {
            PMLog.warn(TAG, "Do not set DFP listeners. These are used by DFPBannerEventHandler internally.");
        }

        if (null != bid) {

            // Logging details of bid objects for debug purpose.
            PMLog.debug(TAG, bid.toString());

            Map<String, String> targeting = bid.getTargetingInfo();
            if (targeting != null && !targeting.isEmpty()) {
                // using for-each loop for iteration over Map.entrySet()
                for (Map.Entry<String, String> entry : targeting.entrySet()) {
                    requestBuilder.addCustomTargeting(entry.getKey(), entry.getValue());
                    PMLog.debug(TAG, "Targeting param [" + entry.getKey() + "] = " + entry.getValue());
                }
            }

            // Save this flag for future reference. It will be referred to wait for onAppEvent, only
            // if POB delivers non-zero bid to DFP SDK.
            double price = bid.getPrice();
            if (price > 0.0d) {
                isAppEventExpected = true;
            }
        }

        final PublisherAdRequest adRequest = requestBuilder.build();

        // Publisher/App developer can add extra targeting parameters to dfpAdView here.
        notifiedBidWin = null;

        // Load DFP ad request
        dfpAdView.loadAd(adRequest);
    }

    @Override
    public void setEventListener(POBBannerEventListener listener) {
        eventListener = listener;
    }

    @Override
    public POBBannerRendering getRenderer(String partnerName) {
        return null;
    }

    @Override
    public POBAdSize getAdSize() {
        if (dfpAdView.getAdSize() != null) {
            return new POBAdSize(dfpAdView.getAdSize().getWidth(), dfpAdView.getAdSize().getHeight());
        } else {
            return null;
        }
    }

    @Override
    public POBAdSize[] requestedAdSizes() {
        POBAdSize[] adSizes = null;

        if (dfpAdView != null) {
            AdSize[] dfpAdSizes = dfpAdView.getAdSizes();
            if (dfpAdSizes != null && dfpAdSizes.length > 0) {
                adSizes = new POBAdSize[dfpAdSizes.length];
                for (int index = 0; index < dfpAdSizes.length; index++) {
                    adSizes[index] = new POBAdSize(dfpAdSizes[index].getWidth(), dfpAdSizes[index].getHeight());
                }
            }
        }
        return adSizes;
    }

    //--- Overridden Methods from DFP App Event listener ------
    @Override
    public void onAppEvent(String key, String s1) {
        PMLog.info(TAG, "onAppEvent()");
        if (TextUtils.equals(key, PUBMATIC_WIN_KEY)) {
            // If onAppEvent is called before onAdLoaded(), it means POB bid wins
            if (notifiedBidWin == null) {
                notifiedBidWin = true;
                eventListener.onOpenWrapPartnerWin();
            } else if (!notifiedBidWin) {
                // In this case onAppEvent is called in wrong order and within 400 milli-sec
                // Hence, notify POB SDK about DFP ad win state
                sendErrorToPOB(new POBError(POBError.OPENWRAP_SIGNALING_ERROR,
                        "DFP ad server mismatched bid win signal"));
            }
        }
    }

    @Override
    public void destroy() {
        resetDelay();
        if (null != dfpAdView) {
            dfpAdView.destroy();
        }
        dfpAdView = null;
        eventListener = null;
    }

    //--- Override Methods from DFP Ad view's AdListener ------
    @Override
    public void onAdFailedToLoad(int errCode) {
        PMLog.info(TAG, "onAdFailedToLoad()");

        if (eventListener != null) {
            switch (errCode) {
                case PublisherAdRequest.ERROR_CODE_INVALID_REQUEST:
                    eventListener.onFailed(new POBError(POBError.INVALID_REQUEST, "DFP SDK gives invalid request error"));
                    break;
                case PublisherAdRequest.ERROR_CODE_NETWORK_ERROR:
                    eventListener.onFailed(new POBError(POBError.NETWORK_ERROR, "DFP SDK gives network error"));
                    break;
                case PublisherAdRequest.ERROR_CODE_NO_FILL:
                    eventListener.onFailed(new POBError(POBError.NO_ADS_AVAILABLE, "DFP SDK gives no fill error"));
                    break;
                default:
                    eventListener.onFailed(new POBError(POBError.INTERNAL_ERROR, "DFP SDK failed with error code:"+errCode));
                    break;
            }
        }else {
            PMLog.error(TAG, "Can not call failure callback, POBBannerEventListener reference null. DFP error:"+errCode);
        }
    }

    @Override
    public void onAdOpened() {
        if (eventListener != null) {
            eventListener.onAdOpened();
        }
    }

    @Override
    public void onAdClosed() {
        if (eventListener != null) {
            eventListener.onAdClosed();
        }
    }

    @Override
    public void onAdLoaded() {
        PMLog.info(TAG, "onAdServerWin()");
        if (eventListener != null) {

            // Wait only if onAppEvent() is not already called.
            if (notifiedBidWin == null) {

                // Check if POB bid delivers non-zero bids to DFP, then only wait
                if (isAppEventExpected) {
                    // Wait for 400 milli-sec to get onAppEvent before conveying to POB SDK
                    scheduleDelay();
                } else {
                    notifyPOBAboutAdReceived();
                }
            }
        }
    }

    @Override
    public void onAdLeftApplication() {
        super.onAdLeftApplication();
        if (eventListener != null) {
            eventListener.onAdLeftApplication();
        }
    }

    /**
     * Interface to get the DFP view and it's request builder, to configure the properties.
     */
    public interface DFPConfigListener {
        /**
         * This method is called before event handler makes an ad request call to DFP SDK. It passes
         * DFP ad view & request builder which will be used to make ad request. Publisher can
         * configure the ad request properties on the provided objects.
         *
         * @param adView         DFP Banner ad view
         * @param requestBuilder DFP Banner ad request builder
         */
        void configure(PublisherAdView adView,
                       PublisherAdRequest.Builder requestBuilder);
    }
}
