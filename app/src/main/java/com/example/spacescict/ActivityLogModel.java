package com.example.spacescict;

import com.google.firebase.Timestamp;

public class ActivityLogModel {
    public String id, action, actionType, target, status;
    public Timestamp timestamp;

    public ActivityLogModel(String id, String action, String actionType, String target,
                            String status, Timestamp timestamp) {
        this.id = id;
        this.action = action;
        this.actionType = actionType;
        this.target = target;
        this.status = status;
        this.timestamp = timestamp;
    }
}