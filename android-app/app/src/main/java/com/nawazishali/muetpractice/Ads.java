package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * AdMob ads for UniTest Prep.
 *
 * LIVE STATE (Nov 13, 2026): ALL AdMob IDs are the owner's real ones —
 * App ID in AndroidManifest.xml, BANNER_ID and INTERSTITIAL_ID below.
 * Ads earn money for the owner's AdMob account. Never click your own ads.
 *
 * v3.6 diagnostics: the banner is loaded only after the ads SDK reports
 * initialization complete (loading earlier can silently fail), and every
 * load result is recorded in bannerStatus / sdkStatus so the exact reason
 * an ad did not show is visible on the Home screen and in Settings > About.
 */
public class Ads {

    // Ad unit IDs — both are the owner's real AdMob units.
    private static final String BANNER_ID = "ca-app-pub-5229747293404900/5728194075"; // owner's real banner unit
    private static final String INTERSTITIAL_ID = "ca-app-pub-5229747293404900/5983199150"; // owner's real interstitial unit

    private static boolean initialized = false;
    private static boolean initReady = false;
    private static InterstitialAd interstitial = null;
    private static int resultsSeen = 0;
    private static AdView pendingBanner = null;

    /** Latest banner load state, shown on screen for diagnosis. */
    public static volatile String bannerStatus = "not requested yet";
    /** Latest ads-SDK initialization state, shown on screen for diagnosis. */
    public static volatile String sdkStatus = "not started";

    /** Starts the ads SDK once. Never throws: ads must never crash the app. */
    public static void init(Context ctx) {
        try {
            if (initialized) return;
            initialized = true;
            sdkStatus = "initializing...";
            MobileAds.initialize(ctx.getApplicationContext(), status -> {
                try {
                    AdapterStatus as = status.getAdapterStatusMap()
                            .get("com.google.android.gms.ads.MobileAds");
                    sdkStatus = (as == null)
                            ? "init done"
                            : "init " + as.getInitializationState() + " - " + as.getDescription();
                } catch (Throwable t) {
                    sdkStatus = "init done";
                }
                initReady = true;
                if (pendingBanner != null) {
                    AdView v = pendingBanner;
                    pendingBanner = null;
                    requestBanner(v);
                }
            });
            loadInterstitial(ctx);
        } catch (Throwable ignored) {
            // Ads are optional; the app must work without them.
        }
    }

    private static void requestBanner(AdView adView) {
        try {
            bannerStatus = "loading...";
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Throwable t) {
            bannerStatus = "request error: " + t;
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
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    bannerStatus = "LOADED OK";
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    bannerStatus = "FAILED code=" + error.getCode() + " | " + error.getMessage();
                }
            });
            container.addView(adView);
            if (initReady) {
                requestBanner(adView);
            } else {
                bannerStatus = "waiting for SDK init...";
                pendingBanner = adView;
            }
        } catch (Throwable t) {
            bannerStatus = "attach error: " + t;
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
