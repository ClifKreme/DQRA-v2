package com.appname.dqra;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.AppFeedback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppFeedbackAdapter extends RecyclerView.Adapter<AppFeedbackAdapter.ViewHolder> {
    private final List<AppFeedback> feedbackList = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault());

    public void setData(List<AppFeedback> feedback) {
        feedbackList.clear();
        feedbackList.addAll(feedback);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_feedback, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppFeedback feedback = feedbackList.get(position);
        holder.tvSenderName.setText(firstNonEmpty(feedback.getSenderName(), "Unknown"));
        holder.tvRoleMunicipality.setText(buildMetaText(feedback));
        holder.tvMessage.setText(firstNonEmpty(feedback.getMessage(), "No message"));
        holder.tvDate.setText(feedback.getTimestamp() > 0
                ? dateFormat.format(new Date(feedback.getTimestamp()))
                : "");
        holder.ratingFeedbackItem.setRating(feedback.getRating());
    }

    @Override
    public int getItemCount() {
        return feedbackList.size();
    }

    private String buildMetaText(AppFeedback feedback) {
        String role = firstNonEmpty(feedback.getSenderRole(), "User");
        String municipality = feedback.getMunicipality();
        if (municipality == null || municipality.trim().isEmpty()) {
            return role;
        }
        return role + " - " + municipality;
    }

    private String firstNonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName;
        TextView tvRoleMunicipality;
        TextView tvMessage;
        TextView tvDate;
        RatingBar ratingFeedbackItem;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvFeedbackSenderName);
            tvRoleMunicipality = itemView.findViewById(R.id.tvFeedbackRoleMunicipality);
            tvMessage = itemView.findViewById(R.id.tvFeedbackMessage);
            tvDate = itemView.findViewById(R.id.tvFeedbackDate);
            ratingFeedbackItem = itemView.findViewById(R.id.ratingFeedbackItem);
        }
    }
}
