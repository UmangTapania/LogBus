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
import androidx.lifecycle.lifecycleScope
import com.github.umangtapania.logbus.config.LogStyleConfig
import com.github.umangtapania.logbus.core.LogBus
import com.github.umangtapania.logbus.core.LogLevel
import com.github.umangtapania.logbus.route.CrashlyticsRoute
import com.github.umangtapania.logbus.route.FileRoute
import com.github.umangtapania.logbus.route.LogcatRoute
import com.github.umangtapania.logbus.style.LogBoxStyle
import com.github.umangtapania.logbus.style.LogcatStyle
import com.github.umangtapania.logbusdemo.ui.theme.LogBusDemoTheme
import kotlinx.coroutines.launch

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
                                lifecycleScope.launch {
                                    LogcatRoute().log(LogLevel.DEBUG,"SPECIAL LOG", "This is a specific log route triggering")
                                }
                                LogBus.i("This is a demo log, lets make it a little bit longer, it shouold cover multiple lines, this is just for testing, so ignore any bugs found in it, is this length enough?, no, lets make it even longer, i want it to be super loooooooong")
                                LogBus.a("This is a demo log, lets make it a little bit longer, it shouold cover multiple lines, this is just for testing, so ignore any bugs found in it, is this length enough?, no, lets make it even longer, i want it to be super loooooooong")
                                LogBus.d("This is a demo log, lets make it a little bit longer, it shouold cover multiple lines, this is just for testing, so ignore any bugs found in it, is this length enough?, no, lets make it even longer, i want it to be super loooooooong")
                                LogBus.w("This is a demo log, lets make it a little bit longer, it shouold cover multiple lines, this is just for testing, so ignore any bugs found in it, is this length enough?, no, lets make it even longer, i want it to be super loooooooong")
                                LogBus.e("This is a demo log, lets make it a little bit longer, it shouold cover multiple lines, this is just for testing, so ignore any bugs found in it, is this length enough?, no, lets make it even longer, i want it to be super loooooooong")
                            }
                        )
                    }
                }
            }
        }
//        LogBus.addRoute(LogcatRoute())
        LogBus.initialize(
            listOf( LogcatRoute() )
        )
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