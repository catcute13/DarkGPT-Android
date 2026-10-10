package com.darkgpt.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("darkgpt_settings", Context.MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var messages: TextView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var history = JSONArray()

    private val bg = Color.rgb(13, 16, 25)
    private val panel = Color.rgb(25, 30, 44)
    private val accent = Color.rgb(116, 91, 255)
    private val textColor = Color.rgb(238, 241, 250)
    private val muted = Color.rgb(164, 172, 195)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showChat()
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun button(label: String, onTap: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            setOnClickListener { onTap() }
        }
    }

    private fun showChat() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(16, 18, 16, 12)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = makeText("DarkGPT", 26f, textColor, true)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Cài đặt") { showSettings() })
        root.addView(header)

        root.addView(makeText("Trợ lý lập trình game • Mod hợp pháp • Tạo mã nguồn", 12f, muted).apply {
            setPadding(0, 4, 0, 12)
        })

        messages = makeText("Xin chào! Tôi là DarkGPT.\n\nHãy nhập yêu cầu lập trình game, giải thích lỗi hoặc tạo mã nguồn. Bạn cần cấu hình dịch vụ AI trong Cài đặt trước khi gửi tin nhắn.", 15f, textColor).apply {
            setPadding(14, 14, 14, 14)
            setBackgroundColor(panel)
            gravity = Gravity.TOP
        }
        val scroll = ScrollView(this)
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        status = makeText("Chưa gửi yêu cầu", 12f, muted)
        root.addView(status)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        input = EditText(this).apply {
            hint = "Nhập yêu cầu..."
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 15f
            setPadding(12, 8, 12, 8)
            setBackgroundColor(panel)
            minLines = 1
            maxLines = 5
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("Gửi") { sendMessage() })
        root.addView(row)
        setContentView(root)
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 18)
            setBackgroundColor(bg)
        }
        layout.addView(makeText("Cài đặt AI", 24f, textColor, true))
        layout.addView(makeText("Nhập thông tin API của nhà cung cấp tương thích OpenAI. Không có API key thì ứng dụng chưa thể trả lời.", 13f, muted).apply {
            setPadding(0, 8, 0, 14)
        })
        fun field(title: String, key: String, hintText: String, secret: Boolean = false): EditText {
            layout.addView(makeText(title, 14f, textColor, true).apply { setPadding(0, 8, 0, 4) })
            return EditText(this).apply {
                hint = hintText
                setHintTextColor(muted)
                setTextColor(textColor)
                setSingleLine(true)
                setBackgroundColor(panel)
                setPadding(12, 8, 12, 8)
                setText(prefs.getString(key, "") ?: "")
                if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }.also { /* value saved by Save button */ }
        }
        val base = field("API Base URL", "base", "Ví dụ: https://api.openai.com/v1")
        val key = field("API Key", "key", "Dán API key của bạn", true)
        val model = field("Model", "model", "Tên model do nhà cung cấp cấp")
        layout.addView(makeText("Bảo mật: không gửi API key cho người khác. Nếu dùng dịch vụ tính phí, yêu cầu gửi tin nhắn có thể phát sinh chi phí.", 12f, muted).apply {
            setPadding(0, 12, 0, 14)
        })
        layout.addView(button("Lưu và quay lại") {
            val baseValue = base.text.toString().trim().trimEnd('/')
            val keyValue = key.text.toString().trim()
            val modelValue = model.text.toString().trim()
            prefs.edit().putString("base", baseValue).putString("key", keyValue).putString("model", modelValue).apply()
            showChat()
            Toast.makeText(this, "Đã lưu cài đặt trên thiết bị", Toast.LENGTH_SHORT).show()
        })
        layout.addView(button("Quay lại, không lưu") { showChat() })
        setContentView(ScrollView(this).apply { addView(layout) })
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        val base = prefs.getString("base", "") ?: ""
        val apiKey = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""
        if (base.isBlank() || apiKey.isBlank() || model.isBlank()) {
            Toast.makeText(this, "Mở Cài đặt và nhập API Base URL, API Key, Model trước.", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }
        append("Bạn", prompt)
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        status.text = "Đang chờ AI trả lời..."
        val sendHistory = JSONArray(history.toString())
        sendHistory.put(JSONObject().put("role", "user").put("content", prompt))
        thread {
            var answer: String
            try {
                val url = URL(base.trimEnd('/') + "/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
                val system = JSONObject().put("role", "system").put(
                    "content",
                    "Bạn là DarkGPT, trợ lý lập trình game. Hãy trả lời bằng ngôn ngữ người dùng, ưu tiên hướng dẫn rõ ràng và mã nguồn hoàn chỉnh. Hỗ trợ modding hợp pháp, dự án riêng, mod offline/single-player và API được phép. Không hướng dẫn đánh cắp tài khoản, malware, né anti-cheat hoặc gian lận trái phép trong game online."
                )
                val msgs = JSONArray().put(system)
                for (i in 0 until sendHistory.length()) msgs.put(sendHistory.getJSONObject(i))
                val body = JSONObject().put("model", model).put("messages", msgs).put("temperature", 0.4)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val raw = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) throw Exception("HTTP $code: " + raw.take(600))
                val json = JSONObject(raw)
                answer = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "AI không trả về nội dung.")
                history.put(JSONObject().put("role", "user").put("content", prompt))
                history.put(JSONObject().put("role", "assistant").put("content", answer))
            } catch (e: Exception) {
                answer = "Lỗi kết nối AI: ${e.message ?: "không rõ nguyên nhân"}\n\nKiểm tra Base URL, API key, model và kết nối mạng."
            }
            runOnUiThread {
                append("DarkGPT", answer)
                status.text = "Hoàn tất"
            }
        }
    }

    private fun append(who: String, content: String) {
        val old = messages.text.toString()
        messages.text = "$old\n\n$who:\n$content"
    }
}
