package com.wenes.escala.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wenes.escala.data.CorDestaque
import com.wenes.escala.data.FundoEscuro
import com.wenes.escala.data.TemaApp

// Mesmo tema dos outros apps: fundo escuro e destaque escolhidos à parte.

private val Branco = Color.White

val FundoEscuro.cor: Color get() = Color(argb)

/** Tom forte (botões e seleções com texto branco), igual no claro e no escuro, exceto o Azul. */
private fun forte(c: CorDestaque, escuro: Boolean) = when (c) {
    CorDestaque.AZUL -> if (escuro) Color(0xFF2F6FEB) else Color(0xFF3D7FEF)
    CorDestaque.VERDE -> Color(0xFF0E9F6E)
    CorDestaque.ROXO -> Color(0xFF8E6BD6)
    CorDestaque.TURQUESA -> Color(0xFF1F8F85)
    CorDestaque.LARANJA -> Color(0xFFD9803A)
    CorDestaque.ROSA -> Color(0xFFD9638A)
    CorDestaque.AUTOMATICA -> Color(0xFF3D7FEF)
}

/** Tom claro (texto, links e ícones no escuro). */
private fun claroNoEscuro(c: CorDestaque) = when (c) {
    CorDestaque.AZUL, CorDestaque.AUTOMATICA -> Color(0xFF60A5FA)
    CorDestaque.VERDE -> Color(0xFF4ADE80)
    CorDestaque.ROXO -> Color(0xFFB794F6)
    CorDestaque.TURQUESA -> Color(0xFF4DB6AC)
    CorDestaque.LARANJA -> Color(0xFFF4A261)
    CorDestaque.ROSA -> Color(0xFFFB7185)
}

private fun clarear(base: Color, f: Float) = lerp(base, Color.White, f)

private val BaseClara = lightColorScheme().copy(
    background = Color(0xFFF1F3F4), onBackground = Color(0xFF1B1C1E),
    surface = Color(0xFFF1F3F4), onSurface = Color(0xFF1B1C1E),
    surfaceVariant = Color(0xFFE1E3E6), onSurfaceVariant = Color(0xFF44474A),
    outline = Color(0xFF75777B), outlineVariant = Color(0xFFC5C7CA),
    surfaceDim = Color(0xFFD9DADD), surfaceBright = Color(0xFFF8F9FA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F8F9),
    surfaceContainer = Color(0xFFECEDEF), surfaceContainerHigh = Color(0xFFE6E7EA),
    surfaceContainerHighest = Color(0xFFE0E2E4),
    error = Color(0xFFC0463C), errorContainer = Color(0xFFFADAD6),
)

private fun baseEscura(f: Color) = darkColorScheme().copy(
    background = f, surface = f, surfaceDim = f,
    surfaceContainerLowest = f, surfaceContainerLow = clarear(f, .07f),
    surfaceContainer = clarear(f, .04f), surfaceContainerHigh = clarear(f, .11f),
    surfaceContainerHighest = clarear(f, .15f), surfaceBright = clarear(f, .20f),
    surfaceVariant = clarear(f, .15f), outlineVariant = clarear(f, .15f),
    onBackground = Color(0xFFF2F4F8), onSurface = Color(0xFFF2F4F8), onSurfaceVariant = Color(0xFFC5CAD6),
    outline = Color(0xFF8E9195),
    error = Color(0xFFFF8A80), errorContainer = Color(0xFF5C1F1B),
)

/** Esquema de cores do app. Automática usa o papel de parede no Android 12+. */
fun esquemaDe(cor: CorDestaque, escuro: Boolean, fundo: FundoEscuro, ctx: Context? = null): ColorScheme {
    val base = if (escuro) baseEscura(fundo.cor) else BaseClara
    if (cor == CorDestaque.AUTOMATICA && ctx != null && Build.VERSION.SDK_INT >= 31) {
        val d = if (escuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        return if (escuro) d.copy(
            background = base.background, surface = base.surface, surfaceDim = base.surfaceDim,
            surfaceContainerLowest = base.surfaceContainerLowest, surfaceContainerLow = base.surfaceContainerLow,
            surfaceContainer = base.surfaceContainer, surfaceContainerHigh = base.surfaceContainerHigh,
            surfaceContainerHighest = base.surfaceContainerHighest, surfaceBright = base.surfaceBright,
            surfaceVariant = base.surfaceVariant, outlineVariant = base.outlineVariant,
        ) else d
    }
    val f = forte(cor, escuro)
    val t = if (escuro) claroNoEscuro(cor) else f
    return base.copy(
        primary = f, onPrimary = Branco, primaryContainer = f, onPrimaryContainer = Branco,
        secondary = t, onSecondary = if (escuro) Color(0xFF101214) else Branco,
        secondaryContainer = f, onSecondaryContainer = Branco,
        tertiary = t, onTertiary = if (escuro) Color(0xFF101214) else Branco,
        tertiaryContainer = f, onTertiaryContainer = Branco,
        inversePrimary = t,
    )
}

/** Cor da bolinha na escolha do destaque, conforme o modo atual. */
fun amostraDe(cor: CorDestaque, escuro: Boolean): Color = if (escuro) claroNoEscuro(cor) else forte(cor, false)

fun nomeDaCor(cor: CorDestaque) = when (cor) {
    CorDestaque.AUTOMATICA -> "Automática"
    CorDestaque.AZUL -> "Azul"
    CorDestaque.VERDE -> "Verde"
    CorDestaque.ROXO -> "Roxo"
    CorDestaque.TURQUESA -> "Turquesa"
    CorDestaque.LARANJA -> "Laranja"
    CorDestaque.ROSA -> "Rosa"
}

/** O tema está escuro agora? (Escuro, ou Sistema com o sistema escuro.) */
@Composable
fun temaEscuro(tema: TemaApp): Boolean = when (tema) {
    TemaApp.SISTEMA -> isSystemInDarkTheme()
    TemaApp.CLARO -> false
    TemaApp.ESCURO -> true
}

// Fonte padrão do Material, sem alterações, como no Organizador Pro.
private val Tipografia = Typography()

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EscalaTheme(cor: CorDestaque, tema: TemaApp, fundo: FundoEscuro, conteudo: @Composable () -> Unit) {
    val escuro = temaEscuro(tema)
    MaterialTheme(
        colorScheme = esquemaDe(cor, escuro, fundo, LocalContext.current),
        typography = Tipografia,
        shapes = Formas,
        content = conteudo,
    )
}
