package in.pcncloud.hotel.rtdb;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import in.pcncloud.hotel.R;

/**
 * TV Kiosk: live-listen to {@code hotel_settings/{hotelId}/global_announcement}
 * and update the marquee TextView instantly.
 *
 * <pre>
 * listener = KioskAnnouncementListener.attach(this, "lnt_academy");
 * // onDestroy:
 * listener.detach();
 * </pre>
 */
public final class KioskAnnouncementListener {

    private static final String TAG = "KioskAnnouncement";
    private static final String RTDB_URL =
            "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app";

    @Nullable
    private DatabaseReference ref;
    @Nullable
    private ValueEventListener listener;

    private KioskAnnouncementListener() {}

    @NonNull
    public static String path(@Nullable String hotelId) {
        String id = hotelId == null ? "" : hotelId.trim().toLowerCase().replace('-', '_');
        return "hotel_settings/" + id + "/global_announcement";
    }

    /**
     * Finds {@link R.id#tv_announcement_ticker}, starts the marquee, and
     * attaches {@code addValueEventListener} on the same RTDB node Admin writes.
     */
    @NonNull
    public static KioskAnnouncementListener attach(
            @NonNull Activity activity,
            @Nullable String hotelId
    ) {
        TextView ticker = activity.findViewById(R.id.tv_announcement_ticker);
        if (ticker == null) {
            Log.e(TAG, "tv_announcement_ticker not found in layout");
            return new KioskAnnouncementListener();
        }
        ticker.setFocusable(false);
        ticker.setFocusableInTouchMode(false);
        ticker.setSelected(true);
        return attach(hotelId, text -> applyToTextView(ticker, text));
    }

    @NonNull
    public static KioskAnnouncementListener attach(
            @Nullable String hotelId,
            @NonNull OnAnnouncementChanged callback
    ) {
        KioskAnnouncementListener holder = new KioskAnnouncementListener();
        String id = hotelId == null ? "" : hotelId.trim().toLowerCase().replace('-', '_');
        if (id.isEmpty()) {
            Log.e(TAG, "Cannot listen: hotelId is blank");
            callback.onChanged("");
            return holder;
        }

        try {
            holder.ref = FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)
                    .getReference(path(id));
        } catch (Exception e) {
            Log.e(TAG, "FirebaseDatabase getInstance failed", e);
            callback.onChanged("");
            return holder;
        }

        holder.listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || snapshot.getValue() == null) {
                    Log.d(TAG, "RTDB " + path(id) + " empty/null");
                    callback.onChanged("");
                    return;
                }
                Object raw = snapshot.getValue();
                String text = raw instanceof String
                        ? ((String) raw).trim()
                        : String.valueOf(raw).trim();
                Log.d(TAG, "RTDB " + path(id) + " → \"" + text + "\"");
                callback.onChanged(text);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "RTDB " + path(id) + " cancelled: " + error.getMessage(), error.toException());
                callback.onChanged("");
            }
        };
        holder.ref.addValueEventListener(holder.listener);
        return holder;
    }

    public void detach() {
        if (ref != null && listener != null) {
            ref.removeEventListener(listener);
        }
        ref = null;
        listener = null;
    }

    public static void applyToTextView(@NonNull TextView ticker, @Nullable String message) {
        String trimmed = message == null ? "" : message.trim();
        View parent = ticker.getParent() instanceof View ? (View) ticker.getParent() : null;
        if (trimmed.isEmpty()) {
            if (parent != null) parent.setVisibility(View.GONE);
            ticker.setText("");
            ticker.setSelected(false);
            return;
        }
        if (parent != null) parent.setVisibility(View.VISIBLE);
        ticker.setText(trimmed);
        ticker.setSelected(false);
        ticker.setSelected(true);
    }

    public interface OnAnnouncementChanged {
        void onChanged(@NonNull String text);
    }
}
