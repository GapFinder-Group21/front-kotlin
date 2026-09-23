package co.gapfinder.mobile.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.R
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Pantalla de bienvenida (sin auto-login, igual que en Flutter). */
@Composable
fun LandingPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo
        Image(
            painter = painterResource(R.drawable.brand_full),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(28.dp)),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "GAP FINDER",
            style = Typo.heading(FontWeight.Black).copy(
                fontSize = 34.sp,
                color = Palette.White,
                letterSpacing = 2.sp,
            ),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Make your gaps count.",
            style = Typo.label(FontWeight.Normal).copy(
                fontSize = 16.sp,
                color = Palette.White.fade(0.65f),
                letterSpacing = 0.5.sp,
            ),
        )

        Spacer(Modifier.height(64.dp))

        PillButton(
            label = "Get Started",
            onClick = { StackNavigator.go(Destination.SignUp) },
        )

        Spacer(Modifier.height(16.dp))

        // Enlace a login (TextButton sin padding)
        Box(
            modifier = Modifier.inkTap(RoundedCornerShape(50)) { StackNavigator.go(Destination.SignIn) },
        ) {
            Text(
                text = "Already have an account? Log In",
                style = Typo.label(FontWeight.Normal).copy(
                    fontSize = 14.sp,
                    color = Palette.White.fade(0.55f),
                ),
            )
        }
    }
}
