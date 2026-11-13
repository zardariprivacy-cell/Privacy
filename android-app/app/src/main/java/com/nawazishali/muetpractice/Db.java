package com.nawazishali.muetpractice;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class Db extends SQLiteOpenHelper {

    public static final String[] SUBJECTS = {"Mathematics", "Physics", "Chemistry", "English"};

    public Db(Context context) {
        super(context, "muet.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "username TEXT NOT NULL UNIQUE, " +
                "passhash TEXT NOT NULL, " +
                "photo TEXT, " +
                "admin INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE questions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "subject TEXT NOT NULL, " +
                "question TEXT NOT NULL, " +
                "opt0 TEXT NOT NULL, opt1 TEXT NOT NULL, opt2 TEXT NOT NULL, opt3 TEXT NOT NULL, " +
                "correct INTEGER NOT NULL, " +
                "explanation TEXT NOT NULL, " +
                "chapter TEXT NOT NULL DEFAULT 'General', " +
                "topic TEXT NOT NULL DEFAULT 'Mixed Concepts')");
        db.execSQL("CREATE TABLE results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "mode TEXT NOT NULL, " +
                "subject TEXT NOT NULL, " +
                "score INTEGER NOT NULL, " +
                "total INTEGER NOT NULL, " +
                "date INTEGER NOT NULL)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v2.0 -> v2.1: add the MUET entry-test-style question batch.
            // Users, photos and past results are NOT touched.
            seedBatch2(db);
        }
        if (oldVersion < 3) {
            // v2.1 -> v3.0: chapter/topic columns + class XII topic-wise bank.
            db.execSQL("ALTER TABLE questions ADD COLUMN chapter TEXT NOT NULL DEFAULT 'General'");
            db.execSQL("ALTER TABLE questions ADD COLUMN topic TEXT NOT NULL DEFAULT 'Mixed Concepts'");
            seedBatch3(db);
        }
    }

    public static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return password;
        }
    }

    // ---------- Users ----------

    public int userCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM users", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    /** Returns the new user id, or -1 if the username is taken. First user becomes Admin. */
    public long createUser(String name, String username, String password) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("username", username);
        v.put("passhash", hash(password));
        v.put("admin", userCount() == 0 ? 1 : 0);
        return db.insertWithOnConflict("users", null, v, SQLiteDatabase.CONFLICT_NONE);
    }

    public User login(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("users", null, "username=? AND passhash=?",
                new String[]{username, hash(password)}, null, null, null);
        User u = null;
        if (c.moveToFirst()) u = readUser(c);
        c.close();
        return u;
    }

    public User getUser(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("users", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        User u = null;
        if (c.moveToFirst()) u = readUser(c);
        c.close();
        return u;
    }

    public List<User> allUsers() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("users", null, null, null, null, null, "name ASC");
        List<User> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readUser(c));
        c.close();
        return out;
    }

    public void updatePhoto(long userId, String path) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("photo", path);
        db.update("users", v, "id=?", new String[]{String.valueOf(userId)});
    }

    private User readUser(Cursor c) {
        User u = new User();
        u.id = c.getLong(c.getColumnIndexOrThrow("id"));
        u.name = c.getString(c.getColumnIndexOrThrow("name"));
        u.username = c.getString(c.getColumnIndexOrThrow("username"));
        int pi = c.getColumnIndexOrThrow("photo");
        u.photoPath = c.isNull(pi) ? null : c.getString(pi);
        u.admin = c.getInt(c.getColumnIndexOrThrow("admin")) == 1;
        return u;
    }

    // ---------- Questions ----------

    public List<Question> questionsFor(String subject) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("questions", null, "subject=?",
                new String[]{subject}, null, null, "id ASC");
        List<Question> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readQuestion(c));
        c.close();
        return out;
    }

    public List<Question> allQuestions() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("questions", null, null, null, null, null, "subject ASC, id ASC");
        List<Question> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readQuestion(c));
        c.close();
        return out;
    }

    public Question getQuestion(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("questions", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Question q = null;
        if (c.moveToFirst()) q = readQuestion(c);
        c.close();
        return q;
    }

    public long addQuestion(Question q) {
        SQLiteDatabase db = getWritableDatabase();
        return db.insert("questions", null, questionValues(q));
    }

    public void updateQuestion(Question q) {
        SQLiteDatabase db = getWritableDatabase();
        db.update("questions", questionValues(q), "id=?", new String[]{String.valueOf(q.id)});
    }

    public void deleteQuestion(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("questions", "id=?", new String[]{String.valueOf(id)});
    }

    private ContentValues questionValues(Question q) {
        ContentValues v = new ContentValues();
        v.put("subject", q.subject);
        v.put("question", q.question);
        v.put("opt0", q.options[0]);
        v.put("opt1", q.options[1]);
        v.put("opt2", q.options[2]);
        v.put("opt3", q.options[3]);
        v.put("correct", q.correctIndex);
        v.put("explanation", q.explanation);
        v.put("chapter", q.chapter == null ? "General" : q.chapter);
        v.put("topic", q.topic == null ? "Mixed Concepts" : q.topic);
        return v;
    }

    private Question readQuestion(Cursor c) {
        Question q = new Question();
        q.id = c.getLong(c.getColumnIndexOrThrow("id"));
        q.subject = c.getString(c.getColumnIndexOrThrow("subject"));
        q.question = c.getString(c.getColumnIndexOrThrow("question"));
        q.options[0] = c.getString(c.getColumnIndexOrThrow("opt0"));
        q.options[1] = c.getString(c.getColumnIndexOrThrow("opt1"));
        q.options[2] = c.getString(c.getColumnIndexOrThrow("opt2"));
        q.options[3] = c.getString(c.getColumnIndexOrThrow("opt3"));
        q.correctIndex = c.getInt(c.getColumnIndexOrThrow("correct"));
        q.explanation = c.getString(c.getColumnIndexOrThrow("explanation"));
        int ci = c.getColumnIndex("chapter");
        int ti = c.getColumnIndex("topic");
        if (ci >= 0 && !c.isNull(ci)) q.chapter = c.getString(ci);
        if (ti >= 0 && !c.isNull(ti)) q.topic = c.getString(ti);
        return q;
    }

    // ---------- Results & performance ----------

    public void addResult(long userId, String mode, String subject, int score, int total) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("mode", mode);
        v.put("subject", subject);
        v.put("score", score);
        v.put("total", total);
        v.put("date", System.currentTimeMillis());
        db.insert("results", null, v);
    }

    public List<ResultRow> resultsForUser(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("results", null, "user_id=?",
                new String[]{String.valueOf(userId)}, null, null, "date DESC");
        List<ResultRow> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readResult(c));
        c.close();
        return out;
    }

    /** Best score for one mode (+ subject for practice). Returns -1 when there is no result yet. */
    public int bestScore(long userId, String mode, String subject) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT MAX(score) FROM results WHERE user_id=? AND mode=?";
        String[] args;
        if (subject == null) {
            args = new String[]{String.valueOf(userId), mode};
        } else {
            sql += " AND subject=?";
            args = new String[]{String.valueOf(userId), mode, subject};
        }
        Cursor c = db.rawQuery(sql, args);
        int best = -1;
        if (c.moveToFirst() && !c.isNull(0)) best = c.getInt(0);
        c.close();
        return best;
    }

    /** Best mock-test result row for a user, or null. Used by the leaderboard. */
    public ResultRow bestMock(long userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("results", null, "user_id=? AND mode='mock'",
                new String[]{String.valueOf(userId)}, null, null, "score DESC, date ASC", "1");
        ResultRow r = null;
        if (c.moveToFirst()) r = readResult(c);
        c.close();
        return r;
    }

    private ResultRow readResult(Cursor c) {
        ResultRow r = new ResultRow();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.userId = c.getLong(c.getColumnIndexOrThrow("user_id"));
        r.mode = c.getString(c.getColumnIndexOrThrow("mode"));
        r.subject = c.getString(c.getColumnIndexOrThrow("subject"));
        r.score = c.getInt(c.getColumnIndexOrThrow("score"));
        r.total = c.getInt(c.getColumnIndexOrThrow("total"));
        r.dateMillis = c.getLong(c.getColumnIndexOrThrow("date"));
        return r;
    }

    // ---------- Chapters & topics ----------

    public List<TopicInfo> topicsFor(String subject) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT chapter, topic, COUNT(*) AS cnt FROM questions WHERE subject=? " +
                "GROUP BY chapter, topic ORDER BY MIN(id)",
                new String[]{subject});
        List<TopicInfo> out = new ArrayList<>();
        while (c.moveToNext()) {
            TopicInfo t = new TopicInfo();
            t.chapter = c.getString(0);
            t.topic = c.getString(1);
            t.count = c.getInt(2);
            out.add(t);
        }
        c.close();
        return out;
    }

    public List<Question> questionsForTopic(String subject, String chapter, String topic) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("questions", null, "subject=? AND chapter=? AND topic=?",
                new String[]{subject, chapter, topic}, null, null, "id ASC");
        List<Question> out = new ArrayList<>();
        while (c.moveToNext()) out.add(readQuestion(c));
        c.close();
        return out;
    }

    private void insT(SQLiteDatabase db, String subject, String chapter, String topic,
                      String question, String o0, String o1, String o2, String o3,
                      int correct, String explanation) {
        ContentValues v = new ContentValues();
        v.put("subject", subject);
        v.put("chapter", chapter);
        v.put("topic", topic);
        v.put("question", question);
        v.put("opt0", o0);
        v.put("opt1", o1);
        v.put("opt2", o2);
        v.put("opt3", o3);
        v.put("correct", correct);
        v.put("explanation", explanation);
        db.insert("questions", null, v);
    }

    private void seedBatch3(SQLiteDatabase db) {
        // === BATCH3 CONTENT START ===
        insT(db, "Mathematics", "Functions and Limits", "Types of Functions", "If f(x) = x^2, then f(-3) = ?", "-9", "9", "6", "-6", 1, "Squaring a negative number gives a positive result: (-3)^2 = 9.");
        insT(db, "Mathematics", "Functions and Limits", "Types of Functions", "Which of the following is an even function?", "f(x) = x + 1", "f(x) = x^3", "f(x) = x^2", "f(x) = 2x", 2, "An even function satisfies f(-x) = f(x), and (-x)^2 = x^2.");
        insT(db, "Mathematics", "Functions and Limits", "Types of Functions", "The domain of f(x) = 1/(x - 2) excludes which value?", "x = 0", "x = 1", "x = -2", "x = 2", 3, "At x = 2 the denominator becomes zero, so x = 2 is not allowed.");
        insT(db, "Mathematics", "Functions and Limits", "Types of Functions", "If f(x) = 3x - 1 and g(x) = x + 2, then f(g(1)) = ?", "8", "9", "10", "6", 0, "First g(1) = 3, then f(3) = 3(3) - 1 = 8.");
        insT(db, "Mathematics", "Functions and Limits", "Limits Basics", "What is the value of lim (x -> 2) of (x^2 - 4)/(x - 2)?", "0", "2", "4", "Does not exist", 2, "Factoring gives (x - 2)(x + 2)/(x - 2) = x + 2, which is 4 at x = 2.");
        insT(db, "Mathematics", "Functions and Limits", "Limits Basics", "What is the value of lim (x -> 0) of sin(x)/x?", "0", "Infinity", "-1", "1", 3, "This is a standard limit: sin(x)/x approaches 1 as x approaches 0.");
        insT(db, "Mathematics", "Functions and Limits", "Limits Basics", "What is the value of lim (x -> 3) of (2x + 1)?", "7", "5", "6", "8", 0, "Direct substitution gives 2(3) + 1 = 7.");
        insT(db, "Mathematics", "Functions and Limits", "Limits Basics", "What is the value of lim (x -> infinity) of 3/x?", "3", "Infinity", "0", "1", 2, "As x grows very large, 3/x gets closer and closer to 0.");
        insT(db, "Mathematics", "Differentiation", "Rules of Differentiation", "What is d/dx of x^5?", "x^4", "5x^4", "5x^5", "x^5", 1, "By the power rule, bring the power down and reduce it by 1: 5x^4.");
        insT(db, "Mathematics", "Differentiation", "Rules of Differentiation", "What is d/dx of (sin x + e^x)?", "cos x + e^x", "-cos x + e^x", "cos x - e^x", "sin x + e^x", 0, "The derivative of sin x is cos x, and e^x stays e^x.");
        insT(db, "Mathematics", "Differentiation", "Rules of Differentiation", "What is d/dx of (x^2 * e^x)?", "2x * e^x", "x^2 * e^x", "e^x (x^2 + 2x)", "e^x (2x + 1)", 2, "By the product rule: x^2 times e^x plus e^x times 2x.");
        insT(db, "Mathematics", "Differentiation", "Rules of Differentiation", "What is d/dx of ln x?", "e^x", "x ln x", "1/x", "-1/x", 2, "The derivative of the natural log function ln x is 1/x.");
        insT(db, "Mathematics", "Differentiation", "Chain Rule and Implicit Differentiation", "What is d/dx of sin(3x)?", "cos(3x)", "-3 cos(3x)", "3 sin(3x)", "3 cos(3x)", 3, "Derivative of sin is cos, multiplied by the derivative of 3x, which is 3.");
        insT(db, "Mathematics", "Differentiation", "Chain Rule and Implicit Differentiation", "What is d/dx of (2x + 1)^3?", "3(2x + 1)^2", "6(2x + 1)^2", "2(2x + 1)^2", "(2x + 1)^2", 1, "The power 3 comes down, then multiply by 2, the derivative of the inside part.");
        insT(db, "Mathematics", "Differentiation", "Chain Rule and Implicit Differentiation", "If x^2 + y^2 = 25, then dy/dx = ?", "x/y", "y/x", "-x/y", "-y/x", 2, "Differentiating term by term gives 2x + 2y(dy/dx) = 0, so dy/dx = -x/y.");
        insT(db, "Mathematics", "Differentiation", "Chain Rule and Implicit Differentiation", "What is d/dx of e^(x^2)?", "e^(x^2)", "2x e^(x^2)", "2 e^(x^2)", "x^2 e^(x^2)", 1, "e^(x^2) stays the same, multiplied by the derivative of x^2, which is 2x.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Higher Order Derivatives", "If y = x^4, then the second derivative y'' is:", "4x^3", "12x^3", "24x", "12x^2", 3, "The first derivative is 4x^3, and differentiating again gives 12x^2.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Higher Order Derivatives", "If y = sin x, then d2y/dx2 = ?", "cos x", "-sin x", "sin x", "-cos x", 1, "The first derivative is cos x and the second derivative is -sin x.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Higher Order Derivatives", "If y = e^(2x), then y'' = ?", "2e^(2x)", "e^(2x)", "4e^(2x)", "8e^(2x)", 2, "Each differentiation multiplies by 2, so after two times we get 4e^(2x).");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Higher Order Derivatives", "If s = t^3 - 3t, the acceleration at t = 1 is:", "0", "6", "3", "-3", 1, "Velocity is 3t^2 - 3 and acceleration is 6t, which equals 6 at t = 1.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Maxima, Minima and Tangents", "The function f(x) = x^2 - 4x + 3 has a minimum at:", "x = 1", "x = 2", "x = 3", "x = 4", 1, "Setting f'(x) = 2x - 4 = 0 gives x = 2, and the parabola opens upward.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Maxima, Minima and Tangents", "The slope of the tangent to y = x^2 at x = 3 is:", "3", "9", "6", "12", 2, "dy/dx = 2x, so at x = 3 the slope is 2(3) = 6.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Maxima, Minima and Tangents", "For f(x) = x^3, the point x = 0 is a:", "Local maximum", "Local minimum", "Point of inflection", "Corner point", 2, "The slope does not change sign at x = 0, so it is a point of inflection.");
        insT(db, "Mathematics", "Higher Order Derivatives and Applications", "Maxima, Minima and Tangents", "The maximum value of f(x) = -x^2 + 6x - 5 is:", "4", "3", "5", "9", 0, "The vertex is at x = 3, and f(3) = -9 + 18 - 5 = 4.");
        insT(db, "Mathematics", "Integration", "Basic Integration Rules", "What is the integral of x^3 dx?", "3x^2 + C", "x^4 + C", "x^4/4 + C", "4x^4 + C", 2, "Increase the power by 1 and divide by the new power: x^4/4 + C.");
        insT(db, "Mathematics", "Integration", "Basic Integration Rules", "What is the integral of (1/x) dx?", "ln|x| + C", "-1/x^2 + C", "x ln x + C", "1/x + C", 0, "The integral of 1/x is the natural logarithm of x.");
        insT(db, "Mathematics", "Integration", "Basic Integration Rules", "What is the integral of cos x dx?", "-sin x + C", "cos x + C", "tan x + C", "sin x + C", 3, "The derivative of sin x is cos x, so the integral of cos x is sin x + C.");
        insT(db, "Mathematics", "Integration", "Basic Integration Rules", "What is the integral of e^(2x) dx?", "e^(2x) + C", "2e^(2x) + C", "e^(2x)/2 + C", "x e^(2x) + C", 2, "e^(2x) stays the same and is divided by 2, the coefficient of x.");
        insT(db, "Mathematics", "Integration", "Definite Integrals and Area", "The value of the integral of x dx from 0 to 2 is:", "1", "2", "4", "0", 1, "The antiderivative is x^2/2, giving 4/2 - 0 = 2.");
        insT(db, "Mathematics", "Integration", "Definite Integrals and Area", "The value of the integral of sin x dx from 0 to pi is:", "0", "1", "-1", "2", 3, "The antiderivative is -cos x, giving -cos(pi) + cos(0) = 1 + 1 = 2.");
        insT(db, "Mathematics", "Integration", "Definite Integrals and Area", "The area under the line y = x from x = 0 to x = 3 is:", "3", "9", "9/2", "6", 2, "Area equals the integral of x from 0 to 3, which is 9/2.");
        insT(db, "Mathematics", "Integration", "Definite Integrals and Area", "The value of the integral of x^2 dx from -1 to 1 is:", "0", "1/3", "2/3", "2", 2, "The antiderivative is x^3/3, giving 1/3 - (-1/3) = 2/3.");
        insT(db, "Mathematics", "Straight Line", "Slope and Equations of a Line", "The slope of the line through (1, 2) and (3, 6) is:", "1", "3", "2", "4", 2, "Slope = (6 - 2)/(3 - 1) = 4/2 = 2.");
        insT(db, "Mathematics", "Straight Line", "Slope and Equations of a Line", "The equation of a line with slope 2 passing through the origin is:", "y = x + 2", "y = 2x", "y = 2", "x = 2y", 1, "Through the origin c = 0, so y = mx gives y = 2x.");
        insT(db, "Mathematics", "Straight Line", "Slope and Equations of a Line", "The slope of the line 3x + 4y = 12 is:", "3/4", "4/3", "-4/3", "-3/4", 3, "Rearranging gives y = (-3/4)x + 3, so the slope is -3/4.");
        insT(db, "Mathematics", "Straight Line", "Slope and Equations of a Line", "The lines y = 2x + 1 and y = 2x - 3 are:", "Perpendicular", "Parallel", "The same line", "Intersecting at origin", 1, "Both lines have slope 2, and equal slopes mean the lines are parallel.");
        insT(db, "Mathematics", "Straight Line", "Distance and Angle Formulas", "The distance between (0, 0) and (3, 4) is:", "5", "7", "25", "6", 0, "Distance = sqrt(3^2 + 4^2) = sqrt(25) = 5.");
        insT(db, "Mathematics", "Straight Line", "Distance and Angle Formulas", "The midpoint of the segment joining (2, 4) and (6, 8) is:", "(3, 5)", "(4, 6)", "(8, 12)", "(4, 4)", 1, "Midpoint is the average of the coordinates: ((2+6)/2, (4+8)/2) = (4, 6).");
        insT(db, "Mathematics", "Straight Line", "Distance and Angle Formulas", "A line with slope 1 makes which angle with the positive x-axis?", "30 degrees", "60 degrees", "45 degrees", "90 degrees", 2, "tan(theta) = slope = 1, so theta = 45 degrees.");
        insT(db, "Mathematics", "Straight Line", "Distance and Angle Formulas", "A line perpendicular to a line of slope 3 has slope:", "3", "-3", "1/3", "-1/3", 3, "For perpendicular lines the product of the slopes is -1, so the slope is -1/3.");
        insT(db, "Mathematics", "Circle", "Equation of a Circle", "The center of the circle (x - 3)^2 + (y + 2)^2 = 16 is:", "(3, 2)", "(-3, 2)", "(3, -2)", "(-3, -2)", 2, "In the form (x - h)^2 + (y - k)^2 = r^2 the center is (h, k) = (3, -2).");
        insT(db, "Mathematics", "Circle", "Equation of a Circle", "The radius of the circle x^2 + y^2 = 25 is:", "25", "10", "sqrt(5)", "5", 3, "Here r^2 = 25, so the radius r = 5.");
        insT(db, "Mathematics", "Circle", "Equation of a Circle", "The center of the circle x^2 + y^2 - 4x + 6y = 0 is:", "(2, 3)", "(2, -3)", "(-2, -3)", "(-2, 3)", 1, "The center is (-g, -f) where 2g = -4 and 2f = 6, giving (2, -3).");
        insT(db, "Mathematics", "Circle", "Equation of a Circle", "The equation of a circle with center at the origin and radius 4 is:", "x^2 + y^2 = 4", "x^2 + y^2 = 8", "x^2 + y^2 = 16", "x^2 + y^2 = 2", 2, "For center at origin, x^2 + y^2 = r^2 = 4^2 = 16.");
        insT(db, "Mathematics", "Circle", "Tangent and Chord", "A chord of the circle x^2 + y^2 = 25 is at distance 3 from the center. Its length is:", "6", "8", "10", "4", 1, "Half the chord is sqrt(25 - 9) = 4, so the full chord has length 8.");
        insT(db, "Mathematics", "Circle", "Tangent and Chord", "The angle between a tangent to a circle and the radius at the point of contact is:", "45 degrees", "60 degrees", "180 degrees", "90 degrees", 3, "A tangent is always perpendicular to the radius at the point of contact.");
        insT(db, "Mathematics", "Circle", "Tangent and Chord", "The length of the tangent drawn from the point (5, 0) to the circle x^2 + y^2 = 9 is:", "3", "5", "4", "sqrt(34)", 2, "Tangent length = sqrt(5^2 - 9) = sqrt(16) = 4.");
        insT(db, "Mathematics", "Circle", "Tangent and Chord", "The equation of the tangent to x^2 + y^2 = 25 at the point (4, 3) is:", "4x + 3y = 25", "3x + 4y = 25", "4x - 3y = 25", "x + y = 7", 0, "For x^2 + y^2 = a^2, the tangent at (x1, y1) is x*x1 + y*y1 = a^2.");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Parabola", "The focus of the parabola y^2 = 8x is:", "(0, 2)", "(4, 0)", "(2, 0)", "(0, 4)", 2, "Here 4a = 8 so a = 2, and the focus of y^2 = 4ax is (a, 0).");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Parabola", "The vertex of the parabola y = (x - 1)^2 + 2 is:", "(-1, 2)", "(1, 2)", "(1, -2)", "(2, 1)", 1, "In the form y = (x - h)^2 + k, the vertex is (h, k) = (1, 2).");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Parabola", "The directrix of the parabola y^2 = 12x is:", "x = 3", "y = -3", "x = 6", "x = -3", 3, "Here 4a = 12 so a = 3, and the directrix is x = -a = -3.");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Parabola", "The parabola x^2 = 16y opens:", "Downward", "To the right", "Upward", "To the left", 2, "x^2 = 4ay with a positive coefficient opens upward along the y-axis.");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Ellipse and Hyperbola", "For the ellipse x^2/25 + y^2/9 = 1, the semi-major axis a equals:", "3", "25", "9", "5", 3, "The larger denominator is a^2 = 25, so a = 5.");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Ellipse and Hyperbola", "The eccentricity of an ellipse is always:", "Equal to 1", "Greater than 1", "Less than 1", "Equal to 0", 2, "For an ellipse 0 < e < 1; e = 1 gives a parabola and e > 1 a hyperbola.");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Ellipse and Hyperbola", "The foci of the hyperbola x^2/16 - y^2/9 = 1 are:", "(+/-3, 0)", "(+/-4, 0)", "(+/-5, 0)", "(+/-7, 0)", 2, "For a hyperbola c^2 = a^2 + b^2 = 16 + 9 = 25, so c = 5.");
        insT(db, "Mathematics", "Parabola, Ellipse and Hyperbola", "Ellipse and Hyperbola", "The center of the ellipse (x - 2)^2/4 + (y + 1)^2/9 = 1 is:", "(-2, 1)", "(2, 1)", "(-2, -1)", "(2, -1)", 3, "The center is (h, k), and here h = 2 and k = -1.");
        insT(db, "Mathematics", "Vectors", "Vector Basics and Dot Product", "If a = (2, 3) and b = (1, -1), then a + b = ?", "(3, 2)", "(1, 4)", "(3, -2)", "(2, 2)", 0, "Add component by component: (2 + 1, 3 - 1) = (3, 2).");
        insT(db, "Mathematics", "Vectors", "Vector Basics and Dot Product", "The magnitude of the vector (3, 4) is:", "7", "5", "25", "1", 1, "Magnitude = sqrt(3^2 + 4^2) = sqrt(25) = 5.");
        insT(db, "Mathematics", "Vectors", "Vector Basics and Dot Product", "The dot product i . j of the unit vectors i and j is:", "1", "-1", "0", "2", 2, "i and j are perpendicular, and the dot product of perpendicular vectors is 0.");
        insT(db, "Mathematics", "Vectors", "Vector Basics and Dot Product", "If a = (1, 2, 3) and b = (3, 2, 1), then a . b = ?", "8", "9", "11", "10", 3, "Multiply matching components and add: 1(3) + 2(2) + 3(1) = 10.");
        insT(db, "Mathematics", "Vectors", "Cross Product and Applications", "The cross product i x j equals:", "-k", "k", "0", "1", 1, "By the right-hand rule for unit vectors, i x j = k.");
        insT(db, "Mathematics", "Vectors", "Cross Product and Applications", "If a = 2i and b = 3j, then |a x b| = ?", "0", "5", "6", "1", 2, "a x b = 6k, and the magnitude of 6k is 6.");
        insT(db, "Mathematics", "Vectors", "Cross Product and Applications", "The area of the parallelogram with adjacent sides a = (1, 2) and b = (3, 1) is:", "5", "7", "6", "4", 0, "Area = |a x b| = |1(1) - 2(3)| = |-5| = 5.");
        insT(db, "Mathematics", "Vectors", "Cross Product and Applications", "If two vectors a and b are parallel, then a x b is:", "A unit vector", "The zero vector", "Equal to a", "Equal to b", 1, "For parallel vectors sin(theta) = 0, so the cross product is the zero vector.");
        insT(db, "Mathematics", "Differential Equations", "Order, Degree and Formation", "The order of the differential equation dy/dx + y = sin x is:", "0", "1", "2", "3", 1, "The highest derivative present is dy/dx, the first derivative, so the order is 1.");
        insT(db, "Mathematics", "Differential Equations", "Order, Degree and Formation", "The degree of the equation (dy/dx)^2 + dy/dx + y = 0 is:", "1", "3", "2", "Not defined", 2, "Degree is the power of the highest derivative, and dy/dx has power 2.");
        insT(db, "Mathematics", "Differential Equations", "Order, Degree and Formation", "The order of the equation d3y/dx3 + 2(d2y/dx2) = x is:", "1", "2", "5", "3", 3, "The highest derivative is the third derivative, so the order is 3.");
        insT(db, "Mathematics", "Differential Equations", "Order, Degree and Formation", "The differential equation of the family y = A sin x, where A is an arbitrary constant, is:", "y'' - y = 0", "y'' + y = 0", "y' + y = 0", "y'' + y' = 0", 1, "Differentiating twice gives y'' = -A sin x = -y, so y'' + y = 0.");
        insT(db, "Mathematics", "Differential Equations", "Solving Simple Differential Equations", "The solution of dy/dx = 3x^2 is:", "3x^3 + C", "x^3 + C", "6x + C", "x^2 + C", 1, "Integrating 3x^2 with respect to x gives x^3 + C.");
        insT(db, "Mathematics", "Differential Equations", "Solving Simple Differential Equations", "The solution of dy/dx = y is:", "y = x + C", "y = Cx", "y = Ce^x", "y = e^x + C", 2, "Separating variables gives dy/y = dx, which integrates to y = Ce^x.");
        insT(db, "Mathematics", "Differential Equations", "Solving Simple Differential Equations", "The solution of dy/dx = x/y is:", "y = x + C", "y^2 = x^2 + C", "xy = C", "y = x^2 + C", 1, "Separating gives y dy = x dx, and integrating both sides gives y^2 = x^2 + C.");
        insT(db, "Mathematics", "Differential Equations", "Solving Simple Differential Equations", "The solution of dy/dx = cos x is:", "-sin x + C", "cos x + C", "sin x + C", "tan x + C", 2, "The integral of cos x is sin x, plus a constant C.");
        insT(db, "Physics", "Molecular Theory of Gases", "Kinetic Theory Basics", "The average kinetic energy of the molecules of an ideal gas depends only on:", "its pressure", "its volume", "its absolute temperature", "the mass of each molecule", 2, "Average KE = (3/2) k T, so it depends only on the absolute temperature.");
        insT(db, "Physics", "Molecular Theory of Gases", "Kinetic Theory Basics", "The pressure exerted by a gas in a closed container is due to:", "the weight of the molecules", "collisions of molecules with the walls of the container", "attraction between the molecules", "rotation of the molecules", 1, "Molecules strike the walls again and again, and these collisions produce pressure.");
        insT(db, "Physics", "Molecular Theory of Gases", "Kinetic Theory Basics", "At absolute zero temperature, the average kinetic energy of ideal gas molecules becomes:", "zero", "maximum", "half of its room temperature value", "infinite", 0, "KE is proportional to T, so at T = 0 K the molecular KE is zero in theory.");
        insT(db, "Physics", "Molecular Theory of Gases", "Kinetic Theory Basics", "When a gas is compressed slowly at constant temperature, the average kinetic energy of its molecules:", "increases", "decreases", "remains the same", "becomes zero", 2, "At constant temperature the average KE stays the same; only the collision rate rises.");
        insT(db, "Physics", "Molecular Theory of Gases", "Gas Laws and RMS Speed", "The rms speed of the molecules of a gas is proportional to:", "T", "sqrt(T)", "1/T", "T squared", 1, "v(rms) = sqrt(3RT/M), so it grows with the square root of absolute temperature.");
        insT(db, "Physics", "Molecular Theory of Gases", "Gas Laws and RMS Speed", "If the absolute temperature of a gas is raised from 300 K to 1200 K, its rms speed:", "becomes double", "becomes four times", "is halved", "remains unchanged", 0, "Temperature becomes 4 times, and sqrt(4) = 2, so the rms speed doubles.");
        insT(db, "Physics", "Molecular Theory of Gases", "Gas Laws and RMS Speed", "A gas occupies 2 m^3 at 100 kPa. At constant temperature its pressure is raised to 200 kPa. Its new volume is:", "4 m^3", "2 m^3", "0.5 m^3", "1 m^3", 3, "By Boyle law P1V1 = P2V2, so V2 = (100 x 2)/200 = 1 m^3.");
        insT(db, "Physics", "Molecular Theory of Gases", "Gas Laws and RMS Speed", "A fixed mass of gas has volume 300 cm^3 at 300 K. When heated to 400 K at constant pressure, its volume becomes:", "350 cm^3", "400 cm^3", "450 cm^3", "600 cm^3", 1, "By Charles law V/T is constant, so V2 = 300 x (400/300) = 400 cm^3.");
        insT(db, "Physics", "First Law of Thermodynamics", "Heat, Work and Internal Energy", "A gas absorbs 500 J of heat and does 200 J of work. The change in its internal energy is:", "700 J", "300 J", "200 J", "500 J", 1, "delta U = Q - W = 500 - 200 = 300 J.");
        insT(db, "Physics", "First Law of Thermodynamics", "Heat, Work and Internal Energy", "The internal energy of an ideal gas depends only on:", "its pressure", "its volume", "its temperature", "the path followed", 2, "For an ideal gas, internal energy is a function of temperature only.");
        insT(db, "Physics", "First Law of Thermodynamics", "Heat, Work and Internal Energy", "100 J of work is done ON a gas in an adiabatic compression. Its internal energy:", "decreases by 100 J", "increases by 100 J", "remains the same", "increases by 200 J", 1, "In an adiabatic process Q = 0, so the work done on the gas raises its internal energy by 100 J.");
        insT(db, "Physics", "First Law of Thermodynamics", "Heat, Work and Internal Energy", "Which of the following quantities is a state function (depends only on the state, not the path)?", "heat", "work", "internal energy", "none of these", 2, "Internal energy depends only on the present state; heat and work depend on the path.");
        insT(db, "Physics", "First Law of Thermodynamics", "Thermodynamic Processes", "In an isothermal expansion of an ideal gas, the change in internal energy is:", "zero", "positive", "negative", "equal to the heat rejected", 0, "Temperature is constant in an isothermal process, so delta U = 0 for an ideal gas.");
        insT(db, "Physics", "First Law of Thermodynamics", "Thermodynamic Processes", "When a gas expands adiabatically, its temperature:", "rises", "falls", "stays constant", "first rises then falls", 1, "The gas does work from its own internal energy, so it cools down.");
        insT(db, "Physics", "First Law of Thermodynamics", "Thermodynamic Processes", "In an isochoric (constant volume) process, the work done by the gas is:", "maximum", "equal to the heat supplied", "zero", "always negative", 2, "Work = P x delta V, and delta V = 0 at constant volume, so no work is done.");
        insT(db, "Physics", "First Law of Thermodynamics", "Thermodynamic Processes", "A gas at pressure 1 x 10^5 Pa expands by 0.01 m^3 at constant pressure. The work done by the gas is:", "100 J", "1000 J", "10000 J", "10 J", 1, "W = P x delta V = 10^5 x 0.01 = 1000 J.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Entropy and Heat Engines", "In every irreversible (real) process, the entropy of the universe:", "decreases", "remains zero", "increases", "becomes negative", 2, "The second law says the total entropy of the universe always increases in real processes.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Entropy and Heat Engines", "A heat engine can never convert all the absorbed heat into work because:", "of friction in its parts", "its fuel is never pure", "its size is limited", "some heat must be rejected to the cold sink", 3, "The Kelvin-Planck statement: some heat must always be given out to the sink.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Entropy and Heat Engines", "An engine absorbs 1000 J of heat and does 300 J of work in one cycle. The heat rejected to the sink is:", "300 J", "700 J", "1000 J", "1300 J", 1, "Rejected heat = 1000 - 300 = 700 J.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Entropy and Heat Engines", "The SI unit of entropy is:", "J/K", "J", "W/K", "calorie", 0, "Entropy change = heat / temperature, so its unit is joule per kelvin.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Carnot Cycle and Refrigerators", "The efficiency of a Carnot engine depends only on:", "the working substance used", "the temperatures of the source and the sink", "the size of the engine", "the amount of heat absorbed", 1, "Carnot efficiency = 1 - Tc/Th; it depends only on the two reservoir temperatures.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Carnot Cycle and Refrigerators", "A Carnot engine works between 400 K and 300 K. Its efficiency is:", "20 percent", "25 percent", "33 percent", "75 percent", 1, "Efficiency = 1 - 300/400 = 0.25, that is 25 percent.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Carnot Cycle and Refrigerators", "A refrigerator can transfer heat from a cold body to a hot body because:", "it creates cold", "it destroys heat", "external work is done on it", "it stops entropy", 2, "Work from the motor drives the cycle, carrying heat from the cold interior to the warm room.");
        insT(db, "Physics", "Second Law of Thermodynamics", "Carnot Cycle and Refrigerators", "The Carnot cycle consists of:", "two isothermal and two adiabatic processes", "four isothermal processes", "two isochoric and two adiabatic processes", "four adiabatic processes", 0, "A Carnot cycle has two isothermal steps and two adiabatic steps.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Force on Charges and Wires", "A charged particle moving parallel to a uniform magnetic field experiences a magnetic force of:", "zero", "maximum value", "half the maximum value", "infinite value", 0, "F = q v B sin(theta), and theta = 0 for parallel motion, so the force is zero.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Force on Charges and Wires", "A 0.5 m wire carrying 2 A is placed at right angles to a 0.4 T field. The force on the wire is:", "4 N", "0.04 N", "0.4 N", "1.6 N", 2, "F = B I L = 0.4 x 2 x 0.5 = 0.4 N.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Force on Charges and Wires", "The magnetic force on a moving charge is always directed:", "along its velocity", "along the field", "opposite to the field", "perpendicular to both velocity and field", 3, "The force F = q(v x B) is perpendicular to the plane of v and B.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Force on Charges and Wires", "A charged particle enters a uniform magnetic field at right angles to the field. It moves in:", "a straight line", "a circle", "a parabola", "a straight line with increasing speed", 1, "The force is always at right angles to the velocity, so the particle moves in a circle at constant speed.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Field of Currents", "If the distance from a long straight current-carrying wire is doubled, the magnetic field at that point becomes:", "double", "half", "four times", "unchanged", 1, "B = mu0 I / (2 pi r), so B is inversely proportional to r; doubling r halves B.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Field of Currents", "The magnetic field inside a long current-carrying solenoid is:", "zero", "nearly uniform and strong", "very weak", "circular around the wire turns only", 1, "Inside a solenoid the field is strong and nearly uniform along its axis.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Field of Currents", "If the current through a long straight wire is doubled, the magnetic field around it:", "is halved", "is unchanged", "doubles", "becomes four times", 2, "B is directly proportional to I, so doubling the current doubles the field.");
        insT(db, "Physics", "Magnetic Fields", "Magnetic Field of Currents", "The magnetic field lines around a straight current-carrying conductor are:", "concentric circles", "straight parallel lines", "radial lines pointing outward", "ellipses", 0, "By the right-hand rule, the field lines form circles centred on the wire.");
        insT(db, "Physics", "Electromagnetic Induction", "Faraday and Lenz Laws", "The magnitude of the emf induced in a coil depends on:", "the resistance of the coil", "the area of the coil only", "the rate of change of magnetic flux through it", "the colour of the magnet", 2, "Faraday law: induced emf = rate of change of flux linkage.");
        insT(db, "Physics", "Electromagnetic Induction", "Faraday and Lenz Laws", "Lenz law is a direct consequence of the law of conservation of:", "energy", "charge", "momentum", "mass", 0, "The induced current opposes the change, so no work means no energy from nothing.");
        insT(db, "Physics", "Electromagnetic Induction", "Faraday and Lenz Laws", "The flux through a 100-turn coil changes by 0.02 Wb in 0.1 s. The induced emf is:", "2 V", "20 V", "200 V", "0.2 V", 1, "emf = N x (delta flux / delta t) = 100 x (0.02 / 0.1) = 20 V.");
        insT(db, "Physics", "Electromagnetic Induction", "Faraday and Lenz Laws", "The magnetic flux through a coil is maximum when the plane of the coil is:", "parallel to the field", "at 45 degrees to the field", "at 60 degrees to the field", "perpendicular to the field", 3, "Flux = B A cos(theta) is greatest when the coil normal is along the field, i.e. plane normal to B.");
        insT(db, "Physics", "Electromagnetic Induction", "Inductance and Transformers", "The SI unit of self-inductance is:", "weber", "henry", "tesla", "ohm", 1, "Self-inductance is measured in henry (H).");
        insT(db, "Physics", "Electromagnetic Induction", "Inductance and Transformers", "An inductor in a circuit opposes:", "the flow of charge itself", "the voltage of the battery", "any change in the current through it", "the resistance of the wires", 2, "The self-induced emf always acts against the change in current.");
        insT(db, "Physics", "Electromagnetic Induction", "Inductance and Transformers", "A transformer has 100 turns on the primary and 2000 on the secondary. If 12 V AC is applied to the primary, the secondary voltage is:", "240 V", "24 V", "120 V", "6 V", 0, "Vs/Vp = Ns/Np = 20, so Vs = 12 x 20 = 240 V.");
        insT(db, "Physics", "Electromagnetic Induction", "Inductance and Transformers", "In an ideal step-up transformer, the current in the secondary coil compared with the primary is:", "equal", "greater", "zero", "less", 3, "Power in = power out; voltage rises, so current must fall.");
        insT(db, "Physics", "Alternating Current", "AC Basics and RMS Values", "The rms value of an alternating voltage is related to its peak value V0 by:", "Vrms = V0", "Vrms = 0.707 V0", "Vrms = 1.414 V0", "Vrms = 0.5 V0", 1, "Vrms = V0 / sqrt(2) = 0.707 V0.");
        insT(db, "Physics", "Alternating Current", "AC Basics and RMS Values", "An AC supply has a peak voltage of 311 V. Its rms voltage is about:", "311 V", "220 V", "440 V", "110 V", 1, "Vrms = 311 x 0.707 = about 220 V.");
        insT(db, "Physics", "Alternating Current", "AC Basics and RMS Values", "The angular frequency of the 50 Hz mains supply is about:", "50 rad/s", "100 rad/s", "314 rad/s", "628 rad/s", 2, "omega = 2 pi f = 2 x 3.14 x 50 = 314 rad/s.");
        insT(db, "Physics", "Alternating Current", "AC Basics and RMS Values", "An ordinary AC voltmeter actually measures:", "the rms value", "the peak value", "the average value", "the instantaneous value", 0, "AC meters are calibrated to read rms values.");
        insT(db, "Physics", "Alternating Current", "Reactance, Impedance and Resonance", "The inductive reactance of a coil increases when:", "the frequency of the supply increases", "the frequency of the supply decreases", "its resistance increases", "it is connected to DC", 0, "XL = 2 pi f L, so XL rises with frequency.");
        insT(db, "Physics", "Alternating Current", "Reactance, Impedance and Resonance", "At resonance in a series RLC circuit, the impedance is:", "maximum", "infinite", "minimum and equal to R", "exactly zero", 2, "At resonance XL = XC and they cancel, leaving Z = R, the minimum impedance.");
        insT(db, "Physics", "Alternating Current", "Reactance, Impedance and Resonance", "The power factor of a series RLC circuit at resonance is:", "zero", "one", "0.5", "infinite", 1, "At resonance voltage and current are in phase, so cos(phi) = 1.");
        insT(db, "Physics", "Alternating Current", "Reactance, Impedance and Resonance", "A coil of inductance 0.1 H is connected to a 50 Hz supply. Its inductive reactance is about:", "3.14 ohm", "31.4 ohm", "314 ohm", "0.314 ohm", 1, "XL = 2 pi f L = 2 x 3.14 x 50 x 0.1 = 31.4 ohm.");
        insT(db, "Physics", "Physics of Solids", "Stress, Strain and Elasticity", "The SI unit of stress is:", "newton", "joule", "pascal", "metre", 2, "Stress = force / area, measured in N/m^2, called the pascal.");
        insT(db, "Physics", "Physics of Solids", "Stress, Strain and Elasticity", "Young modulus of a material is defined as:", "stress / strain", "strain / stress", "stress x strain", "force x length", 0, "Within the elastic limit, Y = tensile stress / tensile strain.");
        insT(db, "Physics", "Physics of Solids", "Stress, Strain and Elasticity", "A force of 100 N acts on a wire of cross-sectional area 1 x 10^-6 m^2. The stress in the wire is:", "10^6 Pa", "10^8 Pa", "10^4 Pa", "10^10 Pa", 1, "Stress = F/A = 100 / 10^-6 = 10^8 Pa.");
        insT(db, "Physics", "Physics of Solids", "Stress, Strain and Elasticity", "Strain is a pure ratio, therefore it has:", "the unit of length", "the unit of pascal", "the unit of newton", "no unit", 3, "Strain = change in length / original length, so the units cancel.");
        insT(db, "Physics", "Physics of Solids", "Band Theory and Conductors", "In a good conductor at ordinary temperatures, the valence band and conduction band:", "are widely separated", "overlap each other", "have a gap of 5 eV", "are both completely empty", 1, "In conductors the bands overlap, so electrons move freely.");
        insT(db, "Physics", "Physics of Solids", "Band Theory and Conductors", "When the temperature of a semiconductor is increased, its electrical conductivity:", "decreases", "remains the same", "increases", "becomes zero", 2, "Heat frees more charge carriers in a semiconductor, so conductivity rises.");
        insT(db, "Physics", "Physics of Solids", "Band Theory and Conductors", "The forbidden energy gap of an insulator is:", "zero", "small, about 1 eV", "large, several eV", "negative", 2, "Insulators like diamond have a large gap of about 5 eV or more, so electrons cannot jump it easily.");
        insT(db, "Physics", "Physics of Solids", "Band Theory and Conductors", "At absolute zero, a pure (intrinsic) semiconductor behaves like:", "a good conductor", "an insulator", "a superconductor", "a metal", 1, "At 0 K all bonds are complete and there are no free carriers, so it insulates.");
        insT(db, "Physics", "Solid State Electronics", "Diodes and Rectification", "A p-n junction diode conducts appreciable current mainly when it is:", "forward biased", "reverse biased", "not biased at all", "kept in the dark", 0, "Forward bias narrows the depletion region and lets current flow.");
        insT(db, "Physics", "Solid State Electronics", "Diodes and Rectification", "A half-wave rectifier allows through:", "both halves of every AC cycle", "the negative half only", "only one half of each AC cycle", "pure DC only", 2, "One diode conducts on alternate half cycles only, giving pulsating DC.");
        insT(db, "Physics", "Solid State Electronics", "Diodes and Rectification", "When a p-n junction is reverse biased, its depletion region:", "narrows", "widens", "disappears completely", "remains unchanged", 1, "Reverse bias pulls carriers away from the junction, widening the depletion layer.");
        insT(db, "Physics", "Solid State Electronics", "Diodes and Rectification", "A Zener diode is mainly used as a:", "amplifier", "rectifier", "oscillator", "voltage regulator", 3, "In reverse breakdown the Zener voltage stays nearly constant, so it stabilises voltage.");
        insT(db, "Physics", "Solid State Electronics", "Transistors and Amplifiers", "In a transistor, Ic = 10 mA and Ib = 0.2 mA. The current gain beta is:", "5", "50", "500", "0.02", 1, "beta = Ic / Ib = 10 / 0.2 = 50.");
        insT(db, "Physics", "Solid State Electronics", "Transistors and Amplifiers", "The most heavily doped region of a transistor is the:", "base", "emitter", "collector", "junction", 1, "The emitter is heavily doped so it can inject many charge carriers into the base.");
        insT(db, "Physics", "Solid State Electronics", "Transistors and Amplifiers", "In a common-emitter amplifier, the output signal is, compared with the input:", "exactly in phase", "90 degrees out of phase", "180 degrees out of phase", "unrelated in phase", 2, "A common-emitter amplifier inverts the signal, a phase change of 180 degrees.");
        insT(db, "Physics", "Solid State Electronics", "Transistors and Amplifiers", "In a transistor, the large collector current is controlled by the small:", "base current", "collector voltage only", "room temperature", "emitter resistance", 0, "A small change in base current produces a large change in collector current.");
        insT(db, "Physics", "Digital Electronics", "Logic Gates", "The output of an AND gate is 1 only when:", "any one input is 1", "no input is 1", "all inputs are 1", "exactly one input is 1", 2, "AND needs every input to be 1 for the output to be 1.");
        insT(db, "Physics", "Digital Electronics", "Logic Gates", "The output of an OR gate is 0 only when:", "all inputs are 0", "any one input is 0", "all inputs are 1", "the two inputs are different", 0, "OR gives 1 if even a single input is 1; it is 0 only when all inputs are 0.");
        insT(db, "Physics", "Digital Electronics", "Logic Gates", "For an XOR gate with A = 1 and B = 0, the output is:", "0", "1", "both 0 and 1", "undefined", 1, "XOR gives 1 when the inputs are different.");
        insT(db, "Physics", "Digital Electronics", "Logic Gates", "The universal gates, from which all other gates can be built, are:", "AND and OR", "NOT and AND", "XOR and XNOR", "NAND and NOR", 3, "Any logic circuit can be made using only NAND gates, or only NOR gates.");
        insT(db, "Physics", "Digital Electronics", "Boolean Operations and ICs", "In Boolean algebra, the value of A + 1 is:", "A", "0", "1", "the complement of A", 2, "In OR operation, 1 combined with anything gives 1.");
        insT(db, "Physics", "Digital Electronics", "Boolean Operations and ICs", "According to De Morgan theorem, the complement of (A . B) is:", "A' . B'", "A' + B'", "A + B", "A . B", 1, "De Morgan: complement of a product is the sum of the complements.");
        insT(db, "Physics", "Digital Electronics", "Boolean Operations and ICs", "An integrated circuit (IC) contains a very large number of components on:", "a single small chip of silicon", "many separate circuit boards", "one vacuum tube", "a wire-wound board", 0, "An IC packs thousands of transistors and other parts on one silicon chip.");
        insT(db, "Physics", "Digital Electronics", "Boolean Operations and ICs", "The Boolean expression for the output of an OR gate is:", "Y = A . B", "Y = A + B", "Y = A - B", "Y = complement of A", 1, "In Boolean notation, + stands for the OR operation.");
        insT(db, "Physics", "Relativity", "Postulates and Time Dilation", "According to the second postulate of special relativity, the speed of light in vacuum:", "depends on the motion of the source", "is the same for all inertial observers", "depends on the motion of the observer", "is different in every medium only", 1, "c is constant for every observer in an inertial frame, whatever their motion.");
        insT(db, "Physics", "Relativity", "Postulates and Time Dilation", "A clock moving at high speed relative to an observer appears to run:", "slow", "fast", "at its normal rate", "backwards", 0, "Moving clocks are observed to tick more slowly: time dilation.");
        insT(db, "Physics", "Relativity", "Postulates and Time Dilation", "An event takes 3 s on a clock moving at 0.8 c. For a stationary observer this interval appears to be:", "3 s", "4 s", "5 s", "6 s", 2, "gamma = 1/sqrt(1 - 0.64) = 1/0.6, so t = 3/0.6 = 5 s.");
        insT(db, "Physics", "Relativity", "Postulates and Time Dilation", "Proper time between two events is the time measured by a clock that:", "is always on Earth", "moves at the speed of light", "is far away from the events", "is present at both events at the same place", 3, "Proper time is measured in the frame where the two events happen at the same position.");
        insT(db, "Physics", "Relativity", "Mass-Energy and Length Contraction", "The energy equivalent of a mass of 1 kg, using E = m c^2, is:", "3 x 10^8 J", "9 x 10^16 J", "9 x 10^8 J", "3 x 10^16 J", 1, "E = 1 x (3 x 10^8)^2 = 9 x 10^16 J.");
        insT(db, "Physics", "Relativity", "Mass-Energy and Length Contraction", "Length contraction of a fast-moving object is observed:", "in all directions equally", "only along the direction of motion", "only perpendicular to the motion", "only when it is at rest", 1, "Only the dimension along the motion shrinks; widths at right angles do not change.");
        insT(db, "Physics", "Relativity", "Mass-Energy and Length Contraction", "A rod of proper length 10 m moves at 0.6 c past an observer. Its measured length is:", "8 m", "10 m", "6 m", "12.5 m", 0, "L = L0 sqrt(1 - 0.36) = 10 x 0.8 = 8 m.");
        insT(db, "Physics", "Relativity", "Mass-Energy and Length Contraction", "As the speed of a body approaches the speed of light, its total relativistic energy:", "becomes zero", "stays equal to m c^2", "increases without limit", "becomes negative", 2, "gamma grows without bound as v approaches c, so the energy grows without limit.");
        insT(db, "Physics", "Quantum Physics", "Photoelectric Effect", "No photoelectrons are emitted, however intense the light, if the frequency of the light is:", "very high", "equal to the threshold frequency", "below the threshold frequency", "in the ultraviolet range", 2, "Each photon must have energy at least equal to the work function; intensity cannot help.");
        insT(db, "Physics", "Quantum Physics", "Photoelectric Effect", "Increasing the intensity of light falling on a metal surface increases:", "the maximum energy of the photoelectrons", "the number of photoelectrons emitted per second", "the work function of the metal", "the threshold frequency", 1, "Brighter light means more photons, hence more electrons, but each has the same maximum energy.");
        insT(db, "Physics", "Quantum Physics", "Photoelectric Effect", "Light of photon energy 5 eV falls on a metal of work function 2 eV. The maximum kinetic energy of the emitted electrons is:", "3 eV", "2 eV", "5 eV", "7 eV", 0, "KE(max) = hf - work function = 5 - 2 = 3 eV.");
        insT(db, "Physics", "Quantum Physics", "Photoelectric Effect", "The stopping potential in a photoelectric experiment is a measure of:", "the intensity of light", "the maximum kinetic energy of the photoelectrons", "the work function alone", "the wavelength of light", 1, "e x V0 = KE(max); the retarding voltage that stops the fastest electrons gives their energy.");
        insT(db, "Physics", "Quantum Physics", "Compton Effect and de Broglie Waves", "The Compton effect provides evidence that photons possess:", "momentum", "electric charge", "rest mass", "colour", 0, "The X-ray photon transfers momentum to the electron, like a particle collision.");
        insT(db, "Physics", "Quantum Physics", "Compton Effect and de Broglie Waves", "In Compton scattering, the wavelength of the scattered X-ray photon, compared with the incident one:", "decreases", "stays the same", "increases", "becomes zero", 2, "The photon loses energy to the electron, so its wavelength increases.");
        insT(db, "Physics", "Quantum Physics", "Compton Effect and de Broglie Waves", "If the momentum of a particle is doubled, its de Broglie wavelength:", "doubles", "halves", "becomes four times", "stays the same", 1, "lambda = h / p, so doubling p halves the wavelength.");
        insT(db, "Physics", "Quantum Physics", "Compton Effect and de Broglie Waves", "A particle has momentum 3.3 x 10^-24 kg m/s. Taking h = 6.6 x 10^-34 J s, its de Broglie wavelength is:", "2 x 10^-24 m", "1 x 10^-10 m", "6.6 x 10^-10 m", "2 x 10^-10 m", 3, "lambda = h / p = (6.6 x 10^-34) / (3.3 x 10^-24) = 2 x 10^-10 m.");
        insT(db, "Physics", "Atomic Physics", "Bohr Model and Spectra", "In Bohr model of the atom, the angular momentum of the electron is quantised as:", "n h", "n h / (2 pi)", "n squared x h", "2 pi / (n h)", 1, "Bohr postulate: m v r = n h / (2 pi), where n = 1, 2, 3, ...");
        insT(db, "Physics", "Atomic Physics", "Bohr Model and Spectra", "Using E(n) = -13.6 / n^2 eV, the energy of the hydrogen electron in the n = 2 state is:", "-13.6 eV", "-6.8 eV", "-3.4 eV", "-1.7 eV", 2, "E2 = -13.6 / 4 = -3.4 eV.");
        insT(db, "Physics", "Atomic Physics", "Bohr Model and Spectra", "A photon is emitted by an atom when an electron jumps:", "from a lower to a higher energy level", "around the nucleus once", "from a higher to a lower energy level", "without changing its level", 2, "The photon carries away the energy difference between the two levels.");
        insT(db, "Physics", "Atomic Physics", "Bohr Model and Spectra", "The Balmer series of the hydrogen spectrum lies in the:", "ultraviolet region", "visible region", "infrared region", "X-ray region", 1, "Balmer lines (jumps to n = 2) fall in visible light.");
        insT(db, "Physics", "Atomic Physics", "X-Rays and Lasers", "X-rays are produced in an X-ray tube when:", "atoms of the gas are heated", "light falls on a metal surface", "a gas is ionised by a spark", "fast electrons strike a heavy metal target", 3, "The sudden stopping of fast electrons in the target produces X-rays.");
        insT(db, "Physics", "Atomic Physics", "X-Rays and Lasers", "Increasing the voltage across an X-ray tube increases the:", "wavelength of the X-rays", "penetrating power of the X-rays", "size of the target", "mass of the electrons", 1, "Higher voltage gives shorter wavelength, more penetrating (harder) X-rays.");
        insT(db, "Physics", "Atomic Physics", "X-Rays and Lasers", "Laser light is different from ordinary light because it is:", "incoherent and weak", "made of many colours", "coherent and monochromatic", "always invisible", 2, "In laser light the waves are in phase (coherent) and of a single colour (monochromatic).");
        insT(db, "Physics", "Atomic Physics", "X-Rays and Lasers", "A necessary condition for laser action in a material is:", "population inversion", "very low temperature", "very high pressure", "a strong magnetic field", 0, "More atoms must be in the excited state than in the lower state: population inversion.");
        insT(db, "Physics", "Nuclear Physics", "Radioactivity and Half-Life", "A radioactive sample has mass 80 g. After 3 half-lives, the mass of the undecayed substance left is:", "40 g", "20 g", "10 g", "5 g", 2, "Each half-life halves the amount: 80 to 40 to 20 to 10 g.");
        insT(db, "Physics", "Nuclear Physics", "Radioactivity and Half-Life", "An alpha particle is actually:", "a helium nucleus", "a fast electron", "a photon of gamma rays", "a single proton", 0, "An alpha particle has 2 protons and 2 neutrons, like a helium nucleus.");
        insT(db, "Physics", "Nuclear Physics", "Radioactivity and Half-Life", "The rate of radioactive decay of a sample can be changed by:", "heating it strongly", "keeping it under high pressure", "changing its chemical state", "none of these factors", 3, "Decay is a nuclear process, unaffected by temperature, pressure or chemical changes.");
        insT(db, "Physics", "Nuclear Physics", "Radioactivity and Half-Life", "The SI unit of activity of a radioactive source is:", "gray", "becquerel", "sievert", "roentgen", 1, "Activity is measured in becquerel: one decay per second.");
        insT(db, "Physics", "Nuclear Physics", "Fission, Fusion and Binding Energy", "The mass defect of a nucleus is:", "the mass of the nucleus alone", "the mass of the separate nucleons minus the mass of the nucleus", "the mass gained during fusion", "the mass of the neutrons only", 1, "The bound nucleus has less mass than its separate protons and neutrons; the difference is the mass defect.");
        insT(db, "Physics", "Nuclear Physics", "Fission, Fusion and Binding Energy", "Nuclear fusion takes place naturally in:", "nuclear power reactors", "atom bombs only", "X-ray tubes", "the sun and other stars", 3, "In stars, hydrogen nuclei fuse into helium at very high temperature.");
        insT(db, "Physics", "Nuclear Physics", "Fission, Fusion and Binding Energy", "The binding energy per nucleon is greatest for nuclei close to:", "iron", "uranium", "hydrogen", "helium", 0, "Iron-56 is near the peak of the binding energy curve, so it is very stable.");
        insT(db, "Physics", "Nuclear Physics", "Fission, Fusion and Binding Energy", "In a nuclear reactor, the chain reaction is kept under control by rods made of:", "graphite", "heavy water", "cadmium or boron", "uranium", 2, "Cadmium and boron absorb neutrons, so moving the rods controls the reaction rate.");
        insT(db, "Physics", "Particle Physics", "Fundamental Particles", "The quark composition of a proton is:", "u u d", "u d d", "u u u", "d d d", 0, "A proton is made of two up quarks and one down quark.");
        insT(db, "Physics", "Particle Physics", "Fundamental Particles", "Which of the following particles does NOT experience the strong nuclear force?", "proton", "neutron", "electron", "quark", 2, "Electrons are leptons; only quarks (and particles made of them) feel the strong force.");
        insT(db, "Physics", "Particle Physics", "Fundamental Particles", "The exchange (carrier) particle of the electromagnetic force is the:", "gluon", "photon", "W boson", "graviton", 1, "Electromagnetic interactions between charges are carried by photons.");
        insT(db, "Physics", "Particle Physics", "Fundamental Particles", "The antiparticle of the electron is the:", "proton", "neutron", "photon", "positron", 3, "The positron has the same mass as the electron but a positive charge.");
        insT(db, "Physics", "Particle Physics", "Forces and Conservation Laws", "The weakest of the four fundamental forces is the:", "strong nuclear force", "electromagnetic force", "weak nuclear force", "gravitational force", 3, "Gravity is by far the weakest force, though it acts over huge distances.");
        insT(db, "Physics", "Particle Physics", "Forces and Conservation Laws", "Beta decay of a nucleus takes place through the:", "strong force", "weak force", "gravitational force", "electromagnetic force", 1, "In beta decay a neutron changes into a proton; this is caused by the weak force.");
        insT(db, "Physics", "Particle Physics", "Forces and Conservation Laws", "The strong force between two quarks is transmitted by particles called:", "gluons", "photons", "W bosons", "neutrinos", 0, "Quarks exchange gluons, which bind them inside protons and neutrons.");
        insT(db, "Physics", "Particle Physics", "Forces and Conservation Laws", "Which quantity is conserved in ALL particle interactions?", "strangeness", "kinetic energy", "electric charge", "the number of particles", 2, "Total electric charge never changes in any interaction; strangeness is not conserved in weak decays.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Group Trends and Periodicity", "Down Group 1, the atomic radius of alkali metals increases mainly because:", "Nuclear charge decreases down the group", "A new electron shell is added in each period", "Shielding effect decreases down the group", "Atoms lose electrons more easily", 1, "Each element down the group has one extra electron shell, so the atoms get bigger.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Group Trends and Periodicity", "Which halogen is the most electronegative element?", "Chlorine", "Bromine", "Fluorine", "Iodine", 2, "Fluorine is the smallest halogen and attracts bonding electrons most strongly.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Group Trends and Periodicity", "Across Period 3 from sodium to chlorine, the first ionization energy generally:", "Decreases steadily", "Remains constant", "First decreases then increases", "Increases", 3, "Across a period nuclear charge increases and size decreases, so more energy is needed to remove an electron.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Group Trends and Periodicity", "Which element has the strongest metallic character?", "Lithium", "Potassium", "Sodium", "Beryllium", 1, "Metallic character increases down a group, and potassium is the lowest Group 1 element listed.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Important Compounds (Caustic Soda, Sulfuric Acid)", "Caustic soda (NaOH) is prepared industrially by:", "Heating sodium carbonate with lime", "Reaction of sodium with cold water only", "Electrolysis of brine", "Neutralizing HCl with sodium", 2, "In the chlor-alkali process, electrolysis of concentrated NaCl solution gives NaOH, Cl2 and H2.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Important Compounds (Caustic Soda, Sulfuric Acid)", "In the Contact process for H2SO4, the catalyst used to convert SO2 into SO3 is:", "Finely divided iron", "Platinum gauze", "Vanadium pentoxide (V2O5)", "Nickel powder", 2, "V2O5 catalyzes the oxidation of SO2 to SO3 in the Contact process.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Important Compounds (Caustic Soda, Sulfuric Acid)", "Concentrated sulfuric acid chars sugar and turns it black because it acts as a strong:", "Oxidizing agent only", "Dehydrating agent", "Reducing agent", "Catalyst", 1, "Concentrated H2SO4 removes the elements of water from sugar, leaving black carbon behind.");
        insT(db, "Chemistry", "Chemistry of Representative Elements", "Important Compounds (Caustic Soda, Sulfuric Acid)", "When a caustic soda solution is left exposed to air for a long time, it slowly forms sodium carbonate because NaOH:", "Evaporates quickly", "Absorbs CO2 from the air", "Reacts with nitrogen of air", "Decomposes into sodium metal", 1, "NaOH absorbs carbon dioxide from air: 2NaOH + CO2 gives Na2CO3 + H2O.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Properties of Transition Metals", "Transition metals show variable oxidation states because:", "Their atoms can lose all p electrons", "They have completely filled d orbitals", "Their (n-1)d and ns electrons have almost equal energy and both can take part in bonding", "They form only ionic compounds", 2, "The ns and (n-1)d electrons are close in energy, so different numbers of them can be used in bonding.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Properties of Transition Metals", "Zinc is not regarded as a true transition metal because:", "It has no d electrons at all", "It is a liquid at room temperature", "It does not form any compounds", "Its d subshell is completely filled (3d10) in the atom and in Zn2+ ion", 3, "A transition metal must have a partly filled d subshell, but zinc has a full 3d10 subshell.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Properties of Transition Metals", "Which of the following ions is colourless in aqueous solution?", "Cu2+", "Fe3+", "Cr3+", "Sc3+", 3, "Sc3+ has no d electrons (3d0), so no d-d transitions occur and it is colourless.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Properties of Transition Metals", "Fe3+ is more stable than Fe2+ mainly because Fe3+ has:", "A half-filled 3d5 configuration", "A completely filled 3d subshell", "More protons than Fe2+", "A larger atomic size", 0, "Half-filled subshells have extra stability, and Fe3+ has exactly five electrons in its 3d subshell.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Complex Compounds and Metallurgy", "The coordination number of copper in the complex [Cu(NH3)4]2+ is:", "2", "6", "4", "8", 2, "Four NH3 ligands are directly attached to the central Cu2+ ion, so the coordination number is 4.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Complex Compounds and Metallurgy", "Which ligand is bidentate (attaches through two donor atoms)?", "NH3", "Ethylenediamine (en)", "Cl-", "H2O", 1, "Ethylenediamine has two nitrogen donor atoms and attaches to the metal at two points.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Complex Compounds and Metallurgy", "Froth flotation process is used for the concentration of:", "Oxide ores", "Carbonate ores", "Chloride ores", "Sulfide ores", 3, "Sulfide ore particles are preferentially wetted by oil and rise with the froth, leaving gangue behind.");
        insT(db, "Chemistry", "Chemistry of Outer Transition (d-Block) Elements", "Complex Compounds and Metallurgy", "Roasting of an ore means:", "Heating the ore in the absence of air", "Dissolving the ore in a suitable acid", "Heating the ore strongly in the presence of excess air, below its melting point", "Melting the ore with a reducing agent", 2, "Roasting converts sulfide ores into oxides by heating them in excess air without melting.");
        insT(db, "Chemistry", "Organic Compounds", "Classification and Functional Groups", "The functional group present in ketones is:", "A carbonyl (C=O) group within the carbon chain", "-OH group", "-CHO group", "-COOH group", 0, "In ketones the carbonyl carbon is bonded to two other carbon atoms inside the chain.");
        insT(db, "Chemistry", "Organic Compounds", "Classification and Functional Groups", "Which compound contains an ester functional group?", "CH3COOCH3", "CH3COOH", "CH3CHO", "CH3OCH3", 0, "CH3COOCH3 (methyl ethanoate) has the -COO- linkage characteristic of esters.");
        insT(db, "Chemistry", "Organic Compounds", "Classification and Functional Groups", "Which of the following is an alicyclic compound?", "Benzene", "Cyclohexane", "Toluene", "Ethene", 1, "Cyclohexane is a ring compound with aliphatic properties, so it is alicyclic, not aromatic.");
        insT(db, "Chemistry", "Organic Compounds", "Classification and Functional Groups", "The compound CH3-NH2 belongs to the class of:", "Alcohols", "Amides", "Amines", "Nitriles", 2, "The -NH2 group is the amino group, so compounds containing it are called amines.");
        insT(db, "Chemistry", "Organic Compounds", "Isomerism", "Ethanol (CH3CH2OH) and dimethyl ether (CH3OCH3) are:", "Functional group isomers", "Chain isomers", "Position isomers", "Geometrical isomers", 0, "They have the same molecular formula C2H6O but different functional groups, alcohol and ether.");
        insT(db, "Chemistry", "Organic Compounds", "Isomerism", "Geometrical (cis-trans) isomerism is shown by:", "But-2-ene", "But-1-ene", "Propene", "Ethene", 0, "But-2-ene has different groups on each carbon of the C=C bond and rotation about the double bond is restricted.");
        insT(db, "Chemistry", "Organic Compounds", "Isomerism", "The number of structural isomers of C4H10 is:", "2", "1", "3", "4", 0, "C4H10 has two isomers: n-butane and 2-methylpropane (isobutane).");
        insT(db, "Chemistry", "Organic Compounds", "Isomerism", "n-Pentane and 2-methylbutane are examples of:", "Functional group isomerism", "Geometrical isomerism", "Chain isomerism", "Optical isomerism", 2, "They differ only in the arrangement (branching) of the carbon skeleton, so they are chain isomers.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "IUPAC Naming Rules", "In IUPAC nomenclature, the parent chain of an organic compound is selected as:", "The shortest continuous carbon chain", "Any chain containing oxygen", "The chain with the most hydrogen atoms", "The longest continuous carbon chain", 3, "The parent structure is always based on the longest continuous chain of carbon atoms.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "IUPAC Naming Rules", "The IUPAC suffix used for a carboxylic acid (-COOH group) is:", "-oic acid", "-al", "-one", "-ol", 0, "Carboxylic acids are named by adding the suffix -oic acid to the parent alkane name.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "IUPAC Naming Rules", "A compound contains both a -COOH group and an -OH group. Its name will be based on the:", "Alcohol group, with suffix -ol", "Carboxylic acid group, with suffix -oic acid", "Group that comes first alphabetically", "Group attached to the longest alkyl group", 1, "Carboxylic acid has higher priority than alcohol, so -OH is named as a hydroxy- prefix.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "IUPAC Naming Rules", "While numbering the parent chain, the direction is chosen so that:", "Substituents get the highest possible locants", "Numbering always starts from the right", "Alphabetical order of substituents is followed", "The multiple bond or principal functional group gets the lowest possible locant", 3, "Lowest locants are given to the principal functional group or multiple bond when numbering.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "Naming Hydrocarbons and Derivatives", "The IUPAC name of CH3-CH=CH-CH3 is:", "But-2-ene", "But-1-ene", "Butane", "2-Butyne", 0, "The four-carbon chain has a double bond starting at carbon 2, so it is but-2-ene.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "Naming Hydrocarbons and Derivatives", "The IUPAC name of CH3-CH(CH3)-CH2-CH3 is:", "Pentane", "3-Methylbutane", "2-Ethylpropane", "2-Methylbutane", 3, "The longest chain has 4 carbons with a methyl group on carbon 2: 2-methylbutane.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "Naming Hydrocarbons and Derivatives", "The IUPAC name of CH3COOH is:", "Methanoic acid", "Ethanoic acid", "Propanoic acid", "Ethanal", 1, "CH3COOH has two carbons including the -COOH carbon, so it is ethanoic acid.");
        insT(db, "Chemistry", "Nomenclature of Organic Compounds", "Naming Hydrocarbons and Derivatives", "The IUPAC name of CH3-CH2-Cl is:", "Chloroethane", "Chloromethane", "Chloroethene", "Dichloroethane", 0, "A chlorine substituent on a two-carbon chain gives the name chloroethane.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkanes and Alkenes", "The general formula of alkanes is:", "CnH2n", "CnH2n-2", "CnH2n+2", "CnHn", 2, "Saturated open-chain hydrocarbons (alkanes) follow the general formula CnH2n+2.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkanes and Alkenes", "Alkenes decolorize bromine water because they:", "Undergo addition across the C=C double bond", "Undergo substitution reactions", "Are highly soluble in water", "Release hydrogen gas", 0, "Bromine adds across the double bond to form a colourless dibromo compound; this is a test for unsaturation.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkanes and Alkenes", "When HBr adds to propene according to Markovnikov rule, the major product is:", "1-Bromopropane", "1,2-Dibromopropane", "2-Bromopropane", "Propane", 2, "The hydrogen of HBr attaches to the carbon already having more hydrogens, giving 2-bromopropane.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkanes and Alkenes", "Methane reacts with chlorine in sunlight by a:", "Addition reaction", "Polymerization reaction", "Free radical substitution reaction", "Neutralization reaction", 2, "In sunlight, chlorine replaces hydrogen atoms of methane one by one through a free radical mechanism.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkynes and Benzene", "Ethyne reacts with water in the presence of HgSO4 and dilute H2SO4 to form:", "Ethanol", "Ethanal (acetaldehyde)", "Ethanoic acid", "Methane", 1, "Hydration of ethyne first gives an unstable enol which rearranges to ethanal.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkynes and Benzene", "Benzene undergoes electrophilic substitution rather than addition mainly because:", "It has no pi electrons", "Substitution preserves its delocalized aromatic pi system and stability", "It cannot react with electrophiles", "Its carbon atoms are sp3 hybridized", 1, "Addition would destroy the stable delocalized ring system, so benzene prefers substitution.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkynes and Benzene", "In the nitration of benzene, the attacking electrophile is:", "NO2- ion", "HNO3 molecule", "NO+ ion", "NO2+ (nitronium) ion", 3, "The mixture of conc. HNO3 and conc. H2SO4 generates the nitronium ion NO2+, which attacks the ring.");
        insT(db, "Chemistry", "Hydrocarbons", "Alkynes and Benzene", "In the laboratory, ethyne (acetylene) is prepared by the action of water on:", "Calcium carbonate", "Calcium hydroxide", "Calcium carbide", "Calcium sulfate", 2, "CaC2 + 2H2O gives C2H2 gas and Ca(OH)2; this is the standard laboratory preparation of ethyne.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Reactions of Alkyl Halides (SN1, SN2)", "The rate of an SN2 reaction depends on the concentration of:", "Alkyl halide only", "Nucleophile only", "Solvent only", "Both alkyl halide and nucleophile", 3, "SN2 is bimolecular: both the substrate and the nucleophile take part in the rate-determining step.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Reactions of Alkyl Halides (SN1, SN2)", "Tertiary alkyl halides react mainly by the SN1 mechanism because:", "They form the most stable carbocation", "They have no carbon-hydrogen bonds", "They are insoluble in water", "Their halogen is very tightly bound", 0, "SN1 goes through a carbocation, and a tertiary carbocation is stabilized by three alkyl groups.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Reactions of Alkyl Halides (SN1, SN2)", "In an SN2 reaction, the nucleophile attacks the carbon from the back side, which causes:", "Inversion of configuration (Walden inversion)", "Retention of configuration", "Formation of a carbocation", "Elimination of HX", 0, "Back-side attack flips the arrangement of groups, producing inversion of configuration.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Reactions of Alkyl Halides (SN1, SN2)", "Which alkyl halide reacts fastest in an SN2 reaction?", "Methyl bromide", "tert-Butyl bromide", "Isopropyl bromide", "Ethyl bromide", 0, "Methyl halides have the least steric hindrance, so the nucleophile attacks them most easily in SN2.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Amines and Grignard Reagent", "A Grignard reagent (CH3MgBr) is prepared by reacting an alkyl halide with magnesium in:", "Water", "Dilute HCl", "Ethanol", "Dry ether", 3, "Grignard reagents are destroyed by moisture, so they are prepared and kept in completely dry ether.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Amines and Grignard Reagent", "When CH3MgBr reacts with CO2 and the product is hydrolyzed, the final product is:", "Methane", "Methanol", "Ethane", "Ethanoic acid", 3, "Grignard reagent adds to CO2 to give a carboxylate salt, which on hydrolysis yields ethanoic acid.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Amines and Grignard Reagent", "Amines behave as bases because the nitrogen atom:", "Has a lone pair of electrons that can accept a proton", "Contains a pi bond", "Loses a hydrogen atom easily", "Is more electronegative than oxygen", 0, "The lone pair on nitrogen accepts H+ from acids, which is why amines are basic.");
        insT(db, "Chemistry", "Alkyl Halides and Amines", "Amines and Grignard Reagent", "Aniline is a weaker base than ethylamine because in aniline:", "Nitrogen has no lone pair", "The benzene ring donates a proton", "The lone pair of nitrogen is delocalized into the benzene ring", "Nitrogen is sp3 hybridized", 2, "Delocalization spreads the lone pair into the ring, making it less available for accepting a proton.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Alcohols and Their Reactions", "When ethanol is heated with concentrated H2SO4 at about 170 degrees C, the main product is:", "Diethyl ether", "Ethanoic acid", "Ethene", "Ethane", 2, "At 170 degrees C the alcohol is dehydrated and loses water to form ethene.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Alcohols and Their Reactions", "In the Lucas test, a tertiary alcohol reacts with Lucas reagent (conc. HCl + ZnCl2) and shows turbidity:", "After one hour", "Immediately", "Only on heating", "Never", 1, "Tertiary alcohols form their chlorides at once, so the mixture turns turbid immediately.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Alcohols and Their Reactions", "Ethanol reacts with sodium metal to produce:", "Sodium ethoxide and hydrogen gas", "Sodium hydroxide and ethane", "Sodium ethanoate and water", "No reaction at all", 0, "Like water, alcohols react with sodium to give an alkoxide and hydrogen: 2C2H5OH + 2Na gives 2C2H5ONa + H2.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Alcohols and Their Reactions", "Ethanol and ethanoic acid react in the presence of concentrated H2SO4 to form:", "An ether and water", "A ketone and hydrogen", "An aldehyde and water", "An ester (ethyl ethanoate) and water", 3, "This reversible reaction is called esterification and gives a sweet-smelling ester.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Phenols and Ethers", "Phenol is more acidic than ethanol because the phenoxide ion formed after losing H+ is:", "Smaller in size than ethoxide", "A stronger base than ethoxide", "Stabilized by resonance with the benzene ring", "Insoluble in water", 2, "The negative charge on the phenoxide ion is delocalized over the ring, which stabilizes it.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Phenols and Ethers", "Phenol reacts with bromine water to give a white precipitate of:", "2,4,6-Tribromophenol", "Bromobenzene", "Picric acid", "Phenyl bromide", 0, "The -OH group strongly activates the ring, so all ortho and para positions are brominated at once.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Phenols and Ethers", "In Williamson synthesis, an ether is prepared by reacting:", "An alcohol with concentrated H2SO4 at 170 degrees C", "A phenol with bromine water", "An alkyl halide with a sodium alkoxide", "Two molecules of a carboxylic acid", 2, "RO-Na + R-X gives R-O-R plus NaX; this is the general laboratory method for ethers.");
        insT(db, "Chemistry", "Alcohols, Phenols and Ethers", "Phenols and Ethers", "Phenol does not liberate CO2 gas from sodium bicarbonate solution because phenol is:", "Insoluble in water", "A stronger acid than carbonic acid", "Weaker than carbonic acid", "Not an acid at all", 2, "Only acids stronger than carbonic acid release CO2 from NaHCO3; phenol is weaker, so no effervescence occurs.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Aldehydes vs Ketones", "Tollens reagent (ammoniacal silver nitrate) gives a silver mirror with:", "Ketones only", "Both aldehydes and ketones", "Alcohols", "Aldehydes only", 3, "Aldehydes are mild reducing agents and reduce Tollens reagent to metallic silver; ketones do not react.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Aldehydes vs Ketones", "On warming with Fehling solution, an aldehyde produces a red precipitate of:", "Cu2O", "Cu metal", "CuO", "Cu(OH)2", 0, "The aldehyde reduces blue Cu2+ ions of Fehling solution to red copper(I) oxide, Cu2O.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Aldehydes vs Ketones", "Ketones resist oxidation by mild oxidizing agents because:", "Their carbonyl carbon carries a hydrogen atom", "They contain a -CHO group", "They have no hydrogen atom attached to the carbonyl carbon", "They are always gases", 2, "Oxidation of a ketone would require breaking a carbon-carbon bond, so mild oxidants do not attack it.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Aldehydes vs Ketones", "Which of the following compounds gives a positive iodoform test?", "Formaldehyde (HCHO)", "Acetaldehyde (CH3CHO)", "Propanal (CH3CH2CHO)", "Benzaldehyde (C6H5CHO)", 1, "The iodoform test is given by compounds containing the CH3-CO- group, which acetaldehyde possesses.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Reactions of Carbonyl Compounds", "Addition of HCN to an aldehyde or ketone produces a:", "Cyanohydrin", "Oxime", "Acetal", "Hydrazone", 0, "The CN- ion adds to the carbonyl carbon and H+ to oxygen, forming a cyanohydrin.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Reactions of Carbonyl Compounds", "Reduction of propanone (acetone) with LiAlH4 gives:", "Propane", "Propan-1-ol", "Propan-2-ol", "Propanoic acid", 2, "Reduction of a ketone converts the carbonyl group into a secondary alcohol group.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Reactions of Carbonyl Compounds", "The Cannizzaro reaction is shown by aldehydes which:", "Contain alpha-hydrogen atoms", "Contain a benzene ring only", "Have no alpha-hydrogen atoms, e.g. formaldehyde", "Are soluble in water", 2, "With concentrated KOH, aldehydes without alpha-hydrogen undergo self oxidation-reduction: one molecule is oxidized and another reduced.");
        insT(db, "Chemistry", "Carbonyl Compounds I", "Reactions of Carbonyl Compounds", "Which aldehyde can undergo aldol condensation with dilute NaOH?", "CH3CHO", "HCHO", "C6H5CHO", "(CH3)3C-CHO", 0, "Aldol condensation needs an alpha-hydrogen atom; only acetaldehyde among these has one.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Carboxylic Acids", "A carboxylic acid liberates CO2 gas with effervescence when treated with:", "NaOH solution", "NaHCO3 solution", "NaCl solution", "CaCl2 solution", 1, "Carboxylic acids are stronger than carbonic acid, so they release CO2 from sodium bicarbonate.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Carboxylic Acids", "When sodium ethanoate is heated with soda lime (NaOH + CaO), the gas produced is:", "Ethane", "CO2", "Hydrogen", "Methane", 3, "This is decarboxylation: the -COONa group is removed as Na2CO3 and methane is formed.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Carboxylic Acids", "Which is the strongest acid among the following?", "Ethanoic acid", "Propanoic acid", "Methanoic (formic) acid", "Butanoic acid", 2, "Alkyl groups push electrons and weaken the acid; formic acid has no alkyl group, so it is the strongest of these.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Carboxylic Acids", "Carboxylic acids have higher boiling points than alcohols of similar molar mass mainly because they:", "Are ionic compounds", "Form dimers through strong hydrogen bonding", "Contain a C=C double bond", "React with sodium metal", 1, "Two acid molecules join through two hydrogen bonds to form a stable dimer, which needs more heat to break apart.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Esters, Amides and Derivatives", "Among acid derivatives, the most reactive towards nucleophiles is:", "Amide", "Ester", "Acid anhydride", "Acid chloride", 3, "The reactivity order is acid chloride greater than anhydride, greater than ester, greater than amide.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Esters, Amides and Derivatives", "When an ester is boiled with sodium hydroxide solution, the products are:", "A carboxylic acid and an alcohol", "An aldehyde and water", "A sodium carboxylate (soap-like salt) and an alcohol", "A ketone and CO2", 2, "Alkaline hydrolysis of an ester is called saponification and gives the salt of the acid plus an alcohol.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Esters, Amides and Derivatives", "Which of the following is an amide?", "CH3COOCH3", "CH3COCl", "(CH3CO)2O", "CH3CONH2", 3, "CH3CONH2 (ethanamide) contains the -CONH2 group, which is the amide group.");
        insT(db, "Chemistry", "Carbonyl Compounds II", "Esters, Amides and Derivatives", "Fischer esterification is the reaction between:", "An alcohol and an alkyl halide", "A carboxylic acid and an alcohol in the presence of conc. H2SO4", "An ester and water alone", "An amide and an acid chloride", 1, "The acid catalyst removes water and an ester is formed in this reversible reaction.");
        insT(db, "Chemistry", "Biochemistry", "Carbohydrates and Proteins", "Glucose is classified as an:", "Aldohexose", "Ketohexose", "Aldopentose", "Disaccharide", 0, "Glucose has six carbon atoms and an aldehyde group, so it is an aldohexose monosaccharide.");
        insT(db, "Chemistry", "Biochemistry", "Carbohydrates and Proteins", "On hydrolysis, one molecule of sucrose gives:", "Glucose and fructose", "Two molecules of glucose", "Glucose and galactose", "Two molecules of fructose", 0, "Sucrose is a disaccharide made of one glucose unit and one fructose unit.");
        insT(db, "Chemistry", "Biochemistry", "Carbohydrates and Proteins", "In proteins, amino acid units are joined together by:", "Glycosidic bonds", "Hydrogen bonds only", "Ester bonds", "Peptide bonds", 3, "A peptide bond (-CO-NH-) forms between the -COOH of one amino acid and the -NH2 of another, with loss of water.");
        insT(db, "Chemistry", "Biochemistry", "Carbohydrates and Proteins", "When egg white is heated, the protein is denatured. During denaturation:", "All peptide bonds are broken", "The secondary and tertiary structures are disrupted but peptide bonds stay intact", "Only the primary structure is destroyed", "The protein becomes a carbohydrate", 1, "Denaturation unfolds the protein and destroys its shape, but the amino acid sequence (primary structure) remains unchanged.");
        insT(db, "Chemistry", "Biochemistry", "Lipids, Vitamins and Minerals", "Fats and oils are chemically:", "Polymers of glucose", "Esters of glycerol with fatty acids", "Salts of carboxylic acids", "Ethers of glycerol", 1, "Each fat molecule is a triglyceride: glycerol combined with three fatty acid molecules through ester links.");
        insT(db, "Chemistry", "Biochemistry", "Lipids, Vitamins and Minerals", "Which vitamin is water-soluble and its deficiency causes scurvy?", "Vitamin A", "Vitamin C", "Vitamin D", "Vitamin K", 1, "Vitamin C (ascorbic acid) dissolves in water, and its lack in diet causes bleeding gums and scurvy.");
        insT(db, "Chemistry", "Biochemistry", "Lipids, Vitamins and Minerals", "Goiter (swelling of the thyroid gland) is caused by the deficiency of:", "Iodine", "Iron", "Calcium", "Fluorine", 0, "The thyroid gland needs iodine to make its hormones; iodine deficiency enlarges the gland.");
        insT(db, "Chemistry", "Biochemistry", "Lipids, Vitamins and Minerals", "Unsaturated fats differ from saturated fats because they:", "Contain only single bonds", "Contain one or more C=C double bonds and are usually liquids at room temperature", "Have no carbon atoms", "Are always solids at room temperature", 1, "Double bonds in the fatty acid chains lower the melting point, so unsaturated fats are oils.");
        insT(db, "Chemistry", "Industrial Chemistry", "Fertilizers, Polymers and Fibers", "Urea is a widely used fertilizer because it contains about:", "20 percent nitrogen", "60 percent nitrogen", "10 percent nitrogen", "46 percent nitrogen", 3, "Urea has the highest nitrogen content (about 46 percent) among common solid nitrogen fertilizers.");
        insT(db, "Chemistry", "Industrial Chemistry", "Fertilizers, Polymers and Fibers", "An NPK fertilizer supplies plants with the three essential elements:", "Nitrogen, potassium and calcium", "Phosphorus, sulfur and iron", "Nitrogen, carbon and oxygen", "Nitrogen, phosphorus and potassium", 3, "N, P and K are the primary nutrients that crops need in the largest amounts.");
        insT(db, "Chemistry", "Industrial Chemistry", "Fertilizers, Polymers and Fibers", "PVC is the polymer obtained from the monomer:", "Ethene", "Chloroethene (vinyl chloride)", "Styrene", "Tetrafluoroethene", 1, "Polyvinyl chloride is made by addition polymerization of vinyl chloride molecules.");
        insT(db, "Chemistry", "Industrial Chemistry", "Fertilizers, Polymers and Fibers", "Which of the following is a synthetic polyamide fiber?", "Nylon", "Cotton", "Wool", "Silk", 0, "Nylon is a man-made polyamide; cotton, wool and silk are natural fibers.");
        insT(db, "Chemistry", "Industrial Chemistry", "Cosmetics, Adhesives and Pesticides", "DDT belongs to the class of pesticides called:", "Herbicides", "Insecticides", "Fungicides", "Rodenticides", 1, "DDT was widely used to kill insects such as mosquitoes, so it is an insecticide.");
        insT(db, "Chemistry", "Industrial Chemistry", "Cosmetics, Adhesives and Pesticides", "A chemical substance used to kill unwanted weeds in crops is called a:", "Fungicide", "Insecticide", "Fertilizer", "Herbicide", 3, "Herbicides selectively destroy weeds without harming the crop plants.");
        insT(db, "Chemistry", "Industrial Chemistry", "Cosmetics, Adhesives and Pesticides", "The main ingredient of talcum powder is talc, which is chemically:", "Calcium carbonate", "Sodium bicarbonate", "Zinc oxide", "Hydrated magnesium silicate", 3, "Talc is a soft mineral, hydrated magnesium silicate, which absorbs moisture and reduces friction on skin.");
        insT(db, "Chemistry", "Industrial Chemistry", "Cosmetics, Adhesives and Pesticides", "Which of the following materials is used as a strong adhesive?", "Urea", "Epoxy resin", "DDT", "PVC powder", 1, "Epoxy resins form very strong bonds on setting and are used as adhesives for metals, wood and plastics.");
        insT(db, "Chemistry", "Environmental Chemistry", "Air Pollution and Ozone", "The ozone layer in the stratosphere is destroyed mainly by:", "Chlorofluorocarbons (CFCs)", "CO2 from vehicles", "Water vapours", "Nitrogen gas", 0, "CFCs release chlorine atoms in the stratosphere, and each chlorine atom destroys many ozone molecules.");
        insT(db, "Chemistry", "Environmental Chemistry", "Air Pollution and Ozone", "Acid rain is caused mainly by the presence of which gases in polluted air?", "CO and CH4", "O2 and N2", "SO2 and oxides of nitrogen (NOx)", "H2 and He", 2, "SO2 and NOx dissolve in rain water to form sulfuric and nitric acids, making the rain acidic.");
        insT(db, "Chemistry", "Environmental Chemistry", "Air Pollution and Ozone", "Carbon monoxide is a poisonous gas because it:", "Burns the skin on contact", "Destroys the ozone layer directly", "Turns into CO2 inside the lungs", "Binds with haemoglobin much more strongly than oxygen and stops oxygen transport", 3, "CO forms a stable compound with haemoglobin, so the blood cannot carry oxygen to body cells.");
        insT(db, "Chemistry", "Environmental Chemistry", "Air Pollution and Ozone", "The gas mainly responsible for the greenhouse effect and global warming is:", "Oxygen", "Nitrogen", "Argon", "Carbon dioxide", 3, "CO2 absorbs the infrared radiation re-emitted by the Earth and traps heat in the atmosphere.");
        insT(db, "Chemistry", "Environmental Chemistry", "Water Pollution and Green Chemistry", "Eutrophication of a lake is caused by:", "Addition of chlorine to water", "Excess of nitrates and phosphates from fertilizers, causing algal bloom and oxygen depletion", "Presence of sand in water", "Decrease of minerals in water", 1, "Extra nutrients make algae grow fast; when they die and decay, dissolved oxygen is used up and fish die.");
        insT(db, "Chemistry", "Environmental Chemistry", "Water Pollution and Green Chemistry", "The pollution level of water is commonly measured by BOD, which means:", "Basic Oxygen Density", "Balanced Oxygen Distribution", "Biological Oxygen Demand - oxygen needed by microbes to decompose organic matter", "Biochemical Oxide Determination", 2, "A higher BOD value means more organic pollution, because microbes need more oxygen to break it down.");
        insT(db, "Chemistry", "Environmental Chemistry", "Water Pollution and Green Chemistry", "The main aim of green chemistry is to:", "Increase the yield of every reaction at any cost", "Design chemical products and processes that reduce or eliminate hazardous substances", "Replace all natural products with synthetic ones", "Use more fossil fuels efficiently", 1, "Green chemistry prevents pollution at the source by making reactions cleaner and safer.");
        insT(db, "Chemistry", "Environmental Chemistry", "Water Pollution and Green Chemistry", "Chlorination of drinking water is carried out in treatment plants in order to:", "Kill harmful bacteria and germs", "Remove hardness of water", "Improve the taste of water", "Increase the oxygen content", 0, "Chlorine is a disinfectant; it destroys disease-causing microorganisms in water.");
        insT(db, "Chemistry", "Spectroscopy", "UV-Visible and IR Spectroscopy", "Infrared (IR) spectroscopy is most commonly used to:", "Measure the molar mass of a compound", "Identify the functional groups present in a molecule", "Count the number of protons in a molecule", "Determine the melting point of a solid", 1, "Different bonds absorb IR radiation at characteristic frequencies, revealing the functional groups.");
        insT(db, "Chemistry", "Spectroscopy", "UV-Visible and IR Spectroscopy", "In an IR spectrum, a strong absorption band near 1700 cm-1 indicates the presence of:", "A carbonyl (C=O) group", "An O-H group", "A C=C double bond", "An N-H group", 0, "The C=O stretching vibration gives a strong band in the 1680-1750 cm-1 region.");
        insT(db, "Chemistry", "Spectroscopy", "UV-Visible and IR Spectroscopy", "Absorption in the ultraviolet-visible region takes place when radiation causes:", "Vibration of chemical bonds", "Rotation of whole molecules", "Breaking of the nucleus", "Excitation of electrons to higher energy levels", 3, "UV-visible photons have enough energy to promote electrons, especially pi electrons, to higher orbitals.");
        insT(db, "Chemistry", "Spectroscopy", "UV-Visible and IR Spectroscopy", "A broad IR absorption band in the region 3200-3600 cm-1 is characteristic of:", "C=O group", "C-H group of alkanes", "C-Cl group", "O-H group of alcohols and phenols", 3, "Hydrogen-bonded O-H groups show a strong, broad stretching band around 3200-3600 cm-1.");
        insT(db, "Chemistry", "Spectroscopy", "NMR and Mass Spectroscopy", "In 1H NMR spectroscopy, the reference standard compound used (signal at 0 ppm) is:", "Chloroform", "Benzene", "Tetramethylsilane (TMS)", "Water", 2, "TMS gives a single sharp signal away from most proton signals and is taken as 0 ppm.");
        insT(db, "Chemistry", "Spectroscopy", "NMR and Mass Spectroscopy", "The number of signals in the 1H NMR spectrum of ethanol (CH3-CH2-OH) is:", "1", "2", "5", "3", 3, "Ethanol has three types of protons - CH3, CH2 and OH - so it gives three signals.");
        insT(db, "Chemistry", "Spectroscopy", "NMR and Mass Spectroscopy", "In the 1H NMR spectrum of ethanol, the CH3 signal appears as a triplet because:", "It has three hydrogen atoms", "The OH group splits it into three", "The neighbouring CH2 group has two protons and splitting follows the n+1 rule", "It contains three carbon atoms", 2, "A signal is split into n+1 peaks, where n is the number of protons on the adjacent carbon (2 + 1 = 3).");
        insT(db, "Chemistry", "Spectroscopy", "NMR and Mass Spectroscopy", "A mass spectrometer is used to determine the:", "Colour of a compound", "Relative molar mass and structure fragments of a compound", "Boiling point of a liquid", "pH of a solution", 1, "The molecular ion peak gives the molar mass, and the fragmentation pattern helps in finding the structure.");
        insT(db, "English", "Tenses", "Present and Past Tenses", "She _____ to school by bus every day.", "go", "goes", "went", "going", 1, "With he/she/it in the simple present tense, the verb takes -s or -es.");
        insT(db, "English", "Tenses", "Present and Past Tenses", "They _____ a cricket match yesterday.", "played", "play", "plays", "playing", 0, "The word yesterday shows past time, so the simple past tense is used.");
        insT(db, "English", "Tenses", "Present and Past Tenses", "Look! The baby _____ now.", "cries", "cried", "is crying", "cry", 2, "Look and now show an action happening at this moment, so present continuous is used.");
        insT(db, "English", "Tenses", "Present and Past Tenses", "He _____ his homework before dinner last night.", "finish", "finishes", "finishing", "finished", 3, "Last night shows a finished past action, so the simple past tense is used.");
        insT(db, "English", "Tenses", "Future and Perfect Tenses", "We _____ our exams by next June.", "will finish", "will have finished", "finished", "are finish", 1, "By next June shows completion before a future time, so future perfect is used.");
        insT(db, "English", "Tenses", "Future and Perfect Tenses", "I _____ this book already.", "have read", "reads", "will read", "reading", 0, "Already with a completed action takes the present perfect tense.");
        insT(db, "English", "Tenses", "Future and Perfect Tenses", "She _____ in Karachi since 2019.", "lives", "lived", "has lived", "will live", 2, "Since 2019 shows an action continuing from the past, so present perfect is used.");
        insT(db, "English", "Tenses", "Future and Perfect Tenses", "The train _____ before we reached the station.", "had left", "leaves", "left", "will leave", 0, "The train left before another past action, so the past perfect tense is used.");
        insT(db, "English", "Active and Passive Voice", "Passive of Simple Tenses", "Change into passive voice: The teacher checks the notebooks daily.", "The notebooks were checked by the teacher daily.", "The notebooks are checked by the teacher daily.", "The notebooks is checked by the teacher daily.", "The notebooks have checked by the teacher daily.", 1, "Simple present passive uses is/are + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Simple Tenses", "Change into passive voice: Ali wrote a letter.", "A letter is written by Ali.", "A letter wrote by Ali.", "A letter was written by Ali.", "A letter has written by Ali.", 2, "Simple past passive uses was/were + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Simple Tenses", "Change into passive voice: They will build a new bridge here.", "A new bridge will be built here.", "A new bridge will built here.", "A new bridge is being built here.", "A new bridge built here.", 0, "Simple future passive uses will be + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Simple Tenses", "Change into passive voice: The cat chased the mouse.", "The mouse is chased by the cat.", "The mouse has been chased by the cat.", "The mouse chased by the cat.", "The mouse was chased by the cat.", 3, "Simple past passive uses was + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Perfect and Modal Verbs", "Change into passive voice: She has completed the task.", "The task has completed by her.", "The task has been completed by her.", "The task was completed by her.", "The task is completed by her.", 1, "Present perfect passive uses has/have been + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Perfect and Modal Verbs", "Change into passive voice: You must obey the rules.", "The rules must obeyed.", "The rules must be obey.", "The rules must be obeyed.", "The rules are must be obeyed.", 2, "Modal passive uses modal + be + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Perfect and Modal Verbs", "Change into passive voice: They had finished the work.", "The work had been finished by them.", "The work has been finished by them.", "The work had finished by them.", "The work was being finished by them.", 0, "Past perfect passive uses had been + past participle.");
        insT(db, "English", "Active and Passive Voice", "Passive of Perfect and Modal Verbs", "Change into passive voice: We can solve this problem.", "This problem can solved by us.", "This problem could be solved by us.", "This problem is solved by us.", "This problem can be solved by us.", 3, "With can, the passive form is can be + past participle.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Statements", "Change into indirect speech: He said, \"I am busy today.\"", "He said that he is busy today.", "He said that he was busy that day.", "He said that I am busy that day.", "He said that he had busy that day.", 1, "In indirect speech the present tense changes to past and today becomes that day.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Statements", "Change into indirect speech: She said, \"I bought a new dress yesterday.\"", "She said that she buys a new dress the day before.", "She said that I had bought a new dress the day before.", "She said that she had bought a new dress the day before.", "She said that she will buy a new dress the day before.", 2, "Simple past changes to past perfect and yesterday becomes the day before.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Statements", "Change into indirect speech: Ahmed said, \"I will help you tomorrow.\"", "Ahmed said that he would help me the next day.", "Ahmed said that he will help me tomorrow.", "Ahmed said that I would help you tomorrow.", "Ahmed said that he helps me the next day.", 0, "Will changes to would and tomorrow becomes the next day.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Statements", "Change into indirect speech: The teacher said, \"The earth moves round the sun.\"", "The teacher said that the earth moved round the sun.", "The teacher said that the earth will move round the sun.", "The teacher said that the earth had moved round the sun.", "The teacher said that the earth moves round the sun.", 3, "A universal truth does not change its tense in indirect speech.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Questions and Commands", "Change into indirect speech: He asked me, \"Where do you live?\"", "He asked me where do I live.", "He asked me that where I lived.", "He asked me where I lived.", "He asked me where I live.", 2, "In indirect questions, statement order is used and the tense moves back.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Questions and Commands", "Change into indirect speech: The policeman said to the driver, \"Stop the car.\"", "The policeman ordered the driver to stop the car.", "The policeman ordered the driver that stop the car.", "The policeman said the driver to stop the car.", "The policeman ordered the driver stopped the car.", 0, "A command is reported with ordered + to + verb.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Questions and Commands", "Change into indirect speech: She said to me, \"Please give me a glass of water.\"", "She requested me give her a glass of water.", "She requested me that give her a glass of water.", "She requested me to gave her a glass of water.", "She requested me to give her a glass of water.", 3, "A polite request with please is reported with requested + to + verb.");
        insT(db, "English", "Direct and Indirect Speech", "Narration of Questions and Commands", "Change into indirect speech: Ali asked, \"Have you finished your work?\"", "Ali asked if I have finished my work.", "Ali asked if I had finished my work.", "Ali asked if had I finished my work.", "Ali asked if you had finished your work.", 1, "A yes/no question is reported with if, and present perfect changes to past perfect.");
        insT(db, "English", "Prepositions", "Prepositions of Time and Place", "The classes start _____ 8 o'clock in the morning.", "at", "on", "in", "by", 0, "At is used for clock time.");
        insT(db, "English", "Prepositions", "Prepositions of Time and Place", "He has been waiting here _____ two hours.", "since", "from", "by", "for", 3, "For is used for a period of time; since is used for a starting point.");
        insT(db, "English", "Prepositions", "Prepositions of Time and Place", "The book is lying _____ the table.", "in", "on", "at", "into", 1, "On is used for a surface.");
        insT(db, "English", "Prepositions", "Prepositions of Time and Place", "His birthday falls _____ Monday this year.", "in", "at", "on", "by", 2, "On is used before days and dates.");
        insT(db, "English", "Prepositions", "Prepositions with Verbs and Adjectives", "You should listen _____ your parents.", "with", "for", "to", "on", 2, "The verb listen is followed by to.");
        insT(db, "English", "Prepositions", "Prepositions with Verbs and Adjectives", "She is interested _____ learning English.", "on", "in", "at", "for", 1, "The adjective interested takes in.");
        insT(db, "English", "Prepositions", "Prepositions with Verbs and Adjectives", "He was accused _____ theft.", "for", "from", "with", "of", 3, "Accused is followed by of.");
        insT(db, "English", "Prepositions", "Prepositions with Verbs and Adjectives", "The teacher is angry _____ the lazy students.", "with", "upon", "over", "from", 0, "Angry is followed by with for a person.");
        insT(db, "English", "Articles and Determiners", "A, An, The", "He is _____ honest man.", "a", "an", "the", "no article", 1, "Honest begins with a vowel sound, so an is used.");
        insT(db, "English", "Articles and Determiners", "A, An, The", "_____ sun rises in the east.", "a", "an", "no article", "the", 3, "Unique things like the sun take the.");
        insT(db, "English", "Articles and Determiners", "A, An, The", "She plays _____ piano very well.", "the", "a", "an", "no article", 0, "Musical instruments take the after the verb play.");
        insT(db, "English", "Articles and Determiners", "A, An, The", "He bought _____ umbrella from the market.", "a", "the", "an", "no article", 2, "Umbrella begins with a vowel sound, so an is used.");
        insT(db, "English", "Articles and Determiners", "Determiners and Quantifiers", "There is not _____ milk in the jug.", "many", "few", "a few", "much", 3, "Much is used with uncountable nouns like milk.");
        insT(db, "English", "Articles and Determiners", "Determiners and Quantifiers", "Only _____ students passed the test; the rest failed.", "a few", "much", "a little", "any", 0, "A few is used with countable nouns and means a small number.");
        insT(db, "English", "Articles and Determiners", "Determiners and Quantifiers", "There are _____ books on the shelf.", "much", "a little", "several", "any", 2, "Several is used with countable plural nouns.");
        insT(db, "English", "Articles and Determiners", "Determiners and Quantifiers", "I have very _____ money left in my pocket.", "few", "little", "many", "a few", 1, "Little is used with uncountable nouns like money.");
        insT(db, "English", "Synonyms and Antonyms", "Common Synonyms", "The synonym of begin is:", "start", "stop", "end", "close", 0, "Begin and start have the same meaning.");
        insT(db, "English", "Synonyms and Antonyms", "Common Synonyms", "The synonym of brave is:", "afraid", "weak", "courageous", "timid", 2, "A brave person is courageous.");
        insT(db, "English", "Synonyms and Antonyms", "Common Synonyms", "The synonym of rapid is:", "slow", "late", "gentle", "swift", 3, "Rapid means very fast, like swift.");
        insT(db, "English", "Synonyms and Antonyms", "Common Synonyms", "The synonym of ancient is:", "new", "old", "fresh", "modern", 1, "Ancient means very old.");
        insT(db, "English", "Synonyms and Antonyms", "Common Antonyms", "The antonym of victory is:", "win", "success", "defeat", "honour", 2, "Defeat is the opposite of victory.");
        insT(db, "English", "Synonyms and Antonyms", "Common Antonyms", "The antonym of expand is:", "grow", "contract", "extend", "enlarge", 1, "Contract means to become smaller, the opposite of expand.");
        insT(db, "English", "Synonyms and Antonyms", "Common Antonyms", "The antonym of transparent is:", "opaque", "clear", "thin", "bright", 0, "Opaque means not letting light through, the opposite of transparent.");
        insT(db, "English", "Synonyms and Antonyms", "Common Antonyms", "The antonym of generous is:", "kind", "rich", "helpful", "selfish", 3, "A generous person gives freely; a selfish person does not.");
        insT(db, "English", "Sentence Correction", "Subject-Verb Agreement", "Choose the correct sentence:", "The list of items are on the table.", "The list of items is on the table.", "The list of items were on the table.", "The list of items have on the table.", 1, "The subject list is singular, so it takes is.");
        insT(db, "English", "Sentence Correction", "Subject-Verb Agreement", "Choose the correct sentence:", "Neither Ali nor his friends was present.", "Neither Ali nor his friends is present.", "Neither Ali nor his friends has present.", "Neither Ali nor his friends were present.", 3, "With neither...nor, the verb agrees with the nearer subject, friends, so were is used.");
        insT(db, "English", "Sentence Correction", "Subject-Verb Agreement", "Choose the correct sentence:", "Each of the girls have a book.", "Each of the girls are having a book.", "Each of the girls has a book.", "Each of the girls have books.", 2, "Each is singular, so it takes has.");
        insT(db, "English", "Sentence Correction", "Subject-Verb Agreement", "Choose the correct sentence:", "The news is very shocking.", "The news are very shocking.", "The news were very shocking.", "The news have very shocking.", 0, "News looks plural but is singular, so it takes is.");
        insT(db, "English", "Sentence Correction", "Common Errors (Pronouns, Adjectives, Adverbs)", "Choose the correct sentence:", "Between you and I, this is wrong.", "Between you and he, this is wrong.", "Between you and she, this is wrong.", "Between you and me, this is wrong.", 3, "After a preposition like between, the object pronoun me is used.");
        insT(db, "English", "Sentence Correction", "Common Errors (Pronouns, Adjectives, Adverbs)", "Choose the correct sentence:", "She is taller than her sister.", "She is more taller than her sister.", "She is tallest than her sister.", "She is more tall than her sister.", 0, "Taller is already comparative; more taller is a double comparative and is wrong.");
        insT(db, "English", "Sentence Correction", "Common Errors (Pronouns, Adjectives, Adverbs)", "Choose the correct sentence:", "He drives very fastly.", "He drives very fast.", "He drives very fastest.", "He drives very more fast.", 1, "Fast is both an adjective and an adverb; there is no word fastly.");
        insT(db, "English", "Sentence Correction", "Common Errors (Pronouns, Adjectives, Adverbs)", "Choose the correct sentence:", "He speaks English fluent.", "He speaks English fluentness.", "He speaks English fluently.", "He speaks English in fluent.", 2, "An adverb, fluently, is needed to describe how he speaks.");
        insT(db, "English", "Vocabulary and Idioms", "Common Idioms", "The idiom to bite the dust means:", "to fail", "to eat quickly", "to bite something", "to run away", 0, "To bite the dust means to fail or be defeated.");
        insT(db, "English", "Vocabulary and Idioms", "Common Idioms", "The idiom a blessing in disguise means:", "a hidden loss", "a costly gift", "a hidden benefit", "a religious event", 2, "It means something good that first seemed bad.");
        insT(db, "English", "Vocabulary and Idioms", "Common Idioms", "The idiom to burn the midnight oil means:", "to waste oil", "to work late at night", "to light a lamp", "to cook at night", 1, "It means to study or work late into the night.");
        insT(db, "English", "Vocabulary and Idioms", "Common Idioms", "The idiom once in a blue moon means:", "every night", "every month", "always", "very rarely", 3, "It means something that happens very rarely.");
        insT(db, "English", "Vocabulary and Idioms", "One-Word and Phrasal Verbs", "A person who speaks many languages is called a _____.", "orator", "bilingual", "polyglot", "author", 2, "A polyglot is a person who knows or speaks many languages.");
        insT(db, "English", "Vocabulary and Idioms", "One-Word and Phrasal Verbs", "The firemen worked hard to _____ the fire.", "put off", "put on", "put up", "put out", 3, "Put out means to stop something from burning.");
        insT(db, "English", "Vocabulary and Idioms", "One-Word and Phrasal Verbs", "A speech given without preparation is called _____.", "extempore", "maiden", "oration", "lecture", 0, "An extempore speech is spoken without preparation.");
        insT(db, "English", "Vocabulary and Idioms", "One-Word and Phrasal Verbs", "Please _____ the lights before leaving the room.", "switch on", "switch off", "switch over", "switch in", 1, "Switch off means to stop a device or light from working.");
        // === BATCH3 CONTENT END ===
    }

    // ---------- Seed: the starter 40 questions ----------

    private void ins(SQLiteDatabase db, String subject, String question,
                     String o0, String o1, String o2, String o3, int correct, String explanation) {
        ContentValues v = new ContentValues();
        v.put("subject", subject);
        v.put("question", question);
        v.put("opt0", o0);
        v.put("opt1", o1);
        v.put("opt2", o2);
        v.put("opt3", o3);
        v.put("correct", correct);
        v.put("explanation", explanation);
        db.insert("questions", null, v);
    }

    private void seed(SQLiteDatabase db) {
        // Mathematics
        ins(db, "Mathematics", "If x + 5 = 12, then x = ?", "5", "7", "12", "17", 1, "When a number crosses the = sign, + becomes -. So x = 12 - 5 = 7.");
        ins(db, "Mathematics", "If x - 3 = 5, then x = ?", "2", "3", "8", "15", 2, "Crossing the = sign, - becomes +. So x = 5 + 3 = 8.");
        ins(db, "Mathematics", "What is 20% of 200?", "20", "40", "60", "100", 1, "20% means 20 out of 100. 200 x 20/100 = 40.");
        ins(db, "Mathematics", "7 x 8 = ?", "54", "56", "63", "72", 1, "7 x 8 = 56.");
        ins(db, "Mathematics", "If 3x = 15, then x = ?", "3", "5", "12", "45", 1, "Divide both sides by 3: x = 15 / 3 = 5.");
        ins(db, "Mathematics", "What is the square of 9?", "18", "36", "81", "99", 2, "Square means multiply the number by itself: 9 x 9 = 81.");
        ins(db, "Mathematics", "Which number is prime?", "9", "15", "11", "21", 2, "A prime number has only two factors: 1 and itself. 11 is prime.");
        ins(db, "Mathematics", "1/2 + 1/4 = ?", "1/6", "2/6", "3/4", "1", 2, "Make the bottom numbers equal: 1/2 = 2/4, so 2/4 + 1/4 = 3/4.");
        ins(db, "Mathematics", "What is 2^3?", "5", "6", "8", "9", 2, "2^3 = 2 x 2 x 2 = 8.");
        ins(db, "Mathematics", "The angles of a triangle add up to:", "90 degrees", "180 degrees", "270 degrees", "360 degrees", 1, "The three angles of any triangle always add up to 180 degrees.");
        // Physics
        ins(db, "Physics", "Speed is calculated as:", "Distance x Time", "Distance / Time", "Time / Distance", "Distance + Time", 1, "Speed = Distance / Time.");
        ins(db, "Physics", "The SI unit of force is:", "Joule", "Watt", "Newton", "Pascal", 2, "Force is measured in newtons (N).");
        ins(db, "Physics", "A bus covers 60 km in 2 hours. Its speed is:", "120 km/h", "30 km/h", "62 km/h", "15 km/h", 1, "Speed = 60 / 2 = 30 km/h.");
        ins(db, "Physics", "The SI unit of length is:", "Centimeter", "Metre", "Kilometer", "Inch", 1, "The standard (SI) unit of length is the metre (m).");
        ins(db, "Physics", "Which of these is a scalar quantity?", "Speed", "Velocity", "Force", "Acceleration", 0, "Speed has only size, no direction, so it is a scalar. The others have direction too.");
        ins(db, "Physics", "Objects fall to the Earth because of:", "Magnetism", "Friction", "Gravity", "Pressure", 2, "Gravity is the force that pulls objects toward the Earth.");
        ins(db, "Physics", "The SI unit of energy is:", "Newton", "Joule", "Watt", "Volt", 1, "Energy is measured in joules (J).");
        ins(db, "Physics", "A car moves at a constant speed of 10 m/s for 5 seconds. Distance covered = ?", "2 m", "15 m", "50 m", "100 m", 2, "Distance = Speed x Time = 10 x 5 = 50 m.");
        ins(db, "Physics", "Capacitive reactance (Xc) decreases when frequency:", "Decreases", "Increases", "Stays zero", "Becomes DC", 1, "Xc = 1/(2 x pi x f x C). When frequency f increases, Xc decreases.");
        ins(db, "Physics", "At sea level, water boils at:", "90 C", "95 C", "100 C", "120 C", 2, "At normal air pressure (sea level), water boils at 100 degrees C.");
        // Chemistry
        ins(db, "Chemistry", "The chemical formula of water is:", "CO2", "H2O", "O2", "H2", 1, "Water is H2O.");
        ins(db, "Chemistry", "One molecule of water has how many hydrogen atoms?", "1", "2", "3", "4", 1, "H2O means 2 hydrogen atoms and 1 oxygen atom.");
        ins(db, "Chemistry", "The smallest particle of an element is called:", "Molecule", "Atom", "Ion", "Cell", 1, "Everything is made of atoms; an atom is the smallest part of an element.");
        ins(db, "Chemistry", "The chemical symbol of sodium is:", "S", "So", "Na", "N", 2, "Sodium's symbol is Na.");
        ins(db, "Chemistry", "The pH of a neutral solution is:", "0", "7", "14", "1", 1, "pH 7 is neutral. Below 7 is acidic, above 7 is basic.");
        ins(db, "Chemistry", "Which of these is an acid?", "NaOH", "HCl", "NaCl", "CaCO3", 1, "HCl (hydrochloric acid) is an acid.");
        ins(db, "Chemistry", "The atomic number of oxygen is:", "6", "8", "16", "2", 1, "Oxygen has 8 protons, so its atomic number is 8.");
        ins(db, "Chemistry", "The formula of common salt is:", "KCl", "NaCl", "CaCl2", "NaOH", 1, "Common salt is sodium chloride, NaCl.");
        ins(db, "Chemistry", "Plants take in which gas for photosynthesis?", "Oxygen", "Nitrogen", "Carbon dioxide", "Hydrogen", 2, "Plants use carbon dioxide (CO2) and release oxygen.");
        ins(db, "Chemistry", "H2SO4 is the formula of:", "Hydrochloric acid", "Nitric acid", "Sulfuric acid", "Acetic acid", 2, "H2SO4 is sulfuric acid.");
        // English
        ins(db, "English", "The synonym of \"happy\" is:", "Sad", "Glad", "Angry", "Tired", 1, "Glad means happy.");
        ins(db, "English", "The antonym of \"big\" is:", "Large", "Huge", "Small", "Wide", 2, "The opposite of big is small.");
        ins(db, "English", "The past tense of \"go\" is:", "Goed", "Went", "Gone to", "Going", 1, "Go -> went (past) -> gone.");
        ins(db, "English", "Choose the correct sentence:", "He go to school daily.", "He goes to school daily.", "He going to school daily.", "He gone to school daily.", 1, "With he/she/it, we add -s or -es to the verb: he goes.");
        ins(db, "English", "The plural of \"child\" is:", "Childs", "Children", "Childes", "Childrens", 1, "Child is irregular: its plural is children.");
        ins(db, "English", "Which word is a noun?", "Run", "Quickly", "Book", "Happy", 2, "A noun is a person, place or thing. Book is a thing.");
        ins(db, "English", "She has ___ apple.", "a", "an", "the to", "two", 1, "We use \"an\" before words starting with a vowel sound: an apple.");
        ins(db, "English", "The synonym of \"fast\" is:", "Slow", "Quick", "Late", "Weak", 1, "Quick means fast.");
        ins(db, "English", "Choose the correct spelling:", "Beautifull", "Beautiful", "Biutiful", "Beautyful", 1, "The correct spelling is B-E-A-U-T-I-F-U-L.");
        ins(db, "English", "He is afraid ___ dogs.", "from", "of", "with", "by", 1, "The correct phrase is \"afraid of\".");
        seedBatch2(db);
        seedBatch3(db);
    }

    // ---------- Batch 2: MUET entry-test-style questions ----------

    private void seedBatch2(SQLiteDatabase db) {
        // Mathematics — entry test style
        ins(db, "Mathematics", "If f(x) = 2x + 3, then f(4) = ?", "8", "11", "14", "7", 1, "Put x = 4: f(4) = 2(4) + 3 = 11.");
        ins(db, "Mathematics", "The solution of x^2 - 5x + 6 = 0 is:", "x = 1, 6", "x = -2, -3", "x = 2, 3", "x = 5, 1", 2, "x^2 - 5x + 6 = (x - 2)(x - 3) = 0, so x = 2 or x = 3.");
        ins(db, "Mathematics", "log(1000) to the base 10 is:", "2", "3", "4", "10", 1, "10^3 = 1000, so log of 1000 is 3.");
        ins(db, "Mathematics", "The 5th term of the A.P. 2, 5, 8, ... is:", "11", "17", "20", "14", 3, "Difference d = 3. 5th term = 2 + 4 x 3 = 14.");
        ins(db, "Mathematics", "sin^2(theta) + cos^2(theta) = ?", "0", "2", "1", "tan^2(theta)", 2, "This is a basic identity: sin^2 + cos^2 is always 1.");
        ins(db, "Mathematics", "A matrix having only one row is called:", "Column matrix", "Square matrix", "Row matrix", "Zero matrix", 2, "A matrix with a single row is a row matrix.");
        ins(db, "Mathematics", "If A = {1, 2, 3} and B = {3, 4}, then A intersection B = ?", "{1, 2}", "{3}", "{1, 2, 3, 4}", "Empty set", 1, "Intersection means the common elements. Only 3 is in both sets.");
        ins(db, "Mathematics", "The derivative of x^3 is:", "x^2", "3x", "x^3 / 3", "3x^2", 3, "Bring the power down and reduce it by 1: d/dx (x^3) = 3x^2.");
        ins(db, "Mathematics", "In how many ways can the letters of the word CAT be arranged?", "3", "6", "9", "27", 1, "3 different letters can be arranged in 3! = 3 x 2 x 1 = 6 ways.");
        ins(db, "Mathematics", "If |x| = 5, then x = ?", "5 only", "-5 only", "0", "+5 or -5", 3, "|x| means distance from zero, so x can be 5 or -5.");
        // Physics — entry test style
        ins(db, "Physics", "The SI unit of power is:", "Joule", "Newton", "Watt", "Pascal", 2, "Power is measured in watts (W). 1 watt = 1 joule per second.");
        ins(db, "Physics", "A body of mass 2 kg moves with velocity 20 m/s. Its kinetic energy is:", "200 J", "400 J", "40 J", "800 J", 1, "KE = (1/2)mv^2 = (1/2)(2)(20)^2 = 400 J.");
        ins(db, "Physics", "Which of the following is a vector quantity?", "Speed", "Distance", "Time", "Displacement", 3, "Displacement has both size and direction, so it is a vector.");
        ins(db, "Physics", "The value of g (acceleration due to gravity) is approximately:", "8.9 m/s^2", "9.8 m/s^2", "10.8 m/s^2", "6.7 m/s^2", 1, "On Earth, g is about 9.8 m/s^2.");
        ins(db, "Physics", "If V = 12 volts and R = 4 ohms, the current I = V/R is:", "48 A", "8 A", "3 A", "16 A", 2, "I = 12 / 4 = 3 amperes.");
        ins(db, "Physics", "The time period of a simple pendulum depends on its:", "Mass", "Length", "Amplitude", "Bob size", 1, "T = 2 x pi x sqrt(L/g) — only length (and g) matter, not the mass.");
        ins(db, "Physics", "Which wave cannot travel through a vacuum?", "Light waves", "Radio waves", "Sound waves", "X-rays", 2, "Sound needs a material medium (air, water, solids) to travel.");
        ins(db, "Physics", "A force of 10 N moves a body 5 m in its own direction. Work done = ?", "2 J", "15 J", "0.5 J", "50 J", 3, "Work = Force x Distance = 10 x 5 = 50 J.");
        ins(db, "Physics", "The frequency of the AC supply in Pakistan is:", "50 Hz", "60 Hz", "100 Hz", "220 Hz", 0, "Pakistan uses 50 Hz AC supply.");
        ins(db, "Physics", "A convex mirror always forms an image that is:", "Real and inverted", "Virtual and erect", "Real and erect", "Virtual and inverted", 1, "A convex (bulging) mirror always gives a virtual, erect and smaller image.");
        // Chemistry — entry test style
        ins(db, "Chemistry", "The atomic mass unit (amu) is defined with respect to:", "Hydrogen-1", "Oxygen-16", "Carbon-12", "Helium-4", 2, "1 amu is 1/12 of the mass of one carbon-12 atom.");
        ins(db, "Chemistry", "The number of moles in 18 g of water is:", "0.5", "1", "2", "18", 1, "Molar mass of water is 18 g/mol, so 18 g = 1 mole.");
        ins(db, "Chemistry", "The type of bond in NaCl is:", "Covalent", "Metallic", "Hydrogen", "Ionic", 3, "Sodium gives an electron to chlorine, forming an ionic bond.");
        ins(db, "Chemistry", "The most electronegative element is:", "Oxygen", "Fluorine", "Chlorine", "Nitrogen", 1, "Fluorine attracts electrons the most strongly.");
        ins(db, "Chemistry", "Avogadro's number is:", "6.02 x 10^23", "6.02 x 10^22", "1.6 x 10^-19", "3.0 x 10^8", 0, "One mole of any substance contains 6.02 x 10^23 particles.");
        ins(db, "Chemistry", "Oxidation means:", "Gain of electrons", "Gain of hydrogen", "Loss of electrons", "Loss of oxygen", 2, "OIL RIG: Oxidation Is Loss (of electrons), Reduction Is Gain.");
        ins(db, "Chemistry", "The lightest gas is:", "Oxygen", "Helium", "Nitrogen", "Hydrogen", 3, "Hydrogen is the lightest element and gas.");
        ins(db, "Chemistry", "Ca(OH)2 is commonly called:", "Quicklime", "Slaked lime", "Limestone", "Gypsum", 1, "Ca(OH)2 is slaked lime. CaO is quicklime and CaCO3 is limestone.");
        ins(db, "Chemistry", "Elements in the same group of the periodic table have the same:", "Atomic mass", "Number of shells", "Valence electrons", "Number of neutrons", 2, "Group members have the same number of electrons in their outer shell.");
        ins(db, "Chemistry", "Which of the following is an organic compound?", "NaCl", "CO2", "CH4", "CaCO3", 2, "CH4 (methane) is organic — organic compounds are built on carbon-hydrogen chains.");
        // English — entry test style
        ins(db, "English", "The synonym of \"rapid\" is:", "Slow", "Swift", "Weak", "Late", 1, "Rapid and swift both mean very fast.");
        ins(db, "English", "The antonym of \"ancient\" is:", "Old", "Past", "Modern", "Antique", 2, "Ancient means very old; its opposite is modern.");
        ins(db, "English", "Choose the correct sentence:", "He has been living here for 2015.", "He has been living here since 2015.", "He has been living here from 2015.", "He has been living here at 2015.", 1, "\"Since\" is used with a point in time (since 2015); \"for\" is used with a period (for 5 years).");
        ins(db, "English", "She is good ___ Mathematics.", "in", "on", "at", "for", 2, "The correct phrase is \"good at\" a subject or skill.");
        ins(db, "English", "The passive voice of \"He wrote a letter\" is:", "A letter is written by him.", "A letter was written by him.", "A letter has written by him.", "A letter wrote by him.", 1, "\"Wrote\" is past tense, so the passive uses \"was written\".");
        ins(db, "English", "The past participle of \"eat\" is:", "Ate", "Eating", "Eats", "Eaten", 3, "Eat — ate — eaten.");
        ins(db, "English", "One of the boys ___ absent today.", "are", "were", "is", "have", 2, "\"One of...\" takes a singular verb: one boy is absent.");
        ins(db, "English", "The idiom \"to break the ice\" means:", "To break something frozen", "To feel very cold", "To start a conversation", "To end a fight", 2, "To break the ice means to start talking in a social situation.");
        ins(db, "English", "Choose the correct spelling:", "Ocassion", "Occassion", "Ocasion", "Occasion", 3, "The correct spelling is O-C-C-A-S-I-O-N.");
        ins(db, "English", "He prefers tea ___ coffee.", "than", "to", "from", "over", 1, "We say \"prefer X to Y\", not \"prefer X than Y\".");
    }
}
