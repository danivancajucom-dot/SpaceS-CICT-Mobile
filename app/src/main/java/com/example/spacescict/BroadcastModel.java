package com.example.spacescict;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;

public class BroadcastModel {
    public String id, content, senderId, senderName, senderRole, recipient;
    public String imageUrl, fileUrl, fileName;
    public String linkTitle, linkUrl, linkImage;
    public Timestamp createdAt;
    public List<String> likeUids = new ArrayList<>();
    public List<String> loveUids = new ArrayList<>();
}