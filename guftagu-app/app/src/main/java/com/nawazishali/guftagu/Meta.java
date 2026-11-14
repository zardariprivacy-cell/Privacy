package com.nawazishali.guftagu;

import org.json.JSONObject;

/**
 * Message actions carried inside the encrypted channel as special payloads:
 * reactions, edits and deletes. They are applied to the local copy and never
 * rendered as bubbles. Only the original author can edit or delete a message.
 */
public class Meta {

    /** Disappearing-message setting for a room: {t:"exp", h:<hours>} (room state, not a bubble). */
    public static boolean isExp(String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        return p != null && "exp".equals(p.optString("t"));
    }

    public static int expHours(String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        return p == null ? 0 : p.optInt("h", 0);
    }

    public static String exp(int hours) {
        try {
            JSONObject p = new JSONObject();
            p.put("t", "exp");
            p.put("h", hours);
            return p.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isMeta(String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        if (p == null) return false;
        String t = p.optString("t");
        return "react".equals(t) || "edit".equals(t) || "del".equals(t);
    }

    /** Applies a meta payload to the local database. Returns true if it was meta. */
    public static boolean apply(LocalDb db, String room, String fromUid, String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        if (p == null) return false;
        String t = p.optString("t");
        String mid = p.optString("mid", "");
        if (mid.isEmpty()) return isMeta(body);
        if ("react".equals(t)) {
            db.setReact(room, mid, fromUid, p.optString("e", ""));
        } else if ("edit".equals(t)) {
            LocalDb.Msg target = db.getMsg(room, mid);
            if (target != null && target.sender.equals(fromUid)) {
                db.applyEdit(room, mid, p.optString("x", ""));
            }
        } else if ("del".equals(t)) {
            LocalDb.Msg target = db.getMsg(room, mid);
            if (target != null && target.sender.equals(fromUid)) {
                db.applyDelete(room, mid);
            }
        } else {
            return false;
        }
        return true;
    }

    public static String react(String mid, String emoji) {
        try {
            JSONObject p = new JSONObject();
            p.put("t", "react");
            p.put("mid", mid);
            p.put("e", emoji);
            return p.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String edit(String mid, String newText) {
        try {
            JSONObject p = new JSONObject();
            p.put("t", "edit");
            p.put("mid", mid);
            p.put("x", newText);
            return p.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String del(String mid) {
        try {
            JSONObject p = new JSONObject();
            p.put("t", "del");
            p.put("mid", mid);
            return p.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isDeleted(String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        return p != null && "deleted".equals(p.optString("t"));
    }

    public static boolean isRichText(String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        return p != null && "text".equals(p.optString("t"));
    }

    /** The human-readable text of a message body (unwraps rich text payloads). */
    public static String displayText(String body) {
        JSONObject p = MediaHelper.parsePayload(body);
        if (p != null && "text".equals(p.optString("t"))) return p.optString("x", "");
        if (p == null) return body;
        switch (p.optString("t")) {
            case "voice": return "🎤 Voice message";
            case "photo": return "📷 Photo";
            case "file": return "📎 " + p.optString("name", "File");
            case "deleted": return "🚫 Message deleted";
            default: return body;
        }
    }

    /** A short one-line snippet of a message, used inside reply quotes. */
    public static String snippetOf(String body) {
        String t = displayText(body).replace("\n", " ").trim();
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }

    /** Builds the body to send when forwarding an existing message. */
    public static String forwardBody(String body) {
        try {
            JSONObject p = MediaHelper.parsePayload(body);
            if (p == null) {
                JSONObject np = new JSONObject();
                np.put("t", "text");
                np.put("x", body);
                np.put("fwd", true);
                return np.toString();
            }
            if ("text".equals(p.optString("t"))) {
                JSONObject np = new JSONObject();
                np.put("t", "text");
                np.put("x", p.optString("x", ""));
                np.put("fwd", true);
                return np.toString();
            }
            JSONObject copy = new JSONObject(p.toString());
            copy.put("fwd", true);
            return copy.toString();
        } catch (Exception e) {
            return body;
        }
    }
}
