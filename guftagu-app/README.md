# Guftagu — Android app (v1.0)

End-to-end encrypted chat app (WhatsApp-style), built by Nawazish Ali.

- Accounts via Firebase Auth (REST, no SDK), data in Firebase Realtime Database (free Spark plan)
- 1-to-1 chat: ECDH P-256 + HKDF + AES-256-GCM with a symmetric hash-chain ratchet
  (per-message keys, forward secrecy for past messages, safety numbers)
- Group chat ("Sab ka Group"): fresh random key per message, wrapped per member
- Owner-only admin panel: users, bans, broadcast announcements — enforced by
  server rules (owner uid) plus an Owner Code lock in the app. The admin can
  never read private chats; that is the point of E2EE.
- Manual build (no Gradle): bash build-guftagu.sh
- Package: com.nawazishali.guftagu
