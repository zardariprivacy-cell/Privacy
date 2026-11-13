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
 * LIVE STATE (Nov 13, 2026): ALL AdMob IDs are the owner's real ones —
 * App ID in AndroidManifest.xml, BANNER_ID and INTERSTITIAL_ID below.
 * Ads earn money for the owner's AdMob account. Never click your own ads.
 */
public class Ads {

    // Ad unit IDs — both are the owner's real AdMob units.
    private static final String BANNER_ID = "ca-app-pub-5229747293404900/5728194075"; // owner's real banner unit
    private static final String INTERSTITIAL_ID = "ca-app-pub-5229747293404900/5983199150"; // owner's real interstitial unit

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
