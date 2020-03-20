package es.everis.sample_webview_payment

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    var mockWebServer: MockWebServer? = null
    var baseUrl: HttpUrl? = null

    companion object {
        const val PORT = 8000
        const val PAYMENT_ENDPOINT = "payment"
        const val RESPONSE_ENDPOINT = "response"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initMockServer()

        initWebView()

        progressBar.visibility = View.VISIBLE
        val data = readLocalFile("request_post.json")
        webview?.postUrl("${baseUrl}${PAYMENT_ENDPOINT}", data.toByteArray(Charsets.UTF_8))
    }

    private fun initMockServer() {
        startServer()
    }

    private fun initWebView() {
        webview?.apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar.visibility = View.VISIBLE
                    this@MainActivity.title = "Loading..."
                    super.onPageStarted(view, url, favicon)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar.visibility = View.GONE
                    this@MainActivity.title = url
                    super.onPageFinished(view, url)
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url
                    if (url.path == "/$RESPONSE_ENDPOINT") {
                        progressBar.visibility = View.VISIBLE

                        GlobalScope.launch(Dispatchers.IO) {
                            val urlConnection = URL(url.toString()).openConnection()
                            urlConnection.connect()

                            val response = BufferedInputStream(urlConnection.getInputStream()).run {
                                ByteArrayOutputStream().apply {
                                    write(readBytes())
                                }.toString()
                            }

                            Log.d("RESPONSE", response)
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                this@MainActivity.title = "Finish"
                                response_text.visibility = View.VISIBLE
                                response_text.text = response
                            }
                        }

                        return true
                    } else {
                        return false
                    }
                }
            }

            settings.apply {
                javaScriptEnabled = true
            }
        }
    }

    private fun startServer() = runBlocking(Dispatchers.IO) {
        try {
            mockWebServer = MockWebServer()
            mockWebServer?.start(PORT)
            baseUrl = mockWebServer?.url("/")
            mockWebServer?.dispatcher = mockDispatcher()
        } catch (exception: IOException) {
            Log.e("MockWebServer", "mock server start error  ${exception.message}")
        }
    }

    private fun stopServer() {
        try {
            mockWebServer?.shutdown()
        } catch (exception: IOException) {
            Log.e("MockWebServer", "mock server shutdown error ${exception.message}")
        }
    }

    private fun mockDispatcher(): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/$PAYMENT_ENDPOINT" -> {
                        val body = request.body.readUtf8()
                        val response = readLocalFile("payment.html").run {
                            replace("##USERDATA##", body)
                        }
                        MockResponse().setResponseCode(200)
                            .setBodyDelay(2, TimeUnit.SECONDS)
                            .setBody(response)
                    }
                    "/$RESPONSE_ENDPOINT" -> {
                        val response = readLocalFile("response_post.json")
                        MockResponse().setResponseCode(200)
                            .setBodyDelay(3, TimeUnit.SECONDS)
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody(response)
                    }
                    else -> {
                        MockResponse().setResponseCode(500)
                            .setBody("error")
                    }
                }
            }
        }
    }

    private fun readLocalFile(fileName: String): String {
        val inputStream = resources.assets.open(fileName)
        val buffer = ByteArray(inputStream.available())
        inputStream.read(buffer)
        return ByteArrayOutputStream().apply {
            write(buffer)
        }.toString()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}