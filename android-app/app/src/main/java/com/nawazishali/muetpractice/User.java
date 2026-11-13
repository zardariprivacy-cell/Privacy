package com.nawazishali.muetpractice;

public class User {
    public long id;
    public String name;
    public String username;
    public String photoPath; // null when no picture set
    public String email; // null when not set (optional, added v3.4)
    public boolean admin;

    public String initials() {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
