package com.example.spacescict;

import com.google.firebase.Timestamp;

public class NotificationModel {
    public String id;
    int icon;
    String title, desc, badge, type;
    Timestamp createdAt;
    boolean unread, archived;
    int bg;

    public NotificationModel(int icon, String title, String desc, String badge,
                             String type, Timestamp createdAt, boolean unread,
                             boolean archived, int bg) {
        this.icon = icon;
        this.title = title;
        this.desc = desc;
        this.badge = badge;
        this.type = type;
        this.createdAt = createdAt;
        this.unread = unread;
        this.archived = archived;
        this.bg = bg;
    }
}