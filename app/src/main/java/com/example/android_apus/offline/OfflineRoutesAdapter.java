package com.example.android_apus.offline;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android_apus.R;

import java.util.List;

public class OfflineRoutesAdapter extends RecyclerView.Adapter<OfflineRoutesAdapter.VH> {

    public interface OnClick {
        void onClick(OfflineRoute route);
    }

    private final List<OfflineRoute> routes;
    private final OnClick onClick;

    public OfflineRoutesAdapter(List<OfflineRoute> routes, OnClick onClick) {
        this.routes = routes;
        this.onClick = onClick;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_offline_route, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        OfflineRoute r = routes.get(position);
        h.title.setText(r.name);
        h.subtitle.setText(r.mapFile.getName() + (r.gpxFile != null ? " + GPX" : " (no GPX)"));
        h.itemView.setOnClickListener(v -> onClick.onClick(r));
    }

    @Override
    public int getItemCount() {
        return routes.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title, subtitle;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
        }
    }
}
