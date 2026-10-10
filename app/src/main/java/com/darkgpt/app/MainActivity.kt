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
    
    // Lưu trữ thông tin file chờ xuất theo yêu cầu (Đã sửa thành var)
    private enum class ExportType { NONE, TXT, ZIP, MCPACK }
    private var pendingExportType = ExportType.NONE
    private var pendingExportContent = ""
    private var pendingExportFiles: List<Pair<String, String>> = emptyList()

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

        root.addView(fileRow)

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
                            .setM        ).findAll(answer).toList()

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
        return files
    }

    private fun handleExportFileResult(uri: Uri) {
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw Exception("Cannot open destination")

            when (pendingExportType) {
                ExportType.TXT -> {
                    output.use { it.write(pendingExportContent.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this, tr("Đã tạo file TXT.", "TXT file created.", "Файл TXT создан."), Toast.LENGTH_LONG).show()
                }
                ExportType.ZIP, ExportType.MCPACK -> {
                    output.use { stream ->
                        ZipOutputStream(stream).use { zip ->
                            pendingExportFiles.forEach { (filename, content) ->
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
                    val msg = if (pendingExportType == ExportType.MCPACK) {
                        tr("Đã tạo gói MCPACK Minecraft hợp lệ.", "Valid Minecraft MCPACK created.", "MCPACK создан.")
                    } else {
                        tr("Đã tạo ZIP mã nguồn.", "Source ZIP created.", "ZIP создан.")
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Export error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        } finally {
            pendingExportType = ExportType.NONE
            pendingExportContent = ""
            pendingExportFiles = emptyList()
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
        val pattern = Regex("        var cursor = 0

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
}
