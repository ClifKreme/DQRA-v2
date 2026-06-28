package com.appname.dqra;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.appname.dqra.model.UserRegistrationRequest;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UserRegistrationRequestAdapter
        extends RecyclerView.Adapter<UserRegistrationRequestAdapter.RequestViewHolder> {

    public interface OnRequestClickListener {
        void onRequestClick(UserRegistrationRequest request);
    }

    private final List<UserRegistrationRequest> requests = new ArrayList<>();
    private final OnRequestClickListener listener;

    public UserRegistrationRequestAdapter(OnRequestClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<UserRegistrationRequest> newRequests) {
        requests.clear();
        requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_registration_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        UserRegistrationRequest request = requests.get(position);
        holder.tvName.setText(valueOrFallback(request.getFullName()));
        holder.tvEmail.setText(valueOrFallback(request.getEmail()));
        holder.tvSubmittedAt.setText(formatSubmittedAt(request.getSubmittedAt()));
        holder.itemView.setOnClickListener(v -> listener.onRequestClick(request));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    private String formatSubmittedAt(long submittedAt) {
        if (submittedAt <= 0L) {
            return "Submitted recently";
        }
        return "Submitted " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(submittedAt));
    }

    private String valueOrFallback(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value;
    }

    static class RequestViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvEmail;
        final TextView tvSubmittedAt;

        RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvRequestName);
            tvEmail = itemView.findViewById(R.id.tvRequestEmail);
            tvSubmittedAt = itemView.findViewById(R.id.tvRequestSubmittedAt);
        }
    }
}
