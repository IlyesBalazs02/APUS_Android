package com.example.android_apus.tracks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.R;

import java.util.ArrayList;
import java.util.List;

public class TracksAdapter extends RecyclerView.Adapter<TracksAdapter.TrackViewHolder> {

    public interface OnTrackClickListener {
        void onTrackClick(String fileName);
        void onDownloadClick(String fileName);
    }

    private List<String> items;
    private OnTrackClickListener listener;

    public TracksAdapter(List<String> items, OnTrackClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        String name = items.get(position);
        holder.textTrackName.setText(name);

        // Selecting a track (old behaviour)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(name);
            }
        });

        // NEW: clicking Download map
        holder.buttonDownloadMap.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownloadClick(name);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView textTrackName;
        Button buttonDownloadMap;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            textTrackName = itemView.findViewById(R.id.textTrackName);
            buttonDownloadMap = itemView.findViewById(R.id.buttonDownloadMap);
        }
    }
}


