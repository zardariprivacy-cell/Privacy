package com.nawazishali.guftagu;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * End-to-end encryption primitives, using only crypto built into Java/Android:
 * ECDH on P-256 for key agreement, HKDF-SHA256 for key derivation,
 * HMAC-SHA256 for the ratchet chains, AES-256-GCM for messages.
 * The private key is generated on the phone and NEVER leaves it
 * (stored in the app's private folder, which Android sandboxes per app).
 */
public class Crypto {

    // ---------- encoding ----------
    public static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    public static byte[] unb64(String s) { return Base64.getDecoder().decode(s); }

    // ---------- identity keys (per phone) ----------
    private static File pubFile(Context ctx) { return new File(ctx.getFilesDir(), "ecpub.bin"); }
    private static File privFile(Context ctx) { return new File(ctx.getFilesDir(), "ecpriv.bin"); }

    private static byte[] readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int off = 0, n;
        while ((n = in.read(b, off, b.length - off)) > 0) off += n;
        in.close();
        return b;
    }

    private static void writeFile(File f, byte[] b) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(b);
        out.close();
    }

    public static synchronized void ensureKeys(Context ctx) throws Exception {
        if (pubFile(ctx).exists() && privFile(ctx).exists()) return;
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();
        writeFile(pubFile(ctx), kp.getPublic().getEncoded());
        writeFile(privFile(ctx), kp.getPrivate().getEncoded());
    }

    public static byte[] myPubBytes(Context ctx) throws Exception {
        ensureKeys(ctx);
        return readFile(pubFile(ctx));
    }

    public static String myPubB64(Context ctx) throws Exception {
        return b64(myPubBytes(ctx));
    }

    public static PrivateKey myPriv(Context ctx) throws Exception {
        ensureKeys(ctx);
        byte[] pkcs8 = readFile(privFile(ctx));
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    }

    // ---------- agreement + derivation ----------
    public static byte[] ecdh(PrivateKey priv, byte[] peerPubX509) throws Exception {
        PublicKey peer = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(peerPubX509));
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(priv);
        ka.doPhase(peer, true);
        return ka.generateSecret();
    }

    public static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    public static byte[] hkdf(byte[] ikm, byte[] salt, String info, int len) throws Exception {
        if (salt == null) salt = new byte[32];
        byte[] prk = hmac(salt, ikm);
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0;
        byte counter = 1;
        while (pos < len) {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(t);
            mac.update(info.getBytes(StandardCharsets.UTF_8));
            mac.update(counter);
            t = mac.doFinal();
            int take = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, take);
            pos += take;
            counter++;
        }
        return out;
    }

    public static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    // ---------- AES-GCM ----------
    public static byte[] aesGcmEncrypt(byte[] key, byte[] nonce, byte[] plain, byte[] aad) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(plain);
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] nonce, byte[] ct, byte[] aad) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(ct);
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    /** Static pair key used only to wrap group message keys (fresh random key per message). */
    public static byte[] groupWrapKey(PrivateKey priv, byte[] peerPubX509) throws Exception {
        byte[] z = ecdh(priv, peerPubX509);
        return hkdf(z, "guftagu-v1".getBytes(StandardCharsets.UTF_8), "group-wrap", 32);
    }

    /** Safety number both sides can compare (in person or on a call) to verify no middleman. */
    public static String safetyNumber(byte[] pubA, byte[] pubB) throws Exception {
        byte[] first, second;
        if (compareBytes(pubA, pubB) <= 0) { first = pubA; second = pubB; }
        else { first = pubB; second = pubA; }
        byte[] both = new byte[first.length + second.length];
        System.arraycopy(first, 0, both, 0, first.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        byte[] h = sha256(both);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            int v = ((h[i % 32] & 0xff) * 256 + (h[(i + 11) % 32] & 0xff)) % 100000;
            if (i > 0) sb.append(' ');
            sb.append(String.format("%05d", v));
        }
        return sb.toString();
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = (a[i] & 0xff) - (b[i] & 0xff);
            if (d != 0) return d;
        }
        return a.length - b.length;
    }
}
