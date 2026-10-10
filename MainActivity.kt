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

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var chatScroll: ScrollView

    private var history = JSONArray()

    private val bg = Color.rgb(13, 16, 25)
    private val panel = Color.rgb(25, 30, 44)
    private val accent = Color.rgb(116, 91, 255)
    private val textColor = Color.rgb(238, 241, 250)
    private val muted = Color.rgb(164, 172, 195)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(true)
        }

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

    private fun button(
        label: String,
        onTap: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(accent)
            minHeight = dp(48)
            setOnClickListener { onTap() }
        }
    }

    private fun copyText(value: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("DarkGPT code", value)
        )

        Toast.makeText(
            this,
            "Đã sao chép vào bộ nhớ tạm",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showChat() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            fitsSystemWindows = true
        }

        // Hàng tiêu đề
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            makeText("DarkGPT", 26f, textColor, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        root.addView(header)

        // Cài đặt nằm ở hàng riêng, không còn sát góc trên
        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }

        settingsRow.addView(
            button("⚙ Cài đặt") {
                showSettings()
            }
        )

        root.addView(settingsRow)

        root.addView(
            makeText(
                "Trợ lý lập trình game • Mod hợp pháp • Tạo mã nguồn",
                12f,
                muted
            ).apply {
                setPadding(0, 0, 0, dp(10))
            }
        )

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(panel)
        }

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(
                messages,
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            chatScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        status = makeText("Chưa gửi yêu cầu", 12f, muted).apply {
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(status)

        // Hàng nhập tin nhắn luôn nằm cuối màn hình
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        input = EditText(this).apply {
            hint = "Nhập yêu cầu..."
            setHintTextColor(muted)
            setTextColor(textColor)
            textSize = 15f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(panel)
            minLines = 1
            maxLines = 4
            gravity = Gravity.TOP
            isVerticalScrollBarEnabled = true
        }

        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        inputRow.addView(
            button("Gửi") { sendMessage() }
        )

        root.addView(inputRow)

        setContentView(root)

        addMessage(
            "DarkGPT",
            "Xin chào! Tôi là DarkGPT.\n\n" +
                "Hãy nhập yêu cầu lập trình game, giải thích lỗi hoặc tạo mã nguồn. " +
                "Mở Cài đặt để cấu hình dịch vụ AI trước khi gửi."
        )

        // Khi bàn phím mở, đưa vùng chat tới tin nhắn cuối.
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                chatScroll.post {
                    chatScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(bg)
        }

        layout.addView(
            makeText("Cài đặt AI", 24f, textColor, true)
        )

        layout.addView(
            makeText(
                "Nhập thông tin API của nhà cung cấp tương thích OpenAI.",
                13f,
                muted
            ).apply {
                setPadding(0, dp(8), 0, dp(12))
            }
        )

        fun field(
            title: String,
            keyName: String,
            hintText: String,
            secret: Boolean = false
        ): EditText {
            layout.addView(
                makeText(title, 14f, textColor, true).apply {
                    setPadding(0, dp(8), 0, dp(4))
                }
            )

            return EditText(this).apply {
                hint = hintText
                setHintTextColor(muted)
                setTextColor(textColor)
                setSingleLine(true)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setBackgroundColor(panel)
                setText(prefs.getString(keyName, "") ?: "")

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
                "Không chia sẻ API key. Dịch vụ có thể tính phí.",
                12f,
                muted
            ).apply {
                setPadding(0, dp(12), 0, dp(12))
            }
        )

        layout.addView(button("Lưu và quay lại") {
            prefs.edit()
                .putString("base", base.text.toString().trim().trimEnd('/'))
                .putString("key", key.text.toString().trim())
                .putString("model", model.text.toString().trim())
                .apply()

            showChat()

            Toast.makeText(
                this,
                "Đã lưu cài đặt",
                Toast.LENGTH_SHORT
            ).show()
        })

        layout.addView(button("Quay lại, không lưu") {
            showChat()
        })

        val scroll = ScrollView(this).apply {
            addView(layout)
            fitsSystemWindows = true
        }

        setContentView(scroll)
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
                "Hãy nhập API Base URL, API Key và Model trong Cài đặt.",
                Toast.LENGTH_LONG
            ).show()
            showSettings()
            return
        }

        addMessage("Bạn", prompt)
        history.put(
            JSONObject().put("role", "user").put("content", prompt)
        )

        input.setText("")

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        status.text = "Đang chờ AI trả lời..."

        val sendHistory = JSONArray(history.toString())

        thread {
            val answer: String

            try {
                val connection =
                    (URL(base.trimEnd('/') + "/chat/completions")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 20000
                        readTimeout = 90000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }

                val messagesToSend = JSONArray().put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "Bạn là DarkGPT, trợ lý lập trình game. " +
                                "Trả lời bằng ngôn ngữ người dùng. " +
                                "Đặt mã nguồn trong khối Markdown có ba dấu backtick. " +
                                "Ưu tiên code hoàn chỉnh, dễ sao chép và hướng dẫn rõ ràng. " +
                                "Hỗ trợ mod hợp pháp, dự án riêng và mod offline. " +
                                "Không hướng dẫn đánh cắp tài khoản, malware, né anti-cheat " +
                                "hoặc gian lận trái phép trong game online."
                        )
                )

                for (i in 0 until sendHistory.length()) {
                    messagesToSend.put(sendHistory.getJSONObject(i))
                }

                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messagesToSend)
                    .put("temperature", 0.4)

                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val raw = stream.bufferedReader().use { it.readText() }

                if (responseCode !in 200..299) {
                    throw Exception("HTTP $responseCode: " + raw.take(600))
                }

                answer = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "AI không trả về nội dung.")

                history.put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", answer)
                )
            } catch (e: Exception) {
                answer =
                    "Lỗi kết nối AI: ${e.message ?: "không rõ nguyên nhân"}\n\n" +
                    "Kiểm tra Base URL, API key, model và kết nối mạng."
            }

            runOnUiThread {
                addMessage("DarkGPT", answer)
                status.text = "Hoàn tất"
                chatScroll.post {
                    chatScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun addSelectableText(value: String) {
        val textView = makeText(value, 15f, textColor).apply {
            setTextIsSelectable(true)
            isLongClickable = true
            setPadding(0, 0, 0, dp(8))
        }
        messages.addView(textView)
    }

    private fun addMessage(who: String, content: String) {
        messages.addView(
            makeText("$who:", 15f, accent, true).apply {
                setPadding(0, dp(8), 0, dp(6))
            }
        )

        val pattern = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
        val blocks = pattern.findAll(content).toList()

        if (blocks.isEmpty()) {
            addSelectableText(content)
        } else {
            var cursor = 0

            for (block in blocks) {
                val before = content.substring(cursor, block.range.first)

                if (before.isNotBlank()) {
                    addSelectableText(before.trim())
                }

                val language = block.groupValues[1].trim()
                val code = block.groupValues[2].trimEnd('\n', '\r')

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(10))
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

                // EditText chỉ đọc: cho phép chọn đoạn văn bản bằng cách nhấn giữ.
                val codeEditor = EditText(this).apply {
                    setText(code)
                    textSize = 13f
                    setTextColor(textColor)
                    typeface = Typeface.MONOSPACE
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(0, dp(10), 0, dp(8))
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    isLongClickable = true
                    keyListener = null
                    setTextIsSelectable(true)
                    setSelection(0)
                    minLines = 1
                    maxLines = 30
                    gravity = Gravity.TOP or Gravity.START
                }

                card.addView(
                    codeEditor,
                    LinearLayout.LayoutParams(-1, -2)
                )

                messages.addView(
                    card,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        setMargins(0, dp(4), 0, dp(12))
                    }
                )

                cursor = block.range.last + 1
            }

            val remaining = content.substring(cursor)
            if (remaining.isNotBlank()) {
                addSelectableText(remaining.trim())
            }
        }

        messages.addView(button("📋 Sao chép toàn bộ câu trả lời") {
            copyText(content)
        })

        messages.addView(
            View(this).apply {
                setBackgroundColor(Color.rgb(49, 55, 73))
            },
            LinearLayout.LayoutParams(-1, dp(1)).apply {
                setMargins(0, dp(12), 0, dp(8))
            }
        )
    }
}
