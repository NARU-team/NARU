package ai.naru.app.comma

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared RESTING/WAKING status presentation; no session effects or actions. */
@Composable
internal fun RestingStatusCard(
    glyph:CommaGlyph,label:String,value:String,detail:String,modifier:Modifier
) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=Color.White,shadowElevation=3.dp) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(shape=RoundedCornerShape(50),color=CommaDesign.Soft) {
                CommaFallbackIcon(glyph,Modifier.padding(10.dp),CommaDesign.Ink)
            }
            Column {
                Text(label,color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
                Text(value,style=MaterialTheme.typography.titleLarge)
                Text(detail,color=CommaDesign.Muted,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun RestingStatusPill(glyph:CommaGlyph,text:String) {
    Surface(color=CommaDesign.Ink.copy(alpha=0.05f),shape=RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            CommaFallbackIcon(glyph,Modifier.size(20.dp),CommaDesign.Ink)
            Text(text,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

