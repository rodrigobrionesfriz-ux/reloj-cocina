package cl.cocina.launcher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private List<AppItem> items = new ArrayList<AppItem>();

    public AppAdapter(Context ctx) {
        inflater = LayoutInflater.from(ctx);
    }

    public void setItems(List<AppItem> list) {
        items = list;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public AppItem getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override
    public View getView(int pos, View view, ViewGroup parent) {
        if (view == null) view = inflater.inflate(R.layout.item_app, parent, false);
        AppItem it = items.get(pos);
        ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(it.icon);
        ((TextView) view.findViewById(R.id.label)).setText(it.label);
        return view;
    }
}
