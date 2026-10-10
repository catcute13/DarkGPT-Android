
package com.darkgpt.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences("darkgpt", Context.MODE_PRIVATE)
    }
    private lateinit var output: TextView
    private lateinit var prompt: EditText
    private val bg = Color.rgb(15, 18, 28)
    private val purple = Color.rgb(110, 80, 255)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMain()
    }

    private fun showMain() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 20, 16, 12)
            setBackgroundColor(bg)
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        header.addView(TextView(this).apply {
            text = "DarkGPT"
            textSize = 27f
            setTextColor(white)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, -2, 1f))

        val settings = Button(this).apply {
            text = "Cài đặt"
            setOnClickListener { showSettings() }
        }
        header.addView(settings)
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "AI lập trình game • Hỗ trợ tạo mã nguồn"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 12)
        })

        output = TextView(this).apply {
            text = "Xin chào! Đây là DarkGPT.\nMở Cài đặt để kết nối dịch vụ AI, sau đó nhập yêu cầu của bạn."
            textSize = 15f
            setTextColor(white)
            setPadding(12, 12, 12, 12)
        }
        val scroll = ScrollView(this)
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        prompt = EditText(this).apply {
            hint = "Nhập yêu cầu..."
            setTextColor(white)
            setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(35, 39, 54))
            maxLines = 4
        }
        row.addView(prompt, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = "Gửi"
            setOnClickListener { sendPrompt() }
        })
        root.addView(row)
        setContentView(root)
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 20)
            setBackgroundColor(bg)
        }

        fun field(label: String, hintText: String, key: String): EditText {
            layout.addView(TextView(this).apply {
                text = label
                setTextColor(white)
                textSize = 14f
                setPadding(0, 8, 0, 4)
            })
            return EditText(this).apply {
                hint = hintText
                setText(prefs.getString(key, ""))
                setTextColor(white)
                setHintTextColor(Color.LTGRAY)
            }.also { layout.addView(it) }
        }

        val base = field("API Base URL", "https://api.example.com/v1", "base")
        val key = field("API Key", "API key của bạn", "key")
        val model = field("Model", "Tên model được hỗ trợ", "model")

        layout.addView(Button(this).apply {
            text = "Lưu"
            setOnClickListener {
                prefs.edit()
                    .putString("base", base.text.toString().trim().trimEnd('/'))
                    .putString("key", key.text.toString().trim())
                    .putString("model", model.text.toString().trim())
                    .apply()
                showMain()
                Toast.makeText(this@MainActivity, "Đã lưu", Toast.LENGTH_SHORT).show()
            }
        })
        layout.addView(Button(this).apply {
            text = "Quay lại"
            setOnClickListener { showMain() }
        })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun sendPrompt() {
        val question = prompt.text.toString().trim()
        if (question.isEmpty()) return

        val base = prefs.getString("base", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""

        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            Toast.makeText(this, "Hãy cài đặt API trước", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }

        output.append("\n\nBạn:\n$question\n\nDarkGPT: Đang xử lý...")
        prompt.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(prompt.windowToken, 0)

        thread {
            val answer = try {
                val conn = (URL("$base/chat/completions")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $key")
                }

                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put(
                        "content",
                        "Bạn là DarkGPT, trợ lý lập trình game. Hỗ trợ dự án riêng, mod hợp pháp, mã nguồn và giải thích lỗi. Không hỗ trợ đánh cắp tài khoản, malware hoặc né anti-cheat."
                    ))
                    .put(JSONObject().put("role", "user").put("content", question))

                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messages)

                conn.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw Exception("HTTP $code: $raw")

                JSONObject(raw).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message")
                    .optString("content", "AI không trả lời.")
            } catch (e: Exception) {
                "Lỗi kết nối: ${e.message}"
            }

            runOnUiThread {
                output.append("\n\n$answer")
            }
        }
    }
}
