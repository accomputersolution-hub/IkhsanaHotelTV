package `in`.pcncloud.hotel.ui.theme

import androidx.compose.ui.graphics.Color

val NavyDeep = Color(0xFF0B1325)
val NavyMain = Color(0xFF0F172A)
val NavySurface = Color(0xFF1B2838)

/** Night-mode scaffold (18:00–05:59). */
val NightBackground = NavyDeep
val NightSurface = NavySurface

/** Day-mode scaffold (06:00–17:59) — lifted navy, not flat white (TV glare). */
val DayBackground = Color(0xFF1A2740)
val DaySurface = Color(0xFF243552)
val DayOnBackground = Color(0xFFF8FAFC)

val GoldPrimary = Color(0xFFC9A962)
val GoldLight = Color(0xFFE8D5A3)
val GoldMuted = Color(0xFF8B7355)
/** Warm amber used for luxury nav icons / glass badges (#D4AF37). */
val GoldLuxury = Color(0xFFD4AF37)
val GoldGlassFill = Color(0x1AD4AF37) // rgba(212, 175, 55, 0.1)
val GoldGlassBorder = Color(0x33D4AF37) // rgba(212, 175, 55, 0.2)

val FocusCyan = Color(0xFF22D3EE)
val FocusTeal = Color(0xFF34D399)
val FocusBlueTeal = Color(0xFF38BDF8)
val FocusRoyalBlue = Color(0xFF6366F1)

/** L&T / corporate focus accent — clean professional blue. */
val CorporateBlue = Color(0xFF0066B3)
/** Simple frosted dark glass for corporate nav cards. */
val CorporateGlass = Color(0xCC1A1A1A) // ~80% dark grey/black

/** Home-screen Black & Gold palette — reuse on all sub-menus. */
val CorpGold = Color(0xFFD4AF37)
val CorpGoldBright = Color(0xFFFFD700)
val CorpGoldBorderIdle = Color(0x66D4AF37)
/** Legacy solid card fill — prefer [CorpGlassNight] / [CorpGlassDay] for home tiles. */
val CorpCardBg = Color(0xFA111111)
val CorpSubtitle = Color(0xFFCCCCCC)

/** Corporate glassmorphism card fills (wallpaper shows through). */
val CorpGlassNight = Color(0x66000000) // black @ 40%
val CorpGlassDay = Color(0x4DFFFFFF) // white @ 30%

/** Corporate card content colors for day / night. */
val CorpCardTextNight = Color(0xFFF8FAFC)
val CorpCardTextDay = Color(0xFF1A1A1A)
val CorpCardSubtitleNight = Color(0xFFCCCCCC)
val CorpCardSubtitleDay = Color(0xFF475569)

val TextPrimary = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF94A3B8)
val TextDim = Color(0xFF64748B)

val HotelPrimary = Color(0xFF1565C0)
val HotelSecondary = Color(0xFF0D47A1)
val HotelAccent = Color(0xFFE65100)
val HotelBackground = NavyDeep
val HotelSurface = NavySurface

val Purple80 = Color(0xFF90CAF9)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = HotelPrimary
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = HotelAccent
