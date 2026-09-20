package com.example.spacescict;

public class RoomModel {
    String roomId, roomName, building, floor, roomType, status, occupiedUntil;
    int capacity;
    int image;
    boolean watched;

    public RoomModel(String roomId, String roomName, String floor, String roomType,
                     String status, String occupiedUntil, int capacity, int image) {
        this(roomId, roomName, "", floor, roomType, status, occupiedUntil, capacity, image);
    }

    public RoomModel(String roomId, String roomName, String building, String floor,
                     String roomType, String status, String occupiedUntil,
                     int capacity, int image) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.building = building;
        this.floor = floor;
        this.roomType = roomType;
        this.status = status;
        this.occupiedUntil = occupiedUntil;
        this.capacity = capacity;
        this.image = image;
    }
}