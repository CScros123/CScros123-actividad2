package personal.qqq.braintraining.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fase 8: tokens Foco, fuente única junto a app/src/main/res/values(-night)/colors.xml. */
object FocoTokens {
    val Fondo = Color(0xFFF2F5ED)
    val Superficie = Color(0xFFFCFDF8)
    val Texto = Color(0xFF243C31)
    val TextoSecundario = Color(0xFF53685B)
    val Acento = Color(0xFF38674D)
    val Contorno = Color(0xFFD7E2D1)
    val FondoOscuro = Color(0xFF050A09)
    val SuperficieOscura = Color(0xFF101A16)
    val TextoOscuro = Color(0xFFEDF3E6)
    val TextoSecundarioOscuro = Color(0xFFB4C7B5)
    val AcentoOscuro = Color(0xFFAACFA4)
    val RadioTarjeta = 20.dp
    val RadioBoton = 18.dp
}

val Mint = Color(0xFF1EDB90)
val Lavender = Color(0xFFA36BFF)
val Peach = Color(0xFFFF964A)
val Blue = Color(0xFF4778FF)

/**
 * [amoled] uses true black in dark mode (saves battery on OLED screens such as Galaxy S/Ultra).
 * [dynamic] takes the colors from the system wallpaper (Material You / One UI color palette).
 */
@Composable
fun BrainTheme(
    dark: Boolean = false,
    amoled: Boolean = false,
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base =
        if (dynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark)
            darkColorScheme(
                primary = Color(0xFF9DB7FF),
                onPrimary = Color(0xFF102967),
                primaryContainer = Color(0xFF283E75),
                onPrimaryContainer = Color(0xFFDEE7FF),
                secondary = Mint,
                onSecondary = Color(0xFF003822),
                secondaryContainer = Color(0xFF124B37),
                onSecondaryContainer = Color(0xFFBAF7D9),
                background = FocoTokens.FondoOscuro,
                surface = FocoTokens.SuperficieOscura,
                surfaceVariant = Color(0xFF303643),
                onSurface = Color(0xFFF4F6FC),
                onBackground = Color(0xFFF4F6FC),
                onSurfaceVariant = Color(0xFFB7BFCE),
                outline = Color(0xFF818B9E),
                outlineVariant = Color(0xFF3C4352),
                error = Color(0xFFFFB4BD),
                errorContainer = Color(0xFF5C2432),
                onErrorContainer = Color(0xFFFFD9DF),
            )
        else
            lightColorScheme(
                primary = FocoTokens.Acento,
                onPrimary = FocoTokens.Superficie,
                primaryContainer = Color(0xFFDFE7FF),
                onPrimaryContainer = Color(0xFF102967),
                secondary = Color(0xFF08764C),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFC5F4DF),
                onSecondaryContainer = Color(0xFF003822),
                background = FocoTokens.Fondo,
                surface = FocoTokens.Superficie,
                surfaceVariant = Color(0xFFDFE5F0),
                onSurface = FocoTokens.Texto,
                onBackground = FocoTokens.Texto,
                onSurfaceVariant = FocoTokens.TextoSecundario,
                outline = Color(0xFF717E93),
                outlineVariant = Color(0xFFD5DDEA),
                error = Color(0xFFB31F45),
                errorContainer = Color(0xFFFFD9E1),
                onErrorContainer = Color(0xFF490018),
            )
    val colors =
        if (dark && amoled)
            base.copy(
                background = Color.Black,
                surface = Color(0xFF111318),
                surfaceVariant = Color(0xFF1E222B),
                surfaceContainer = Color(0xFF0B0D11),
                outlineVariant = Color(0xFF2A2F3A),
            )
        else base
    MaterialTheme(
        colorScheme = colors,
        shapes =
            Shapes(
                medium = RoundedCornerShape(FocoTokens.RadioBoton),
                large = RoundedCornerShape(FocoTokens.RadioTarjeta),
            ),
        typography =
            Typography(
                displaySmall =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        letterSpacing = (-1.2).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                headlineLarge =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 29.sp,
                        lineHeight = 35.sp,
                        letterSpacing = (-.8).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                headlineMedium =
                    TextStyle(
                        fontSize = 26.sp,
                        lineHeight = 33.sp,
                        letterSpacing = (-.5).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                titleLarge =
                    TextStyle(
                        fontSize = 21.sp,
                        lineHeight = 27.sp,
                        letterSpacing = (-.3).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                titleMedium =
                    TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                labelSmall =
                    TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        letterSpacing = .8.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
            ),
        content = content,
    )
}
