package tw.cib.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val catalog = Catalog.load(this)
        val api = ApiClient()
        ServiceApi.client = api
        setContent {
            CibApp(catalog, api)
        }
    }
}
