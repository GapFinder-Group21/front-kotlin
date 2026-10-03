package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Aviso "Friends Nearby!" con los amigos que están en el edificio al que se acaba de entrar. */
@Composable
fun NearbyFriendsDialog(spotName: String, friends: List<Member>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.White, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .background(Palette.Teal.fade(0.15f), CircleShape)
                        .padding(12.dp)
                ) {
                    Icon(Glyphs.peopleRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Friends Nearby!",
                        style = Typo.heading(FontWeight.Black).copy(fontSize = 18.sp, color = Palette.Ink),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "You entered $spotName",
                        style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink.fade(0.5f)),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "${friends.size} friends are in this building right now:",
                style = Typo.label(FontWeight.Bold).copy(fontSize = 13.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                items(friends) { friend -> NearbyFriendRow(friend) }
            }

            Spacer(Modifier.height(20.dp))
            PillButton(label = "Awesome", color = Palette.Teal, onClick = onDismiss)
        }
    }
}

@Composable
private fun NearbyFriendRow(friend: Member) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .background(Palette.Fog, shape)
            .border(1.dp, Palette.Ink.fade(0.08f), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Portrait(url = friend.avatarUrl, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend.name,
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 14.sp, color = Palette.Ink),
            )
            Text(
                text = "${friend.career} · Sem ${friend.semester}",
                style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.45f)),
            )
        }
    }
}
