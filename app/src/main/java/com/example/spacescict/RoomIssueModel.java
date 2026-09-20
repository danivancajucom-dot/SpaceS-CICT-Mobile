package com.example.spacescict;

import com.google.firebase.Timestamp;

public class RoomIssueModel {
    public String id;
    public String roomId;
    public String roomName;
    public String category;
    public String description;
    public String severity;
    public String status;
    public String imageUrl;
    public String reporterName;
    public String clerkNotes;
    public Timestamp createdAt;

    public String searchableText() {
        return ((roomName == null ? "" : roomName) + " "
                + (category == null ? "" : category) + " "
                + (description == null ? "" : description)).toLowerCase();
    }
}