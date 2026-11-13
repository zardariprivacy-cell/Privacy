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
        super(context, "muet.db", null, 1);
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
                "explanation TEXT NOT NULL)");
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
        // Version 1 — nothing to upgrade yet.
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
    }
}
