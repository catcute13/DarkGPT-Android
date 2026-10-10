package com.darkgpt.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences("darkgpt_settings", Context.MODE_PRIVATE)
    }

    private lateinit var root: LinearLayout
    private lateinit var messages: LinearLayout
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

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

        showChat()
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
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
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            minHeight = 48
            setOnClickListener { onTap() }
        }
    }

    private fun copyText(value: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("DarkGPT", value)
        )

        Toast.makeText(
            this,
            "Đã sao chép!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun applySafeArea(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                left,
                top + insets.systemWindowInsetTop,
                right,
                bottom + insets.systemWindowInsetBottom
            )
            insets
        }
    }

    private fun showChat() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        applySafeArea(root, 16, 8, 16, 10)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = makeText("DarkGPT", 26f, textColor, true)
        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        header.addView(button("⚙ Cài đặt") { showSettings() })
        root.addView(header)

        root.addView(
            makeText(
                "Trợ lý lập trình game • Mod hợp pháp • Tạo mã nguồn",
                12f,
                muted
            ).apply {
                setPadding(0, 4, 0, 12)
            }
        )

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(panel)
        }

        val welcome = "Xin chào! Tôi là DarkGPT.\n\n" +
            "Hãy nhập yêu cầu lập trình game, giải thích lỗi hoặc tạo mã nguồn. " +
            "Bạn cần cấu hình dịch vụ AI trong Cài đặt trước khi gửi tin nhắn."

        addMessage("DarkGPT", welcome)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(
                messages,
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        status = makeText("Chưa gửi yêu cầu", 12f, muted).apply {
            setPadding(0, 8, 0, 6)
        }
        root.addView(status)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
        root.requestApplyInsets()
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 18)
            setBackgroundColor(bg)
        }

        layout.addView(makeText("Cài đặt AI", 24f, textColor, true))

        layout.addView(
            makeText(
                "Nhập thông tin API của nhà cung cấp tương thích OpenAI. " +
                    "Không có API key thì ứng dụng chưa thể trả lời.",
                13f,
                muted
            ).apply {
                setPadding(0, 8, 0, 14)
            }
        )

        fun field(
            title: String,
            key: String,
            hintText: String,
            secret: Boolean = false
        ): EditText {
            layout.addView(
                makeText(title, 14f, textColor, true).apply {
                    setPadding(0, 8, 0, 4)
                }
            )

            return EditText(this).apply {
                hint = hintText
                setHintTextColor(muted)
                setTextColor(textColor)
                setSingleLine(true)
                setBackgroundColor(panel)
                setPadding(12, 8, 12, 8)
                setText(prefs.getString(key, "") ?: "")

                if (secret) {
                    inputType =
                        android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
        }

        val base = field(
            "API Base URL",
            "base",
            "Ví dụ: https://api.openai.com/v1"
        )

        val key = field(
            "API Key",
            "key",
            "Dán API key của bạn",
            true
        )

        val model = field(
            "Model",
            "model",
            "Tên model do nhà cung cấp cấp"
        )

        layout.addView(
            makeText(
                "Bảo mật: không chia sẻ API key. " +
                    "Dịch vụ có thể tính phí theo mức sử dụng.",
                12f,
                muted
            ).apply {
                setPadding(0, 12, 0, 14)
            }
        )

        layout.addView(button("Lưu và quay lại") {
            val baseValue = base.text.toString().trim().trimEnd('/')
            val keyValue = key.text.toString().trim()
            val modelValue = model.text.toString().trim()

            prefs.edit()
                .putString("base", baseValue)
                .putString("key", keyValue)
                .putString("model", modelValue)
                .apply()

            showChat()

            Toast.makeText(
                this,
                "Đã lưu cài đặt trên thiết bị",
                Toast.LENGTH_SHORT
            ).show()
        })

        layout.addView(button("Quay lại, không lưu") {
            showChat()
        })

        val settingsScroll = ScrollView(this).apply {
            addView(layout)
        }

        applySafeArea(settingsScroll, 0, 8, 0, 8)
        setContentView(settingsScroll)
        settingsScroll.requestApplyInsets()
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return

        val base = prefs.getString("base", "") ?: ""
        val apiKey = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""

        if (base.isBlank() || apiKey.isBlank() || model.isBlank()) {
            Toast.makeText(
                this,
                "Mở Cài đặt và nhập API Base URL, API Key, Model trước.",
                Toast.LENGTH_LONG
            ).show()
            showSettings()
            return
        }

        addMessage("Bạn", prompt)
        history.put(JSONObject().put("role", "user").put("content", prompt))
        input.setText("")

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        status.text = "Đang chờ AI trả lời..."

        val sendHistory = JSONArray(history.toString())

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

                val system = JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Bạn là DarkGPT, trợ lý lập trình game. " +
                            "Trả lời bằng ngôn ngữ người dùng. " +
                            "Khi tạo code, hãy đặt code trong khối Markdown " +
                            "bắt đầu và kết thúc bằng ba dấu backtick. " +
                            "Ưu tiên code hoàn chỉnh và hướng dẫn rõ ràng. " +
                            "Hỗ trợ mod hợp pháp, dự án riêng, mod offline và API được phép. " +
                            "Không hướng dẫn đánh cắp tài khoản, malware, né anti-cheat " +
                            "hoặc gian lận trái phép trong game online."
                    )

                val msgs = JSONArray().put(system)

                for (i in 0 until sendHistory.length()) {
                    msgs.put(sendHistory.getJSONObject(i))
                }

                val body = JSONObject()
                    .put("model", model)
                    .put("messages", msgs)
                    .put("temperature", 0.4)

                conn.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val raw = stream.bufferedReader().use { it.readText() }

                if (code !in 200..299) {
                    throw Exception("HTTP $code: " + raw.take(600))
                }

                val json = JSONObject(raw)
                answer = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "AI không trả về nội dung.")

                history.put(
                    JSONObject().put("role", "assistant").put("content", answer)
                )
            } catch (e: Exception) {
                answer = "Lỗi kết nối AI: ${e.message ?: "không rõ nguyên nhân"}\n\n" +
                    "Kiểm tra Base URL, API key, model và kết nối mạng."
            }

            runOnUiThread {
                addMessage("DarkGPT", answer)
                status.text = "Hoàn tất"
            }
        }
    }

    private fun addMessage(who: String, content: String) {
        messages.addView(
            makeText("$who:", 15f, accent, true).apply {
                setPadding(0, 8, 0, 6)
            }
        )

        val parts = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
            .findAll(content)
            .toList()

        if (parts.isEmpty()) {
            messages.addView(
                makeText(content, 15f, textColor).apply {
                    setTextIsSelectable(true)
                    setPadding(0, 0, 0, 8)
                }
            )
        } else {
            var cursor = 0

            for (part in parts) {
                val before = content.substring(cursor, part.range.first)

                if (before.isNotBlank()) {
                    messages.addView(
                        makeText(before.trim(), 15f, textColor).apply {
                            setTextIsSelectable(true)
                            setPadding(0, 0, 0, 8)
                        }
                    )
                }

                val language = part.groupValues[1].trim()
                val code = part.groupValues[2].trimEnd('\n', '\r')

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(10, 8, 10, 10)
                    setBackgroundColor(Color.rgb(8, 11, 18))
                }

                val codeHeader = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                codeHeader.addView(
                    makeText(
                        if (language.isBlank()) "CODE" else language,
                        12f,
                        muted,
                        true
                    ),
                    LinearLayout.LayoutParams(0, -2, 1f)
                )

                codeHeader.addView(
                    button("📋 Sao chép") {
                        copyText(code)
                    }
                )

                card.addView(codeHeader)

                val codeText = makeText(code, 13f, textColor).apply {
                    typeface = Typeface.MONOSPACE
                    setTextIsSelectable(true)
                    setPadding(0, 10, 0, 8)
                    gravity = Gravity.START
                }

                val horizontalScroll = HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = true
                    addView(codeText)
                }

                card.addView(horizontalScroll)

                messages.addView(
                    card,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, 4, 0, 12)
                    }
                )

                cursor = part.range.last + 1
            }

            val remaining = content.substring(cursor)

            if (remaining.isNotBlank()) {
                messages.addView(
                    makeText(remaining.trim(), 15f, textColor).apply {
                        setTextIsSelectable(true)
                        setPadding(0, 0, 0, 8)
                    }
                )
            }
        }

        messages.addView(button("📋 Sao chép toàn bộ câu trả lời") {
            copyText(content)
        }.apply {
            setPadding(4, 0, 4, 0)
        })

        messages.addView(
            View(this).apply {
                setBackgroundColor(Color.rgb(49, 55, 73))
            },
            LinearLayout.LayoutParams(-1, 1).apply {
                setMargins(0, 12, 0, 8)
            }
        )
    }
}
