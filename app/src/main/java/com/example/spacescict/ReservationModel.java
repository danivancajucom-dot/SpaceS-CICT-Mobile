package com.example.spacescict;

public class ReservationModel {
    public String id;
    String status, roomName, courseTitle;
    int image;

    public ReservationModel(String status, String roomName, String courseTitle, int image) {
        this.status = status;
        this.roomName = roomName;
        this.courseTitle = courseTitle;
        this.image = image;
    }
}