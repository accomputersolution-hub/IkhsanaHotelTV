package `in`.pcncloud.hotel.ui.dining

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import `in`.pcncloud.hotel.R

/**
 * Hotel-flavor premium In-Room Dining layout preview / scaffold.
 * Inflates [R.layout.activity_dining_premium] with Champagne-gold D-Pad focus.
 *
 * adb shell am start -n in.pcncloud.hotel/.ui.dining.PremiumDiningActivity
 */
class PremiumDiningActivity : AppCompatActivity() {

    private var cartHasItems = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dining_premium)

        val chargeBtn = findViewById<View>(R.id.btn_charge_to_room)
        val qrBtn = findViewById<View>(R.id.btn_pay_now_qr)
        val cta = findViewById<TextView>(R.id.btn_place_order_cta)

        listOf(chargeBtn, qrBtn, cta).forEach { attachFocusScale(it) }

        chargeBtn.isSelected = true
        chargeBtn.setOnClickListener {
            chargeBtn.isSelected = true
            qrBtn.isSelected = false
        }
        qrBtn.setOnClickListener {
            qrBtn.isSelected = true
            chargeBtn.isSelected = false
        }

        // Demo: long-press CTA toggles empty ↔ filled gold state for design review.
        cta.setOnLongClickListener {
            cartHasItems = !cartHasItems
            applyCtaState(cta)
            true
        }
        applyCtaState(cta)

        chargeBtn.post { chargeBtn.requestFocus() }
    }

    private fun applyCtaState(cta: TextView) {
        cta.isEnabled = cartHasItems
        cta.text = if (cartHasItems) {
            getString(R.string.cart_confirm_cta, 840.0)
        } else {
            getString(R.string.cart_add_items_cta)
        }
        findViewById<View>(R.id.dining_total_row).visibility =
            if (cartHasItems) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dining_cart_empty).visibility =
            if (cartHasItems) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.dining_cart_total).text = "₹840"
    }

    private fun attachFocusScale(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.05f else 1f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            if (hasFocus) {
                v.foreground = ContextCompat.getDrawable(this, R.drawable.dining_focus_border)
            }
        }
    }
}
