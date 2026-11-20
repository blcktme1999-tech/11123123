package tw.cib.reader

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient {
    private val cookies = mutableListOf<Cookie>()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                this@ApiClient.cookies.removeAll { old -> cookies.any { it.name == old.name } }
                this@ApiClient.cookies.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.toList()
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun clearSession() {
        cookies.clear()
    }

    fun get(path: String): JSONObject = call(Request.Builder().url(base + path).get().build())

    fun post(path: String, body: JSONObject): JSONObject = call(
        Request.Builder().url(base + path).post(body.toString().toRequestBody(jsonType)).build()
    )

    private val base = BuildConfig.SITE_URL.trimEnd('/')

    private fun call(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.trim().startsWith("{")) JSONObject(text) else JSONObject()
            if (!response.isSuccessful) {
                throw IllegalStateException(json.optString("error", "請求失敗（${response.code}）"))
            }
            return json
        }
    }

}

object ServiceApi {
    lateinit var client: ApiClient
}

fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
