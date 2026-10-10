package com.darkgpt.app

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.text.InputType
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
    private lateinit var rootLayout: LinearLayout

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
            isAllCaps = false
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
            "Đã sao chép vào bộ nhớ tạm",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun installInsets(root: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
                )

                view.setPadding(
                    dp(14),
                    dp(8) + bars.top,
                    dp(14),
                    dp(8) + bars.bottom
                )

                insets
            }
        } else {
            @Suppress("DEPRECATION")
            root.setOnApplyWindowInsetsListener { view, insets ->
                val top = insets.systemWindowInsetTop
                val bottom = insets.systemWindowInsetBottom

                view.setPadding(
                    dp(14),
                    dp(8) + top,
                    dp(14),
                    dp(8) + bottom
                )

                insets
            }
        }

        root.requestApplyInsets()
    }

    private fun showChat() {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            clipToPadding = false
        }

        // Tiêu đề ứng dụng
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            makeText("DarkGPT", 26f, textColor, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        rootLayout.addView(header)

        // Cài đặt nằm dưới tiêu đề, không nằm ở góc trên
        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(8))
        }

        settingsRow.addView(button("⚙ Cài đặt") {
            showSettings()
        })

        rootLayout.addView(settingsRow)

        rootLayout.addView(
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
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

            addView(
                messages,
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
                )
            )
        }

        rootLayout.addView(
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

        rootLayout.addView(status)

        // Ô nhập liệu nằm cuối giao diện
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
            gravity = Gravity.TOP or Gravity.START
            isVerticalScrollBarEnabled = true

            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE

            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        inputRow.addView(button("Gửi") {
            sendMessage()
        })

        rootLayout.addView(inputRow)

        setContentView(rootLayout)
        installInsets(rootLayout)

        addMessage(
            "DarkGPT",
            "Xin chào! Tôi là DarkGPT.\n\n" +
                "Hãy nhập yêu cầu lập trình game, giải thích lỗi hoặc tạo mã nguồn. " +
                "Mở Cài đặt để cấu hình dịch vụ AI trước khi gửi."
        )

        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                chatScroll.post {
                    chatScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
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

                inputType = if (secret) {
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT
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
                .putString(
                    "base",
                    base.text.toString().trim().trimEnd('/')
                )
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
            setBackgroundColor(bg)
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
            JSONObject()
                .put("role", "user")
                .put("content", prompt)
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

                        setRequestProperty(
                            "Content-Type",
                            "application/json"
                        )

                        setRequestProperty(
                            "Authorization",
                            "Bearer $apiKey"
                        )
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
                    messagesToSend.put(
                        sendHistory.getJSONObject(i)
                    )
                }

                val body = JSONObject()
                    .put("model", model)
                    .put("messages", messagesToSend)
                    .put("temperature", 0.4)

                connection.outputStream.use {
                    it.write(
                        body.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val responseCode = connection.responseCode

                val stream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val raw = stream?.bufferedReader()?.use {
                    it.readText()
                } ?: "Không nhận được nội dung phản hồi."

                if (responseCode !in 200..299) {
                    throw Exception(
                        "HTTP $responseCode: " + raw.take(600)
                    )
                }

                answer = JSONObject(raw)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString(
                        "content",
                        "AI không trả về nội dung."
                    )

                history.put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", answer)
                )

                connection.disconnect()
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

        // Nhận dạng khối Markdown, có hoặc không có tên ngôn ngữ.
        val pattern = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
        val blocks = pattern.findAll(content).toList()

        if (blocks.isEmpty()) {
            addSelectableText(content)
        } else {
            var cursor = 0

            for (block in blocks) {
                val before = content.substring(
                    cursor,
                    block.range.first
                )

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

                // TextView cho phép chọn từng đoạn bằng thao tác nhấn giữ.
                val codeView = TextView(this).apply {
                    text = code
                    textSize = 13f
                    setTextColor(textColor)
                    typeface = Typeface.MONOSPACE
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(0, dp(10), 0, dp(8))
                    setTextIsSelectable(true)
                    isLongClickable = true
                    isFocusable = true
                    gravity = Gravity.TOP or Gravity.START
                }

                card.addView(
                    codeView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
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

        messages.addView(
            button("📋 Sao chép toàn bộ câu trả lời") {
                copyText(content)
            }
        )

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
