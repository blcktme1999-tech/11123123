package tw.cib.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val api = ApiClient()
        ServiceApi.client = api
        setContent {
            var catalog by remember { mutableStateOf(Catalog.load(this@MainActivity)) }
            LaunchedEffect(Unit) {
                val fresh = withContext(Dispatchers.IO) {
                    runCatching { Catalog.refresh(this@MainActivity) }.getOrNull()
                }
                if (fresh != null) catalog = fresh
            }
            CibApp(catalog, api)
        }
    }
}
