package `in`.pcncloud.hotel.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import `in`.pcncloud.hotel.MainActivity
import `in`.pcncloud.hotel.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hotel-flavor only premium 5-star home dashboard (XML / MaterialCardView).
 * Launched from Splash for hotelDebug builds.
 */
class PremiumHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_premium)

        bindClock()
        wireCards()

        findViewById<MaterialCardView>(R.id.card_entertainment).post {
            findViewById<MaterialCardView>(R.id.card_entertainment).requestFocus()
        }
    }

    private fun bindClock() {
        val timeView = findViewById<TextView>(R.id.premium_time)
        val dateView = findViewById<TextView>(R.id.premium_date)
        val now = Date()
        timeView.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        dateView.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
    }

    private fun wireCards() {
        val cards = listOf(
            R.id.card_entertainment to R.string.premium_card_entertainment,
            R.id.card_order_food to R.string.premium_card_order_food,
            R.id.card_guest_services to R.string.premium_card_guest_services,
            R.id.card_live_tv to R.string.premium_card_live_tv,
            R.id.card_laundry to R.string.premium_card_laundry,
            R.id.card_bell_desk to R.string.premium_card_bell_desk,
            R.id.card_message_alerts to R.string.premium_card_message_alerts,
        )

        cards.forEach { (id, labelRes) ->
            val card = findViewById<MaterialCardView>(id)
            attachFocusScale(card)
            card.setOnClickListener {
                when (id) {
                    R.id.card_order_food,
                    R.id.card_guest_services,
                    -> {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                    else -> Toast.makeText(this, getString(labelRes), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Slight scale-up + golden stroke when D-Pad focuses a card. */
    private fun attachFocusScale(card: View) {
        card.setOnFocusChangeListener { view, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1f
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator())
                .start()

            if (view is MaterialCardView) {
                view.strokeWidth = if (hasFocus) {
                    resources.getDimensionPixelSize(R.dimen.premium_focus_stroke)
                } else {
                    resources.getDimensionPixelSize(R.dimen.premium_idle_stroke)
                }
                view.strokeColor = ContextCompat.getColor(
                    this,
                    if (hasFocus) R.color.premium_gold_glow else R.color.premium_card_stroke,
                )
            }
        }
    }
}
