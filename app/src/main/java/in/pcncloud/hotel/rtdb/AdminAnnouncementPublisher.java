package in.pcncloud.hotel.rtdb;

import android.app.Activity;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Admin App: write the TV ticker string to Realtime Database.
 * Node: {@code hotels/{hotelId}/config/global_announcement}
 */
public final class AdminAnnouncementPublisher {

    private static final String TAG = "AdminAnnouncement";
    private static final String RTDB_URL =
            "https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app";

    private AdminAnnouncementPublisher() {}

    @NonNull
    public static String path(@Nullable String hotelId) {
        String id = hotelId == null ? "" : hotelId.trim().toLowerCase().replace('-', '_');
        return "hotels/" + id + "/config/global_announcement";
    }

    /**
     * Wire an EditText + Button. On click, writes the EditText value to RTDB.
     *
     * <pre>
     * AdminAnnouncementPublisher.bind(
     *     this, "lnt_academy", R.id.et_announcement, R.id.btn_publish_announcement);
     * </pre>
     */
    public static void bind(
            @NonNull Activity activity,
            @NonNull String hotelId,
            int editTextId,
            int buttonId
    ) {
        EditText input = activity.findViewById(editTextId);
        Button button = activity.findViewById(buttonId);
        if (input == null || button == null) {
            Log.e(TAG, "EditText or Button not found — check layout ids");
            return;
        }
        button.setOnClickListener(v -> publish(activity, hotelId, String.valueOf(input.getText())));
    }

    public static void publish(
            @Nullable Activity activity,
            @NonNull String hotelId,
            @Nullable String message
    ) {
        String id = hotelId == null ? "" : hotelId.trim().toLowerCase().replace('-', '_');
        if (id.isEmpty()) {
            Log.e(TAG, "Cannot publish: hotelId is blank");
            if (activity != null) {
                Toast.makeText(activity, "Hotel ID is missing", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String text = message == null ? "" : message.trim();
        DatabaseReference ref;
        try {
            ref = FirebaseDatabase.getInstance(FirebaseApp.getInstance(), RTDB_URL)
                    .getReference(path(id));
        } catch (Exception e) {
            Log.e(TAG, "FirebaseDatabase getInstance failed", e);
            if (activity != null) {
                Toast.makeText(activity, "Firebase is not ready", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        ref.setValue(text)
                .addOnSuccessListener(unused -> {
                    Log.i(TAG, "Published → " + path(id));
                    if (activity != null) {
                        Toast.makeText(activity, "TV ticker updated", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(err -> {
                    Log.e(TAG, "Publish failed → " + path(id), err);
                    if (activity != null) {
                        Toast.makeText(activity, "Failed to update ticker", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
