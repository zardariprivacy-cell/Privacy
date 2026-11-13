package com.nawazishali.muetpractice;

public class Question {
    public long id;
    public String subject;
    public String question;
    public String[] options = new String[4];
    public int correctIndex; // 0..3
    public String explanation;
    public String chapter = "General";
    public String topic = "Mixed Concepts";
}
