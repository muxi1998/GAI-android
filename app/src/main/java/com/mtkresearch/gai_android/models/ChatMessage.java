package com.mtkresearch.gai_android.models;

import android.net.Uri;

public class ChatMessage {
    private String text;
    private boolean isUser;
    private Uri imageUri;

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
    }

    public String getText() {
        return text;
    }

    public void updateText(String newText) {
        this.text = newText;
    }

    public void appendText(String token) {
        if (text == null) {
            text = token;
        } else {
            text += token;
        }
    }

    public boolean isUser() {
        return isUser;
    }

    public Uri getImageUri() {
        return imageUri;
    }

    public void setImageUri(Uri imageUri) {
        this.imageUri = imageUri;
    }

    public void setText(String newText) {
        this.text = newText;
    }
} 