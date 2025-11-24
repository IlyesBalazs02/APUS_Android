package com.example.android_apus.tracks;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.R;

import java.util.ArrayList;
import java.util.List;

public class TracksAdapter extends RecyclerView.Adapter<TracksAdapter.TrackViewHolder> {

    private final List<String> tracks = new ArrayList<>();

    public void setTracks(List<String> newTracks) {
        tracks.clear();
        if (newTracks != null) {
            tracks.addAll(newTracks);
        }
        notifyDataSetChanged();
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
        String name = tracks.get(position);
        holder.textTrackName.setText(name);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        TextView textTrackName;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            textTrackName = itemView.findViewById(R.id.textTrackName);
        }
    }
}
