package com.nawazishali.guftagu;

public class NotificationsActivity extends PrefsActivity {

    @Override
    protected String title() { return "Notifications"; }

    @Override
    protected void build() {
        PrefRows.note(this, list, "⚠️ Sach ye hai: Guftagu mein band app par notification nahi aa sakti (WhatsApp wala background system is free tareeqay mein mumkin nahi). Ye settings us waqt ke liye hain jab koi chat khuli ho aur naya message aye.");
        PrefRows.header(this, list, "MESSAGES (CHAT KHULI HO TO)");
        PrefRows.switchRow(this, list, "Awaz",
                "Naya message aye to halki si beep",
                AppPrefs.soundOn(this), on -> AppPrefs.setSoundOn(this, on));
        PrefRows.switchRow(this, list, "Vibration",
                "Naya message aye to phone halka sa vibrate kare",
                AppPrefs.vibrateOn(this), on -> AppPrefs.setVibrateOn(this, on));
    }
}
