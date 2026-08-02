package com.example.spacescict;

public class RoomModel {
    String roomId, roomName, floor, roomType, status, occupiedUntil;
    int capacity;
    int image;

    public RoomModel(String roomId, String roomName, String floor, String roomType,
                     String status, String occupiedUntil, int capacity, int image) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.floor = floor;
        this.roomType = roomType;
        this.status = status;
        this.occupiedUntil = occupiedUntil;
        this.capacity = capacity;
        this.image = image;
    }
}