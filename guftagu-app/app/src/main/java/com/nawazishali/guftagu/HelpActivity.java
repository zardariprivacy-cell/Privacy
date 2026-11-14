package com.nawazishali.guftagu;

public class HelpActivity extends PrefsActivity {

    @Override
    protected String title() { return "Help / About"; }

    @Override
    protected void build() {
        PrefRows.header(this, list, "GUFTAGU");
        PrefRows.note(this, list, "Version 2.7 — Developed by Nawazish Ali\nApni private chat app: accounts, end-to-end encrypted 1-to-1 aur group chat, voice messages, photos, files, 24-ghante Status, aur owner-only admin panel.");
        PrefRows.header(this, list, "ENCRYPTION KAISE KAAM KARTI HAI");
        PrefRows.note(this, list, "Har phone apni secret key khud banata hai — wo key phone se bahar kabhi nahi jati. Messages ECDH (P-256) + AES-256-GCM se lock hote hain, aur har message ki key alag banti hai (hash-chain ratchet), is liye puranay messages mehfooz rehte hain. Server par sirf band (encrypted) text jata hai — server wala, hatta ke app ka admin bhi, tumhari chat nahi parh sakta.");
        PrefRows.header(this, list, "HADDAIN (IMITIAZ KE SAATH)");
        PrefRows.note(this, list, "• App band ho to naye message ki notification nahi aati.\n• Ek waqt mein taqreeban 100 users online reh sakte hain (free server).\n• Photo/file ki hadd: photo khud chhoti ho kar jati hai, file 1 MB tak. Bari videos ke liye alag (paid) storage chahiye.\n• Phone kho jaye ya app delete ho jaye to purani chats wapas nahi a sakti — keys usi phone mein hoti hain.");
        PrefRows.header(this, list, "MASLA HO TO");
        PrefRows.note(this, list, "App ke owner (Nawazish Ali) se rabta karo — wahi admin hain.");
    }
}
