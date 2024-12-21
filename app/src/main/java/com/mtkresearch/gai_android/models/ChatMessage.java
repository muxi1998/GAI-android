package com.mtkresearch.gai_android.models;

public class ChatMessage {
    private String text;
    private final boolean isUser;

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

    public boolean isUser() {
        return isUser;
    }
} 