package com.nawazishali.muetpractice;

public class ResultRow {
    public long id;
    public long userId;
    public String mode;    // "practice" or "mock"
    public String subject; // subject name, or "Mixed" for mock
    public int score;
    public int total;
    public long dateMillis;

    public int percent() {
        if (total == 0) return 0;
        return Math.round(score * 100f / total);
    }
}
