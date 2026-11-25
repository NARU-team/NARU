package com.naru.app.comma

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Figma-derived scoped tokens. System Sans and Canvas glyphs are explicit asset fallbacks. */
internal object CommaDesign {
    val Ink = Color(0xFF131417)
    val Muted = Ink.copy(alpha = 0.60f)
    val Blue = Color(0xFF0064FF)
    val Filled = Color(0xFF44464E)
    val Soft = Color(0xFFEDEEF2)
    val Suggestion = Color(0xFFF5F9FF)
    val Warm = Color(0xFFFBE6D2)
}

@Composable
internal fun CommaDesignTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = CommaDesign.Blue, onPrimary = Color.White,
            background = Color.White, surface = Color.White, onSurface = CommaDesign.Ink,
            onBackground = CommaDesign.Ink, surfaceVariant = CommaDesign.Soft,
            onSurfaceVariant = CommaDesign.Muted),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=30.sp,lineHeight=38.sp),
            titleLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=22.sp,lineHeight=30.sp),
            titleMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=18.sp,lineHeight=26.sp),
            bodyLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=16.sp,lineHeight=24.sp),
            bodyMedium = TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp),
            labelLarge = TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=16.sp,lineHeight=22.sp)
        ), content = content)
}

@Composable
internal fun CommaPanel(modifier: Modifier = Modifier, tint: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape=RoundedCornerShape(22.dp), color=tint, shadowElevation=3.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
internal fun CommaButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min=52.dp), enabled=enabled, shape=RoundedCornerShape(20.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=if(filled) CommaDesign.Filled else CommaDesign.Ink.copy(alpha=0.05f),
            contentColor=if(filled) Color.White else CommaDesign.Ink,
            disabledContainerColor=CommaDesign.Soft.copy(alpha=0.55f), disabledContentColor=CommaDesign.Muted),
        contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge)
    }
}

internal enum class CommaGlyph { CLOCK, TEMPERATURE, SCREEN, SOUND, LIGHT, SPARK, BATTERY }

/** Native fallback line drawings, not the original Naru vectors. */
@Composable
internal fun CommaFallbackIcon(glyph: CommaGlyph, modifier: Modifier = Modifier, color: Color = CommaDesign.Blue) {
    Canvas(modifier.size(28.dp)) {
        val u=size.minDimension/24f
        val stroke=Stroke(1.5f*u,cap=StrokeCap.Round)
        fun line(x:Float,y:Float,x2:Float,y2:Float)=drawLine(color,Offset(x*u,y*u),Offset(x2*u,y2*u),1.5f*u,StrokeCap.Round)
        when(glyph) {
            CommaGlyph.CLOCK -> { drawCircle(color,9*u,Offset(12*u,12*u),style=stroke);line(12f,6f,12f,12f);line(12f,12f,16f,14f) }
            CommaGlyph.TEMPERATURE -> { drawRoundRect(color,Offset(10*u,3*u),Size(4*u,13*u),CornerRadius(2*u),style=stroke);drawCircle(color,4*u,Offset(12*u,17*u),style=stroke);line(12f,9f,12f,18f) }
            CommaGlyph.SCREEN -> { drawRoundRect(color,Offset(3*u,4*u),Size(18*u,13*u),CornerRadius(2*u),style=stroke);line(12f,17f,12f,21f);line(8f,21f,16f,21f) }
            CommaGlyph.SOUND -> for(i in 0..4) { val x=4f+i*4;val h=if(i%2==0) 6f else 12f;line(x,12-h/2,x,12+h/2) }
            CommaGlyph.LIGHT -> { drawCircle(color,6*u,Offset(12*u,9*u),style=stroke);line(9f,14f,10f,19f);line(15f,14f,14f,19f);line(10f,21f,14f,21f) }
            CommaGlyph.SPARK -> { line(12f,2f,12f,8f);line(12f,16f,12f,22f);line(2f,12f,8f,12f);line(16f,12f,22f,12f);line(7f,7f,9f,9f);line(15f,15f,17f,17f) }
            CommaGlyph.BATTERY -> { drawRoundRect(color,Offset(2*u,7*u),Size(18*u,10*u),CornerRadius(2*u),style=stroke);line(22f,10f,22f,14f);line(6f,10f,6f,14f);line(10f,10f,10f,14f);line(14f,10f,14f,14f) }
        }
    }
}
