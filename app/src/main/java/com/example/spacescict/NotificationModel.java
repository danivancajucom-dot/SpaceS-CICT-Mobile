// NotificationModel.java
package com.example.spacescict;

public class NotificationModel {

    int icon;
    String title, desc, time, status;
    int bg;

    public NotificationModel(int icon, String title, String desc,
                             String time, String status, int bg) {
        this.icon = icon;
        this.title = title;
        this.desc = desc;
        this.time = time;
        this.status = status;
        this.bg = bg;
    }
}