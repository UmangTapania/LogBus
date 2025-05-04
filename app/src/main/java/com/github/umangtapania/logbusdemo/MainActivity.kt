package com.github.umangtapania.logbusdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.github.umangtapania.logbus.core.LogBus
import com.github.umangtapania.logbus.route.CrashlyticsRoute
import com.github.umangtapania.logbus.route.FileRoute
import com.github.umangtapania.logbus.route.LogcatRoute
import com.github.umangtapania.logbusdemo.ui.theme.LogBusDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LogBusDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()){
                        Greeting(
                            name = "Android",
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            onClick = {
                                LogBus.i("This is a demo log")
                                LogBus.e("This is message for crashlytics")
                            }
                        )
                    }
                }
            }
        }
        LogBus.addRoute(LogcatRoute())
        LogBus.addRoute(FileRoute(this))
        LogBus.addRoute(CrashlyticsRoute(this))


    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier, onClick : () -> Unit) {
    Text(
        text = "Hello $name!",
        modifier = modifier.clickable { onClick() }
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LogBusDemoTheme {
//        Greeting("Android")
    }
}