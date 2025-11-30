package com.example.myapp01

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapp01.ui.theme.MyApp01Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApp01Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CounterScreen()
                }
            }
        }
    }
}

@Composable
fun CounterScreen() {
    var count by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "$count")
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Button(
                onClick = {
                    count += 1
                    Log.d("TAG", "CounterScreen: $count")
                }) {

                Text("Increment")
            }
            Button(
                onClick = {
                    count = 0
                }) {
                Text("Reset")
            }
            Button(
                onClick = {
                    count -= 1
                    Log.d("TAG", "CounterScreen: $count")
                }) {

                Text("Decrement")
            }
        }


    }

}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CounterScreenPreview() {
    MyApp01Theme {
        CounterScreen()
    }
}