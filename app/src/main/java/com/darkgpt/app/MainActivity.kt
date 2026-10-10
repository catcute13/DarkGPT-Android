
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
import android.content.Context.INPUT_METHOD_SERVICE
import android.widget.*
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("darkgpt", Context.MODE_PRIVATE)
    }

    private val bg = Color.rgb(15, 18, 28)
    private val panel = Color.rgb(25, 29, 43)
    private val codeBg = Color.rgb(9, 12, 20)
    private val purple = Color.rgb(110, 80, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(175, 181, 198)
    private val green = Color.rgb(100, 230, 160)

    private lateinit var root: LinearLayout
    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var prompt: EditText
    private lateinit var sendButton: Button

    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        window.statusBarColor = bg
        window.navigationBarColor = bg

        showMain()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun textView(
        textValue: String,
        size: Float,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
        }
    }

    private fun makeButton(
        label: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
    }

    private fun showMain() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(8))
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        // Tiêu đề riêng
        val title = textView("DarkGPT", 28f, white).apply {
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(-1, -2)
        )

        // Dòng mô tả
        root.addView(
            textView(
                "AI lập trình game • Hỗ trợ tạo mã nguồn",
                13f,
                muted
            ).apply {
                setPadding(0, dp(4), 0, dp(10))
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        // Nút Cài đặt nằm ở hàng RIÊNG bên dưới tiêu đề
        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val settingsButton = makeButton("⚙ Cài đặt") {
            showSettings()
        }

        settingsRow.addView(
            settingsButton,
            LinearLayout.LayoutParams(-2, -2)
        )

        root.addView(
            settingsRow,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            }
        )

        // Khu vực hội thoại có thể cuộn
        chatList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(12))
        }

        chatScroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(4))
            addView(
                chatList,
                ScrollView.LayoutParams(-1, -2)
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

        // Khung nhập luôn ở cuối màn hình
        val inputPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }

        prompt = EditText(this).apply {
            hint = "Nhập yêu cầu..."
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 15f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(panel)
            minLines = 1
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
        }

        inputPanel.addView(
            prompt,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(8)
            }
        )

        sendButton = makeButton("Gửi") {
            sendPrompt()
        }.apply {
            setBackgroundColor(purple)
            setTextColor(white)
        }

        inputPanel.addView(
            sendButton,
            LinearLayout.LayoutParams(-2, -2)
        )

        root.addView(
            inputPanel,
            LinearLayout.LayoutParams(-1, -2)
        )

        setContentView(root)

        addMessage(
            "DarkGPT",
            "Xin chào! Đây là DarkGPT.\n" +
                "Mở Cài đặt để kết nối dịch vụ AI, sau đó nhập yêu cầu của bạn."
        )
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(bg)
        }

        layout.addView(
            textView("Cài đặt API", 25f, white).apply {
                typeface = Typeface.DEFAULT_BOLD
            }
        )

        layout.addView(
            textView(
                "Nhập thông tin dịch vụ AI tương thích với API chat completions.",
                13f,
                muted
            ).apply {
                setPadding(0, dp(6), 0, dp(12))
            }
        )

        fun addField(
            label: String,
            hintText: String,
            keyName: String,
            isSecret: Boolean = false
        ): EditText {
            layout.addView(
                textView(label, 14f, white).apply {
                    setPadding(0, dp(10), 0, dp(4))
                }
            )

            val field = EditText(this).apply {
                hint = hintText
                setText(prefs.getString(keyName, "") ?: "")
                setTextColor(white)
                setHintTextColor(muted)
                textSize = 14f
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(panel)
                singleLine = true

                if (isSecret) {
                    inputType =
                        android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    inputType =
                        android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_NORMAL
                }
            }

            layout.addView(
                field,
                LinearLayout.LayoutParams(-1, -2)
            )

            return field
        }

        val base = addField(
            "API Base URL",
            "https://api.example.com/v1",
            "base"
        )

        val key = addField(
            "API Key",
            "Nhập API key của bạn",
            "key",
            true
        )

        val model = addField(
            "Model",
            "Tên model được hỗ trợ",
            "model"
        )

        layout.addView(
            makeButton("Lưu cài đặt") {
                val baseValue = base.text.toString().trim().trimEnd('/')
                val keyValue = key.text.toString().trim()
                val modelValue = model.text.toString().trim()

                if (baseValue.isBlank() ||
                    keyValue.isBlank() ||
                    modelValue.isBlank()
                ) {
                    Toast.makeText(
                        this,
                        "Hãy điền đủ Base URL, API Key và Model",
                        Toast.LENGTH_LONG
                    ).show()
                    return@makeButton
                }

                if (!baseValue.startsWith("https://")) {
                    Toast.makeText(
                        this,
                        "Base URL cần bắt đầu bằng https://",
                        Toast.LENGTH_LONG
                    ).show()
                    return@makeButton
                }

                prefs.edit()
                    .putString("base", baseValue)
                    .putString("key", keyValue)
                    .putString("model", modelValue)
                    .apply()

                hideKeyboard()
                showMain()

                Toast.makeText(
                    this,
                    "Đã lưu cài đặt",
                    Toast.LENGTH_SHORT
                ).show()
            }.apply {
                setBackgroundColor(purple)
                setTextColor(white)
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(16)
            }
        )

        layout.addView(
            makeButton("Quay lại") {
                hideKeyboard()
                showMain()
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(bg)
                addView(layout)
                isFillViewport = true
            }
        )
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: promptOrNull()
        if (view != null) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun promptOrNull(): View? {
        return if (::prompt.isInitialized) prompt else null
    }

    // Hiển thị tin nhắn và tự tách các khối code Markdown.
    private fun addMessage(role: String, content: String) {
        val isUser = role == "Bạn"

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(
                if (isUser) Color.rgb(38, 39, 65) else panel
            )
        }

        bubble.addView(
            textView(
                role,
                13f,
                if (isUser) Color.rgb(190, 180, 255) else green
            ).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(6))
            }
        )

        renderContent(bubble, content)

        chatList.addView(
            bubble,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        )

        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun renderContent(parent: LinearLayout, content: String) {
        val codePattern = Regex(
            "```([^\\n`]*)\\n([\\s\\S]*?)```"
        )

        var cursor = 0

        for (match in codePattern.findAll(content)) {
            val before = content.substring(cursor, match.range.first)

            if (before.isNotEmpty()) {
                addSelectableText(parent, before)
            }

            val language = match.groupValues[1].trim()
            val code = match.groupValues[2].trimEnd('\n', '\r')

            addCodeBlock(parent, language, code)

            cursor = match.range.last + 1
        }

        val remaining = content.substring(cursor)
        if (remaining.isNotEmpty()) {
            addSelectableText(parent, remaining)
        }

        if (content.isEmpty()) {
            addSelectableText(parent, "")
        }
    }

    // Cho phép nhấn giữ để chọn chữ và dùng menu sao chép của Android.
    private fun addSelectableText(
        parent: LinearLayout,
        value: String
    ) {
        val text = TextView(this).apply {
            this.text = value
            textSize = 15f
            setTextColor(white)
            setTextIsSelectable(true)
            setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG)
            setPadding(0, dp(2), 0, dp(4))
            textIsSelectable = true
            setLineSpacing(dp(2).toFloat(), 1f)
        }

        parent.addView(
            text,
            LinearLayout.LayoutParams(-1, -2)
        )
    }

    // Khối code có nút sao chép riêng và hỗ trợ chọn văn bản.
    private fun addCodeBlock(
        parent: LinearLayout,
        language: String,
        code: String
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(codeBg)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        toolbar.addView(
            textView(
                if (language.isBlank()) "CODE" else language.uppercase(),
                12f,
                green
            ).apply {
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        toolbar.addView(
            makeButton("Sao chép") {
                copyText(code)
            }.apply {
                textSize = 12f
                minHeight = dp(36)
                minWidth = dp(72)
                setPadding(dp(6), 0, dp(6), 0)
            }
        )

        box.addView(
            toolbar,
            LinearLayout.LayoutParams(-1, -2)
        )

        val codeText = TextView(this).apply {
            text = code
            textSize = 13f
            setTextColor(Color.rgb(220, 230, 245))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            textIsSelectable = true
            setPadding(0, dp(8), 0, dp(4))
            setLineSpacing(dp(2).toFloat(), 1f)
            setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG)
            isHorizontalScrollBarEnabled = true
        }

        box.addView(
            codeText,
            LinearLayout.LayoutParams(-1, -2)
        )

        parent.addView(
            box,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        )
    }

    private fun copyText(value: String) {
        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("DarkGPT", value)
        )

        Toast.makeText(
            this,
            "Đã sao chép",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun sendPrompt() {
        if (sending) return

        val question = prompt.text.toString().trim()
        if (question.isEmpty()) return

        val base = prefs.getString("base", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""

        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            Toast.makeText(
                this,
                "Hãy cài đặt API trước",
                Toast.LENGTH_LONG
            ).show()
            showSettings()
            return
        }

        addMessage("Bạn", question)
        prompt.setText("")
        hideKeyboard()

        sending = true
        sendButton.isEnabled = false
        sendButton.text = "..."

        val loading = textView(
            "DarkGPT đang xử lý...",
            14f,
            muted
        ).apply {
            setPadding(dp(12), dp(8), dp(12), dp(12))
            tag = "loading"
        }

        chatList.addView(loading)

        chatScroll.post {
            chatScroll.fullScroll(View.FOCUS_DOWN)
        }

        thread {
            val answer = try {
                val cleanBase = base.trimEnd('/')
                val url = URL("$cleanBase/chat/completions")

                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                    setRequestProperty(
                        "Authorization",
                        "Bearer $key"
                    )
                }

                try {
                    val messages = JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put(
                                    "content",
                                    "Bạn là DarkGPT, trợ lý lập trình game. " +
                                        "Hỗ trợ dự án riêng, mod hợp pháp, mã nguồn " +
                                        "và giải thích lỗi. Không hỗ trợ đánh cắp " +
                                        "tài khoản, malware hoặc né anti-cheat."
                                )
                        )
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", question)
                        )

                    val body = JSONObject()
                        .put("model", model)
                        .put("messages", messages)
                        .put("stream", false)

                    conn.outputStream.use { stream ->
                        stream.write(
                            body.toString().toByteArray(Charsets.UTF_8)
                        )
                    }

                    val responseCode = conn.responseCode
                    val stream = if (responseCode in 200..299) {
                        conn.inputStream
                    } else {
                        conn.errorStream
                    }

                    val raw = stream?.bufferedReader()?.use {
                        it.readText()
                    } ?: ""

                    if (responseCode !in 200..299) {
                        throw Exception("HTTP $responseCode: $raw")
                    }

                    JSONObject(raw)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "AI không trả lời.")
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                "Lỗi kết nối: ${e.message ?: "Không xác định"}"
            }

            runOnUiThread {
                for (i in chatList.childCount - 1 downTo 0) {
                    val child = chatList.getChildAt(i)
                    if (child.tag == "loading") {
                        chatList.removeViewAt(i)
                    }
                }

                addMessage("DarkGPT", answer)

                sending = false
                sendButton.isEnabled = true
                sendButton.text = "Gửi"
            }
        }
    }
}
