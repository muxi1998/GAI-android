package com.mtkresearch.gai_android.adapters;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
        View messageContainer = holder.itemView.findViewById(R.id.messageContainer);
        if (messageContainer != null) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) messageContainer.getLayoutParams();
            if (message.isUser()) {
                params.gravity = Gravity.END;
                messageContainer.setBackgroundResource(R.drawable.bg_user_message);
                holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.user_message_text));
            } else {
                params.gravity = Gravity.START;
                messageContainer.setBackgroundResource(R.drawable.bg_ai_message);
                holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.ai_message_text));
            }
            messageContainer.setLayoutParams(params);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final ImageView messageImage;
        private final View messageContainer;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            messageText = itemView.findViewById(R.id.messageText);
            messageImage = itemView.findViewById(R.id.messageImage);
        }

        void bind(ChatMessage message) {
            // Only set text if it's not empty
            if (message.getText() != null && !message.getText().isEmpty()) {
                messageText.setVisibility(View.VISIBLE);
                messageText.setText(message.getText());
            } else {
                messageText.setVisibility(View.GONE);
            }

            // Handle image
            if (message.getImageUri() != null) {
                messageImage.setVisibility(View.VISIBLE);
                messageImage.setImageURI(message.getImageUri());
            } else {
                messageImage.setVisibility(View.GONE);
            }
        }
    }
} 