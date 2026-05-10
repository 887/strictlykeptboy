package com.eight87.strictlykeptboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      StrictlyKeptBoyTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
          Splash(modifier = Modifier.padding(inner))
        }
      }
    }
  }
}

@Composable
private fun Splash(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = "strictlykeptboy", style = MaterialTheme.typography.displayMedium)
    Text(
      text = "git-backed calendar + timeboxing + todolist",
      style = MaterialTheme.typography.bodyLarge,
    )
    Text(
      text = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_DATE}",
      style = MaterialTheme.typography.labelSmall,
    )
  }
}

@Preview
@Composable
private fun SplashPreview() {
  StrictlyKeptBoyTheme { Splash() }
}
