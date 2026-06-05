package com.example.spacescict;

public class RoomModel {

    String name, status, capacity, time;
    int image;

    public RoomModel(String name, String status, String capacity, String time, int image) {
        this.name = name;
        this.status = status;
        this.capacity = capacity;
        this.time = time;
        this.image = image;
    }
}