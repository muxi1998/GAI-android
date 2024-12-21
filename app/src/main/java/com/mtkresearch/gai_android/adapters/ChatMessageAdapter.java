package com.mtkresearch.gai_android.adapters;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mtkresearch.gai_android.R;
import com.mtkresearch.gai_android.models.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {
    private List<ChatMessage> messages = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private int streamingPosition = -1;
    private String fullText = "";
    private int currentCharIndex = 0;

    public void addMessage(ChatMessage message) {
        messages.add(message);
        int position = messages.size() - 1;
        notifyItemInserted(position);
        
        if (!message.isUser()) {
            streamText(message.getText(), position);
        }
    }

    private void streamText(String text, int position) {
        fullText = text;
        currentCharIndex = 0;
        streamingPosition = position;
        streamNextCharacter();
    }

    private void streamNextCharacter() {
        if (currentCharIndex <= fullText.length()) {
            String partialText = fullText.substring(0, currentCharIndex);
            messages.get(streamingPosition).updateText(partialText);
            notifyItemChanged(streamingPosition);
            
            currentCharIndex++;
            handler.postDelayed(this::streamNextCharacter, 50); // Adjust speed here
        } else {
            streamingPosition = -1;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);

        // Set alignment based on message type
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.messageText.getLayoutParams();
        if (message.isUser()) {
            params.gravity = Gravity.END;
            holder.messageText.setBackgroundResource(R.drawable.bg_user_message);
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.user_message_text));
        } else {
            params.gravity = Gravity.START;
            holder.messageText.setBackgroundResource(R.drawable.bg_ai_message);
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.ai_message_text));
        }
        holder.messageText.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getText());
        }
    }
} 