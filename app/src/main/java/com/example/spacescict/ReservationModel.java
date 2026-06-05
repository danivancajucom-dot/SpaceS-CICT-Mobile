// ReservationModel.java
package com.example.spacescict;

public class ReservationModel {

    String status, room, subject;
    int image;

    public ReservationModel(String status,
                            String room, String subject,
                            int image) {

        this.status = status;
        this.room = room;
        this.subject = subject;
        this.image = image;
    }
}