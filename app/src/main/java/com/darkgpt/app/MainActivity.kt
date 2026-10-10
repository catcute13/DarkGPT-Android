
package com.darkgpt.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context.INPUT_METHOD_SERVICE
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.util.Base64
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONArray
import org.json.JSONObject
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

    private lateinit var chatList: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var prompt: EditText
    private lateinit var sendButton: Button
    private lateinit var clockView: TextView

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            if (::clockView.isInitialized) {
                clockView.text = SimpleDateFormat(
                    "HH:mm:ss  •  dd/MM/yyyy",
                    Locale.getDefault()
                ).format(Date())
            }
            clockHandler.postDelayed(this, 1000)
        }
    }

    private var sending = false
    private var currentChatId = ""
    private var attachedText = ""
    private val attachedFileNames = mutableListOf<String>()

    private var pendingImageBase64: String? = null
    private var pendingImageMime = "image/jpeg"
    private var pendingImageName = ""
    private var pendingImageBytes: ByteArray? = null
    private var pendingZipFiles: List<Pair<String, String>> = emptyList()

    data class Message(
        val role: String,
        val content: String
    )

    data class Chat(
        val id: String,
        var title: String,
        var favorite: Boolean,
        val messages: MutableList<Message>
    )

    private val chats = mutableListOf<Chat>()

    private fun language(): String {
        return when (prefs.getString("language", null)) {
            "vi" -> "vi"
            "en" -> "en"
            "ru" -> "ru"
            else -> when (resources.configuration.locales[0].language) {
                "vi" -> "vi"
                "ru" -> "ru"
                else -> "en"
            }
        }
    }

    private fun tr(vi: String, en: String, ru: String): String {
        return when (language()) {
            "en" -> en
            "ru" -> ru
            else -> vi
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PDFBoxResourceLoader.init(applicationContext)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        window.statusBarColor = bg
        window.navigationBarColor = bg

        loadChats()

        if (chats.isEmpty()) createChat(false)

        if (chats.none { it.id == currentChatId }) {
            currentChatId = chats.first().id
        }

        showMain()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun textView(
        value: String,
        size: Float,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }
    }

    private fun makeButton(
        label: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun currentChat(): Chat? =
        chats.firstOrNull { it.id == currentChatId }

    // -----------------------------
    // CHAT HISTORY
    // -----------------------------

    private fun loadChats() {
        chats.clear()

        try {
            val array = JSONArray(
                prefs.getString("chat_history", "[]") ?: "[]"
            )

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val messages = mutableListOf<Message>()
                val messageArray =
                    obj.optJSONArray("messages") ?: JSONArray()

                for (j in 0 until messageArray.length()) {
                    val message = messageArray.getJSONObject(j)
                    messages.add(
                        Message(
                            message.optString("role"),
                            message.optString("content")
                        )
                    )
                }

                chats.add(
                    Chat(
                        obj.optString(
                            "id",
                            UUID.randomUUID().toString()
                        ),
                        obj.optString(
                            "title",
                            tr("Chat mới", "New chat", "Новый чат")
                        ),
                        obj.optBoolean("favorite", false),
                        messages
                    )
                )
            }

            currentChatId =
                prefs.getString("current_chat", "") ?: ""
        } catch (_: Exception) {
            chats.clear()
        }
    }

    private fun saveChats() {
        val array = JSONArray()

        chats.forEach { chat ->
            val messages = JSONArray()

            chat.messages.forEach { message ->
                messages.put(
                    JSONObject()
                        .put("role", message.role)
                        .put("content", message.content)
                )
            }

            array.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("title", chat.title)
                    .put("favorite", chat.favorite)
                    .put("messages", messages)
            )
        }

        prefs.edit()
            .putString("chat_history", array.toString())
            .putString("current_chat", currentChatId)
            .apply()
    }

    private fun createChat(show: Boolean = true) {
        val chat = Chat(
            UUID.randomUUID().toString(),
            tr(
                "Cuộc trò chuyện mới",
                "New conversation",
                "Новый разговор"
            ),
            false,
            mutableListOf()
        )

        chats.add(0, chat)
        currentChatId = chat.id
        saveChats()

        if (show) showMain()
    }

    // -----------------------------
    // MAIN UI
    // -----------------------------

    private fun showMain() {
        clockHandler.removeCallbacks(clockRunnable)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }

        // Title and creator share the same row.
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleRow.addView(
            textView("DarkGPT", 27f, white).apply {
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        titleRow.addView(
            textView("Creater : Tú-LNMT", 12f, green).apply {
                gravity = Gravity.END
                typeface = Typeface.DEFAULT_BOLD
            }
        )

        root.addView(titleRow)

        root.addView(
            textView(
                tr(
                    "AI lập trình game • Mã nguồn",
                    "Game coding AI • Source code",
                    "ИИ для игр • Исходный код"
                ),
                12f,
                muted
            )
        )

        clockView = textView("", 12f, muted)
        root.addView(clockView)
        clockHandler.post(clockRunnable)

        val topRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        topRow.addView(
            makeButton(
                tr("＋ Chat mới", "＋ New chat", "＋ Новый чат")
            ) { createChat() }
        )

        topRow.addView(
            makeButton(
                tr("☰ Lịch sử", "☰ History", "☰ История")
            ) { showHistory() }
        )

        topRow.addView(
            makeButton(
                tr("⚙ Cài đặt", "⚙ Settings", "⚙ Настройки")
            ) { showSettings() }
        )

        root.addView(topRow)

        val fileRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        fileRow.addView(
            makeButton(
                tr("📎 Chọn tệp", "📎 Attach file", "📎 Файл")
            ) { chooseFile() }
        )

        fileRow.addView(
            makeButton(
                tr("🖼 Chọn ảnh", "🖼 Choose image", "🖼 Изображение")
            ) { chooseImage() }
        )

        fileRow.addView(
            makeButton(
                tr("Tạo ZIP code", "Export code ZIP", "ZIP с кодом")
            ) { exportZip() }
        )

        root.addView(fileRow)

        // Attachment name and image preview.
        if (attachedFileNames.isNotEmpty() ||
            pendingImageBase64 != null
        ) {
            val attachmentBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                setBackgroundColor(panel)
            }

            if (attachedFileNames.isNotEmpty()) {
                attachmentBox.addView(
                    textView(
                        tr("Tệp đã chọn:", "Attached files:", "Файлы:"),
                        12f,
                        green
                    )
                )

                attachedFileNames.forEach { name ->
                    attachmentBox.addView(
                        textView("📎 $name", 12f, white)
                    )
                }
            }

            if (pendingImageBase64 != null) {
                attachmentBox.addView(
                    textView("🖼 $pendingImageName", 12f, green)
                )

                val imageBytes = pendingImageBytes
                if (imageBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(
                        imageBytes, 0, imageBytes.size
                    )

                    if (bitmap != null) {
                        attachmentBox.addView(
                            ImageView(this).apply {
                                setImageBitmap(bitmap)
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                maxHeight = dp(180)
                            },
                            LinearLayout.LayoutParams(-1, dp(180))
                        )
                    }
                }

                attachmentBox.addView(
                    makeButton(
                        tr("Bỏ ảnh", "Remove image", "Убрать изображение")
                    ) {
                        pendingImageBase64 = null
                        pendingImageBytes = null
                        pendingImageName = ""
                        refreshMainPreservingDraft()
                    }
                )
            }

            attachmentBox.addView(
                makeButton(
                    tr("Xóa tệp đính kèm", "Clear attachments", "Удалить вложения")
                ) {
                    attachedText = ""
                    attachedFileNames.clear()
                    pendingImageBase64 = null
                    pendingImageBytes = null
                    pendingImageName = ""
                    refreshMainPreservingDraft()
                }
            )

            root.addView(
                attachmentBox,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(5)
                }
            )
        }

        chatList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(6), dp(2), dp(12))
        }

        chatScroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            addView(chatList, ViewGroup.LayoutParams(-1, -2))
        }

        root.addView(
            chatScroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        prompt = EditText(this).apply {
            hint = tr(
                "Nhập yêu cầu...",
                "Type a request...",
                "Введите запрос..."
            )
            setTextColor(white)
            setHintTextColor(muted)
            textSize = 15f
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(panel)
            minLines = 1
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        inputRow.addView(
            prompt,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = dp(6)
            }
        )

        sendButton = makeButton(
            tr("Gửi", "Send", "Отправить")
        ) { sendPrompt() }.apply {
            setBackgroundColor(purple)
            setTextColor(white)
        }

        inputRow.addView(sendButton)

        root.addView(
            inputRow,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(6)
            }
        )

        setContentView(root)
        renderCurrentChat()
    }

    private fun renderCurrentChat() {
        chatList.removeAllViews()
        val chat = currentChat() ?: return

        if (chat.messages.isEmpty()) {
            addMessageView(
                chatList,
                "DarkGPT",
                tr(
                    "Xin chào! Mở Cài đặt để kết nối API AI. " +
                        "Lịch sử trò chuyện được lưu trên thiết bị.",
                    "Hello! Open Settings to connect an AI API. " +
                        "Chat history is saved on this device.",
                    "Привет! Откройте настройки для подключения API. " +
                        "История чатов сохраняется на устройстве."
                )
            )
        } else {
            chat.messages.forEach {
                addMessageView(chatList, it.role, it.content)
            }
        }

        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // -----------------------------
    // HISTORY
    // -----------------------------

    private fun showHistory() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(bg)
        }

        layout.addView(
            textView(
                tr("Lịch sử chat", "Chat history", "История чатов"),
                24f,
                white
            ).apply { typeface = Typeface.DEFAULT_BOLD }
        )

        layout.addView(
            makeButton(
                tr("＋ Chat mới", "＋ New chat", "＋ Новый чат")
            ) { createChat() }
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this)
        scroll.addView(list)

        chats.sortedByDescending { it.favorite }.forEach { chat ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setBackgroundColor(panel)
            }

            item.addView(
                makeButton(
                    (if (chat.favorite) "★ " else "") + chat.title
                ) {
                    currentChatId = chat.id
                    saveChats()
                    showMain()
                }
            )

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            actions.addView(
                makeButton(
                    if (chat.favorite)
                        tr("Bỏ ưu tiên", "Unfavorite", "Убрать ★")
                    else
                        tr("★ Ưu tiên", "★ Favorite", "★ В избранное")
                ) {
                    chat.favorite = !chat.favorite
                    saveChats()
                    showHistory()
                }
            )

            actions.addView(
                makeButton(
                    tr("Đổi tên", "Rename", "Переименовать")
                ) {
                    val input = EditText(this@MainActivity)
                    input.setText(chat.title)

                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(
                            tr(
                                "Tên cuộc trò chuyện",
                                "Chat name",
                                "Название чата"
                            )
                        )
                        .setView(input)
                        .setPositiveButton("OK") { _, _ ->
                            val name = input.text.toString().trim()
                            if (name.isNotEmpty()) {
                                chat.title = name
                                saveChats()
                                showHistory()
                            }
                        }
                        .setNegativeButton(
                            tr("Hủy", "Cancel", "Отмена"), null
                        )
                        .show()
                }
            )

            actions.addView(
                makeButton(tr("Xóa", "Delete", "Удалить")) {
                    if (chat.favorite) {
                        Toast.makeText(
                            this,
                            tr(
                                "Chat ưu tiên được bảo vệ. Hãy bỏ ưu tiên trước.",
                                "Favorite chats are protected. Unfavorite first.",
                                "Избранные чаты защищены. Сначала уберите ★."
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setMessage(
                                tr(
                                    "Xóa cuộc trò chuyện này?",
                                    "Delete this conversation?",
                                    "Удалить этот разговор?"
                                )
                            )
                            .setNegativeButton(
                                tr("Hủy", "Cancel", "Отмена"), null
                            )
                            .setPositiveButton(
                                tr("Xóa", "Delete", "Удалить")
                            ) { _, _ ->
                                chats.remove(chat)

                                if (currentChatId == chat.id) {
                                    if (chats.isEmpty()) createChat(false)
                                    currentChatId = chats.first().id
                                }

                                saveChats()
                                showHistory()
                            }
                            .show()
                    }
                }
            )

            item.addView(actions)
            list.addView(
                item,
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(
            makeButton(tr("Quay lại", "Back", "Назад")) { showMain() }
        )
        setContentView(layout)
    }

    // -----------------------------
    // SETTINGS
    // -----------------------------

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(bg)
        }

        layout.addView(
            textView(
                tr("Cài đặt", "Settings", "Настройки"),
                25f,
                white
            ).apply { typeface = Typeface.DEFAULT_BOLD }
        )

        layout.addView(
            textView(
                tr(
                    "API tương thích với Chat Completions",
                    "Chat Completions compatible API",
                    "API, совместимый с Chat Completions"
                ),
                13f,
                muted
            )
        )

        fun addField(
            label: String,
            hint: String,
            keyName: String,
            secret: Boolean = false
        ): EditText {
            layout.addView(
                textView(label, 14f, white).apply {
                    setPadding(0, dp(10), 0, dp(4))
                }
            )

            val field = EditText(this).apply {
                this.hint = hint
                setText(prefs.getString(keyName, "") ?: "")
                setTextColor(white)
                setHintTextColor(muted)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(panel)
                isSingleLine = true
                inputType = if (secret) {
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_NORMAL
                }

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?, start: Int, count: Int, after: Int
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?, start: Int, before: Int, count: Int
                    ) {
                        prefs.edit()
                            .putString(keyName, s?.toString() ?: "")
                            .apply()
                    }

                    override fun afterTextChanged(s: Editable?) {}
                })
            }

            layout.addView(field, LinearLayout.LayoutParams(-1, -2))
            return field
        }

        val base = addField(
            "API Base URL",
            "https://api.example.com/v1",
            "base"
        )
        val key = addField("API Key", "API key", "key", true)
        val model = addField("Model", "Model name", "model")

        layout.addView(
            textView(
                tr("Ngôn ngữ", "Language", "Язык"),
                14f,
                white
            ).apply { setPadding(0, dp(12), 0, dp(4)) }
        )

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Tiếng Việt", "English", "Русский")
        )

        spinner.setSelection(
            when (language()) {
                "en" -> 1
                "ru" -> 2
                else -> 0
            }
        )

        var ignoreInitialSelection = true

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (ignoreInitialSelection) {
                        ignoreInitialSelection = false
                        return
                    }

                    val selected = when (position) {
                        1 -> "en"
                        2 -> "ru"
                        else -> "vi"
                    }

                    if (selected != language()) {
                        prefs.edit()
                            .putString("base", base.text.toString())
                            .putString("key", key.text.toString())
                            .putString("model", model.text.toString())
                            .putString("language", selected)
                            .apply()

                        hideKeyboard()
                        showSettings()
                    }
                }
            }

        layout.addView(spinner)

        layout.addView(
            makeButton(
                tr("Lưu cài đặt", "Save settings", "Сохранить")
            ) {
                val b = base.text.toString().trim().trimEnd('/')
                val k = key.text.toString().trim()
                val m = model.text.toString().trim()

                if (b.isBlank() || k.isBlank() || m.isBlank()) {
                    Toast.makeText(
                        this,
                        tr(
                            "Hãy điền đủ Base URL, API Key và Model",
                            "Fill in Base URL, API Key and Model",
                            "Заполните Base URL, API Key и Model"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return@makeButton
                }

                if (!b.startsWith("https://")) {
                    Toast.makeText(
                        this,
                        tr(
                            "Base URL cần bắt đầu bằng https://",
                            "Base URL must start with https://",
                            "Base URL должен начинаться с https://"
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    return@makeButton
                }

                prefs.edit()
                    .putString("base", b)
                    .putString("key", k)
                    .putString("model", m)
                    .apply()

                hideKeyboard()
                showMain()

                Toast.makeText(
                    this,
                    tr("Đã lưu", "Saved", "Сохранено"),
                    Toast.LENGTH_SHORT
                ).show()
            }.apply {
                setBackgroundColor(purple)
                setTextColor(white)
            }
        )

        layout.addView(
            makeButton(tr("Quay lại", "Back", "Назад")) {
                hideKeyboard()
                showMain()
            }
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(bg)
                addView(layout, ViewGroup.LayoutParams(-1, -2))
                isFillViewport = true
            }
        )
    }

    private fun hideKeyboard() {
        currentFocus?.let {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    // -----------------------------
    // PICK FILES AND IMAGES
    // -----------------------------

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 501)
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, 503)
    }

    @Deprecated("Uses startActivityForResult for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return

        when (requestCode) {
            501 -> readAttachment(data.data!!)
            502 -> writeZip(data.data!!)
            503 -> readImage(data.data!!)
        }
    }

    private fun readAttachment(uri: Uri) {
        thread {
            try {
                val name = queryName(uri) ?: "attachment.txt"
                val mime = contentResolver.getType(uri) ?: ""
                val lower = name.lowercase(Locale.ROOT)

                val isMinecraftArchive = listOf(
                    ".zip", ".mcpack", ".mcaddon", ".mcworld", ".mctemplate"
                ).any { lower.endsWith(it) }

                val content = when {
                    lower.endsWith(".pdf") || mime == "application/pdf" -> {
                        val input = contentResolver.openInputStream(uri)
                            ?: throw Exception("Cannot open PDF")

                        input.use {
                            val document = PDDocument.load(it)
                            try {
                                PDFTextStripper().getText(document)
                            } finally {
                                document.close()
                            }
                        }
                    }

                    isMinecraftArchive ||
                        mime == "application/zip" ||
                        mime == "application/x-zip-compressed" -> {
                        readZipText(uri)
                    }

                    mime.startsWith("text/") ||
                        listOf(
                            ".kt", ".java", ".json", ".xml", ".md",
                            ".txt", ".gradle", ".kts", ".yml", ".yaml",
                            ".py", ".js", ".html", ".css", ".properties",
                            ".toml", ".sh", ".c", ".cpp", ".h", ".hpp",
                            ".sql", ".mcfunction", ".lang", ".molang",
                            ".mcmeta", ".ini", ".cfg", ".csv", ".log"
                        ).any { lower.endsWith(it) } -> {
                        contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: throw Exception("Cannot read file")
                    }

                    else -> {
                        throw Exception(
                            "Unsupported format. Choose text, code, PDF, ZIP, " +
                                "MCADDON, MCPACK or MCWORLD."
                        )
                    }
                }

                if (content.length > 120000) {
                    throw Exception("File exceeds 120,000 characters")
                }

                synchronized(attachedFileNames) {
                    attachedFileNames.add(name)
                    attachedText += "\n\n[FILE: $name]\n$content\n[END FILE]"
                }

                runOnUiThread {
                    Toast.makeText(
                        this,
                        tr(
                            "Đã đọc tệp; nội dung sẽ gửi cùng câu hỏi kế tiếp.",
                            "File read; it will be sent with your next prompt.",
                            "Файл прочитан; он будет отправлен со следующим запросом."
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshMainPreservingDraft()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "File error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun readZipText(uri: Uri): String {
        val out = StringBuilder()
        var entries = 0
        var total = 0L

        val allowed = listOf(
            ".kt", ".java", ".json", ".xml", ".md", ".txt",
            ".gradle", ".kts", ".yml", ".yaml", ".py", ".js",
            ".html", ".css", ".properties", ".toml", ".sh",
            ".c", ".cpp", ".h", ".hpp", ".sql", ".mcfunction",
            ".lang", ".molang", ".mcmeta", ".ini", ".cfg", ".csv",
            ".log"
        )

        val input = contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open ZIP archive")

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            val buffer = ByteArray(8192)

            while (entry != null) {
                if (!entry.isDirectory) {
                    entries++
                    if (entries > 100) {
                        throw Exception("Archive contains more than 100 files")
                    }

                    val name = entry.name.replace("\\", "/")
                    val safePath = !name.startsWith("/") &&
                        !name.split("/").any { it == ".." }

                    if (safePath &&
                        allowed.any { name.lowercase(Locale.ROOT).endsWith(it) }
                    ) {
                        val bytes = java.io.ByteArrayOutputStream()
                        var count: Int

                        while (zip.read(buffer).also { count = it } != -1) {
                            total += count
                            if (total > 2_000_000L) {
                                throw Exception("Extracted text exceeds 2 MB")
                            }
                            bytes.write(buffer, 0, count)
                        }

                        out.append("\n\n[ARCHIVE FILE: ")
                            .append(name)
                            .append("]\n")
                            .append(bytes.toString("UTF-8"))
                            .append("\n[END ARCHIVE FILE]\n")

                        if (out.length > 120000) {
                            throw Exception("Archive text exceeds 120,000 characters")
                        }
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (out.isEmpty()) {
            throw Exception(
                "No supported text/code files found in the archive. " +
                    "Binary textures, models and sounds are not read as text."
            )
        }

        return out.toString()
    }

    private fun readImage(uri: Uri) {
        thread {
            try {
                val bytes = contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: throw Exception("Cannot read image")

                if (bytes.size > 5 * 1024 * 1024) {
                    throw Exception("Image exceeds 5 MB")
                }

                val mime = contentResolver.getType(uri) ?: "image/jpeg"
                if (!mime.startsWith("image/")) {
                    throw Exception("This file is not an image")
                }

                val bitmap = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size
                ) ?: throw Exception("Unsupported or damaged image")

                pendingImageBase64 =
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                pendingImageMime =
                    if (mime == "image/jpg") "image/jpeg" else mime
                pendingImageName = queryName(uri) ?: "image"
                pendingImageBytes = bytes

                bitmap.recycle()

                runOnUiThread {
                    Toast.makeText(
                        this,
                        tr(
                            "Đã chọn ảnh. Hãy nhập câu hỏi rồi gửi.",
                            "Image attached. Type a question and send.",
                            "Изображение прикреплено. Введите вопрос и отправьте."
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    refreshMainPreservingDraft()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Image error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun refreshMainPreservingDraft() {
        val draft = if (::prompt.isInitialized) {
            prompt.text.toString()
        } else {
            ""
        }

        showMain()
        prompt.setText(draft)
        prompt.setSelection(prompt.text.length)
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // -----------------------------
    // SEND PROMPT / API
    // -----------------------------

    private fun sendPrompt() {
        if (sending) return

        val typed = prompt.text.toString().trim()

        if (typed.isEmpty() &&
            attachedText.isBlank() &&
            pendingImageBase64 == null
        ) return

        val imageData = pendingImageBase64
        val imageMime = pendingImageMime
        val imageName = pendingImageName

        val question = buildString {
            append(typed)

            if (attachedText.isNotBlank()) append(attachedText)

            if (imageData != null) {
                append("\n\n[Image attached: $imageName]")
            }
        }.trim()

        val base = prefs.getString("base", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""

        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            Toast.makeText(
                this,
                tr(
                    "Hãy cài đặt API trước",
                    "Configure the API first",
                    "Сначала настройте API"
                ),
                Toast.LENGTH_LONG
            ).show()
            showSettings()
            return
        }

        attachedText = ""
        attachedFileNames.clear()
        pendingImageBase64 = null
        pendingImageBytes = null
        pendingImageName = ""

        val chat = currentChat() ?: run {
            createChat(false)
            currentChat()!!
        }

        chat.messages.add(Message("Bạn", question))

        if (chat.messages.count { it.role == "Bạn" } == 1) {
            chat.title = typed.take(36).ifBlank {
                tr("Tệp đính kèm", "Attachment", "Вложение")
            }
        }

        saveChats()
        renderCurrentChat()

        prompt.setText("")
        hideKeyboard()

        sending = true
        sendButton.isEnabled = false
        sendButton.text = "..."

        chatList.addView(
            textView(
                tr(
                    "DarkGPT đang xử lý...",
                    "DarkGPT is thinking...",
                    "DarkGPT думает..."
                ),
                14f,
                muted
            ).apply {
                setPadding(dp(12), dp(8), dp(12), dp(12))
                tag = "loading"
            }
        )

        val chatId = currentChatId
        val history = chat.messages.toList()

        thread {
            val answer = try {
                val connection = (
                    URL(base.trimEnd('/') + "/chat/completions")
                        .openConnection() as HttpURLConnection
                ).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 90000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                    setRequestProperty("Authorization", "Bearer $key")
                }

                try {
                    val messages = JSONArray()

                    messages.put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                when (language()) {
                                    "en" ->
                                        "You are DarkGPT, an assistant for game " +
                                            "and app development. Explain errors and " +
                                            "provide useful source code. When returning " +
                                            "multiple files, use separate code blocks " +
                                            "and put filename: FileName.ext in the opening " +
                                            "code fence. Analyze images when the model " +
                                            "supports vision."
                                    "ru" ->
                                        "Вы DarkGPT, помощник по разработке игр и " +
                                            "приложений. Объясняйте ошибки и предоставляйте " +
                                            "полезный исходный код. Для нескольких файлов " +
                                            "используйте отдельные блоки кода и указывайте " +
                                            "filename: FileName.ext в начале блока. " +
                                            "Анализируйте изображения, если модель поддерживает vision."
                                    else ->
                                        "Bạn là DarkGPT, trợ lý lập trình game và ứng dụng. " +
                                            "Hãy giải thích lỗi và hỗ trợ mã nguồn hữu ích. " +
                                            "Khi trả về nhiều tệp, dùng khối code riêng và ghi " +
                                            "filename: TenTep.ext trong dòng mở đầu. Nếu có ảnh, " +
                                            "hãy phân tích ảnh nếu model hỗ trợ vision."
                                }
                            )
                    )

                    history.takeLast(30).forEachIndexed { index, message ->
                        val role = if (message.role == "Bạn") {
                            "user"
                        } else {
                            "assistant"
                        }

                        val content: Any =
                            if (index == history.lastIndex &&
                                role == "user" &&
                                imageData != null
                            ) {
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("type", "text")
                                            .put("text", message.content)
                                    )
                                    .put(
                                        JSONObject()
                                            .put("type", "image_url")
                                            .put(
                                                "image_url",
                                                JSONObject().put(
                                                    "url",
                                                    "data:$imageMime;base64,$imageData"
                                                )
                                            )
                                    )
                            } else {
                                message.content
                            }

                        messages.put(
                            JSONObject()
                                .put("role", role)
                                .put("content", content)
                        )
                    }

                    val body = JSONObject()
                        .put("model", model)
                        .put("messages", messages)
                        .put("stream", false)

                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }

                    val status = connection.responseCode
                    val responseStream = if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                    val raw = responseStream
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: ""

                    if (status !in 200..299) {
                        throw Exception("HTTP $status: ${raw.take(1500)}")
                    }

                    val content = JSONObject(raw)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .opt("content")

                    when (content) {
                        is String -> content
                        is JSONArray -> {
                            val result = StringBuilder()
                            for (i in 0 until content.length()) {
                                val part = content.optJSONObject(i)
                                if (part?.optString("type") == "text") {
                                    result.append(part.optString("text"))
                                }
                            }
                            result.toString().ifBlank {
                                "AI returned no readable text."
                            }
                        }
                        else -> "AI did not return text."
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                "Connection error: ${e.message ?: "Unknown error"}. " +
                    "For images, verify that the selected model and API " +
                    "support image_url/vision."
            }

            runOnUiThread {
                chats.firstOrNull { it.id == chatId }
                    ?.messages
                    ?.add(Message("DarkGPT", answer))

                saveChats()

                if (currentChatId == chatId) renderCurrentChat()

                sending = false
                if (::sendButton.isInitialized) {
                    sendButton.isEnabled = true
                    sendButton.text = tr("Gửi", "Send", "Отправить")
                }
            }
        }
    }

    // -----------------------------
    // MESSAGE RENDERING
    // -----------------------------

    private fun addMessageView(
        parent: LinearLayout,
        role: String,
        content: String
    ) {
        val isUser = role == "Bạn"

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(
                if (isUser) Color.rgb(38, 39, 65) else panel
            )
        }

        val displayRole = when (role) {
            "Bạn" -> tr("Bạn", "You", "Вы")
            "DarkGPT" -> "DarkGPT"
            else -> role
        }

        bubble.addView(
            textView(
                displayRole,
                13f,
                if (isUser) Color.rgb(190, 180, 255) else green
            ).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 0, 0, dp(6))
            }
        )

        renderContent(bubble, content)

        parent.addView(
            bubble,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun renderContent(parent: LinearLayout, content: String) {
        val pattern = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
        var cursor = 0

        pattern.findAll(content).forEach { match ->
            val before = content.substring(cursor, match.range.first)
            if (before.isNotEmpty()) addSelectableText(parent, before)

            addCodeBlock(
                parent,
                match.groupValues[1].trim(),
                match.groupValues[2].trimEnd('\n', '\r')
            )
            cursor = match.range.last + 1
        }

        val remaining = content.substring(cursor)
        if (remaining.isNotEmpty() || content.isEmpty()) {
            addSelectableText(parent, remaining)
        }
    }

    private fun addSelectableText(parent: LinearLayout, value: String) {
        parent.addView(
            TextView(this).apply {
                text = value
                textSize = 15f
                setTextColor(white)
                setPadding(0, dp(2), 0, dp(4))
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextIsSelectable(true)
            },
            LinearLayout.LayoutParams(-1, -2)
        )
    }

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
            ).apply { typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        toolbar.addView(
            makeButton(tr("Sao chép", "Copy", "Копировать")) {
                copyText(code)
            }.apply {
                textSize = 12f
                minHeight = dp(36)
            }
        )

        box.addView(toolbar)

        box.addView(
            TextView(this).apply {
                text = code
                textSize = 13f
                setTextColor(Color.rgb(220, 230, 245))
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(8), 0, dp(4))
                setTextIsSelectable(true)
            }
        )

        parent.addView(
            box,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(5)
                bottomMargin = dp(7)
            }
        )
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText("DarkGPT", value)
        )

        Toast.makeText(
            this,
            tr("Đã sao chép", "Copied", "Скопировано"),
            Toast.LENGTH_SHORT
        ).show()
    }

    // -----------------------------
    // EXPORT CODE TO ZIP
    // -----------------------------

    private fun exportZip() {
        val answer = currentChat()
            ?.messages
            ?.lastOrNull { it.role == "DarkGPT" }
            ?.content

        if (answer.isNullOrBlank()) {
            Toast.makeText(
                this,
                tr(
                    "Chưa có câu trả lời AI để xuất ZIP.",
                    "No AI answer to export.",
                    "Нет ответа ИИ для экспорта."
                ),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val blocks = Regex(
            "```([^\\n`]*)\\n([\\s\\S]*?)```"
        ).findAll(answer).toList()

        if (blocks.isEmpty()) {
            Toast.makeText(
                this,
                tr(
                    "Không tìm thấy khối code trong câu trả lời.",
                    "No code blocks found in the answer.",
                    "В ответе не найдены блоки кода."
                ),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val files = mutableListOf<Pair<String, String>>()
        val usedNames = mutableSetOf<String>()

        blocks.forEachIndexed { index, match ->
            val header = match.groupValues[1].trim()

            val filename = Regex(
                """filename:\s*([^\s]+)""",
                RegexOption.IGNORE_CASE
            ).find(header)?.groupValues?.get(1)

            val extension = when {
                header.contains("kotlin", true) || header == "kt" -> "kt"
                header.contains("java", true) -> "java"
                header.contains("xml", true) -> "xml"
                header.contains("json", true) -> "json"
                header.contains("python", true) || header == "py" -> "py"
                header.contains("javascript", true) || header == "js" -> "js"
                header.contains("html", true) -> "html"
                header.contains("css", true) -> "css"
                header.contains("gradle", true) -> "gradle.kts"
                else -> "txt"
            }

            var name = (filename ?: "source_${index + 1}.$extension")
                .replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")

            if (name.isBlank()) name = "source_${index + 1}.$extension"

            val baseName = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            var suffix = 2

            while (!usedNames.add(name.lowercase(Locale.ROOT))) {
                name = if (ext.isBlank()) {
                    "${baseName}_$suffix"
                } else {
                    "${baseName}_$suffix.$ext"
                }
                suffix++
            }

            files.add(
                name to match.groupValues[2].trimEnd('\n', '\r')
            )
        }

        pendingZipFiles = files

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "DarkGPT-source.zip")
        }

        startActivityForResult(intent, 502)
    }

    private fun writeZip(uri: Uri) {
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw Exception("Cannot open destination")

            output.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    pendingZipFiles.forEach { (filename, content) ->
                        val safeName = filename
                            .replace("\\", "_")
                            .replace("/", "_")
                            .replace("..", "_")

                        zip.putNextEntry(ZipEntry(safeName))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            }

            Toast.makeText(
                this,
                tr(
                    "Đã tạo ZIP mã nguồn.",
                    "Source ZIP created.",
                    "ZIP с исходным кодом создан."
                ),
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "ZIP error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            pendingZipFiles = emptyList()
        }
    }
}
