package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * AdMob ads for UniTest Prep.
 *
 * CURRENT STATE: Google's official TEST ad units are used below, so the app
 * shows sample "Test Ad" banners. These earn NO money.
 *
 * TO GO LIVE (owner must do this):
 * 1. Create a free AdMob account at admob.com and add this app.
 * 2. AdMob gives an App ID  -> put it in AndroidManifest.xml
 *    (meta-data com.google.android.gms.ads.APPLICATION_ID).
 * 3. Create a Banner ad unit and an Interstitial ad unit in AdMob and
 *    replace BANNER_ID and INTERSTITIAL_ID below with the real IDs.
 * 4. Rebuild and publish on Google Play.
 */
public class Ads {

    // Google TEST ad unit IDs (replace with real AdMob IDs to earn).
    private static final String BANNER_ID = "ca-app-pub-3940256099942544/6300978111";
    private static final String INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712";

    private static boolean initialized = false;
    private static InterstitialAd interstitial = null;
    private static int resultsSeen = 0;

    /** Starts the ads SDK once. Never throws: ads must never crash the app. */
    public static void init(Context ctx) {
        try {
            if (initialized) return;
            initialized = true;
            MobileAds.initialize(ctx.getApplicationContext(), status -> { });
            loadInterstitial(ctx);
        } catch (Throwable ignored) {
            // Ads are optional; the app must work without them.
        }
    }

    /** Adds a banner ad into the given container view. Never throws. */
    public static void attachBanner(Activity activity, ViewGroup container) {
        try {
            if (container == null) return;
            init(activity);
            AdView adView = new AdView(activity);
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId(BANNER_ID);
            container.addView(adView);
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Throwable ignored) {
        }
    }

    private static void loadInterstitial(Context ctx) {
        try {
            InterstitialAd.load(ctx.getApplicationContext(), INTERSTITIAL_ID,
                    new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(InterstitialAd ad) {
                            interstitial = ad;
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called when a test result is shown. Shows a full-screen ad at most on
     * every second result, so students are not disturbed too often.
     * Never throws.
     */
    public static void onResultShown(Activity activity) {
        try {
            init(activity);
            resultsSeen++;
            if (interstitial != null && resultsSeen % 2 == 0) {
                interstitial.show(activity);
                interstitial = null;
                loadInterstitial(activity);
            }
        } catch (Throwable ignored) {
        }
    }
}
