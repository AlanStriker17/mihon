package mihon.desktop.client

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.source.SourceChapter
import mihon.desktop.source.SourceDescriptor
import mihon.desktop.source.SourceManga
import mihon.desktop.source.SourcePage
import mihon.desktop.source.SourcePageResult
import mihon.desktop.server.startSourceServer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.image.BufferedImage
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.Executors
import javax.imageio.ImageIO
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.ImageIcon
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.border.EmptyBorder

private val json = Json { ignoreUnknownKeys = true }
private val http = HttpClient.newBuilder().build()
private val apiBase = System.getenv("MIHON_SOURCE_SERVER_URL") ?: "http://127.0.0.1:8766"
private val libraryStore = DesktopLibraryStore(
    java.nio.file.Path.of(System.getProperty("user.home"), ".mihon", "library.json"),
)
private val readingProgressStore = DesktopReadingProgressStore(
    java.nio.file.Path.of(System.getProperty("user.home"), ".mihon", "reading-progress.json"),
)
private val readingProgressWriter = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "mihon-reading-progress").apply { isDaemon = true }
}

fun main() {
    val embeddedSourceServer = startSourceServer()
    SwingUtilities.invokeLater { MihonDesktopWindow(embeddedSourceServer) }
}

private class MihonDesktopWindow(
    private val embeddedSourceServer: com.sun.net.httpserver.HttpServer,
) : JFrame("Mihon Desktop") {
    private val sourceModel = DefaultComboBoxModel<SourceDescriptor>()
    private val sourcePicker = JComboBox(sourceModel)
    private val query = JTextField()
    private val mangaModel = DefaultListModel<SourceManga>()
    private val mangaList = JList(mangaModel)
    private val chapterModel = DefaultListModel<SourceChapter>()
    private val chapterList = JList(chapterModel)
    private val pageModel = DefaultListModel<SourcePage>()
    private val pageList = JList(pageModel)
    private val libraryModel = DefaultListModel<LibraryEntry>()
    private val libraryList = JList(libraryModel)
    private val pluginStore = DesktopPluginStore()
    private val pluginModel = DefaultListModel<Path>()
    private val pluginList = JList(pluginModel)
    private val status = JLabel("Connecting to the local source server…")
    private var selectedChapter: SourceChapter? = null

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(event: java.awt.event.WindowEvent?) {
                embeddedSourceServer.stop(0)
            }
        })
        minimumSize = Dimension(960, 640)
        setSize(1120, 720)
        setLocationRelativeTo(null)

        sourcePicker.renderer = renderer { (it as? SourceDescriptor)?.displayName().orEmpty() }
        mangaList.cellRenderer = renderer { (it as? SourceManga)?.title.orEmpty() }
        chapterList.cellRenderer = renderer { (it as? SourceChapter)?.name.orEmpty() }
        pageList.cellRenderer = renderer { (it as? SourcePage)?.let { page -> "${page.index + 1}. ${page.imageUrl}" }.orEmpty() }
        libraryList.cellRenderer = renderer {
            (it as? LibraryEntry)?.let { entry -> "${entry.manga.title}  —  ${entry.manga.author.orEmpty()}" }.orEmpty()
        }
        pluginList.cellRenderer = renderer { (it as? Path)?.fileName?.toString().orEmpty() }

        val toolbar = JPanel(BorderLayout(8, 0)).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(sourcePicker, BorderLayout.WEST)
            add(query, BorderLayout.CENTER)
            add(JPanel().apply {
                add(JButton("Search").apply { addActionListener { search() } })
                add(JButton("Add selected to library").apply { addActionListener { addSelectedToLibrary() } })
            }, BorderLayout.EAST)
        }
        query.addActionListener { search() }
        mangaList.addListSelectionListener {
            if (!it.valueIsAdjusting) mangaList.selectedValue?.let(::loadChapters)
        }
        chapterList.addListSelectionListener {
            if (!it.valueIsAdjusting) chapterList.selectedValue?.let(::loadPages)
        }
        pageList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) openReader(pageList.selectedIndex)
            }
        })
        libraryList.addListSelectionListener {
            if (!it.valueIsAdjusting) libraryList.selectedValue?.let { entry ->
                (0 until sourceModel.size).map(sourceModel::getElementAt)
                    .firstOrNull { source -> source.id == entry.sourceId }?.let {
                    sourcePicker.selectedItem = it
                }
                loadChapters(entry.manga)
            }
        }

        val content = JPanel(GridLayout(1, 3, 8, 0)).apply {
            border = EmptyBorder(0, 12, 12, 12)
            add(labeledList("Titles", mangaList))
            add(labeledList("Chapters", chapterList))
            add(labeledList("Pages — double-click URL to open", pageList))
        }
        val libraryPanel = JPanel(BorderLayout(8, 8)).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(JScrollPane(libraryList), BorderLayout.CENTER)
            add(JPanel().apply {
                add(JButton("Remove selected").apply { addActionListener { removeSelectedFromLibrary() } })
                add(JButton("Refresh library").apply { addActionListener { loadLibrary() } })
                add(JButton("Export backup").apply { addActionListener { exportBackup() } })
                add(JButton("Import backup").apply { addActionListener { importBackup() } })
            }, BorderLayout.NORTH)
        }
        val extensionsPanel = JPanel(BorderLayout(8, 8)).apply {
            border = EmptyBorder(12, 12, 12, 12)
            add(JLabel("Desktop source plugins are trusted Java code. Only install JARs from sources you trust."), BorderLayout.NORTH)
            add(JScrollPane(pluginList), BorderLayout.CENTER)
            add(JPanel().apply {
                add(JButton("Install plugin JAR").apply { addActionListener { installPlugin() } })
                add(JButton("Remove selected").apply { addActionListener { removePlugin() } })
                add(JButton("Refresh list").apply { addActionListener { loadPlugins() } })
            }, BorderLayout.SOUTH)
        }
        val tabs = JTabbedPane().apply {
            addTab("Browse", JPanel(BorderLayout()).apply {
                add(toolbar, BorderLayout.NORTH)
                add(content, BorderLayout.CENTER)
            })
            addTab("Library", libraryPanel)
            addTab("Extensions", extensionsPanel)
        }
        layout = BorderLayout()
        add(tabs, BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        isVisible = true
        loadSources()
        loadLibrary()
        loadPlugins()
    }

    private fun labeledList(title: String, list: JList<*>) = JPanel(BorderLayout()).apply {
        add(JLabel(title), BorderLayout.NORTH)
        add(JScrollPane(list), BorderLayout.CENTER)
    }

    private fun loadSources() {
        task("Loading sources…", { get("/api/sources") }) { body ->
            val sources = json.decodeFromString<List<SourceDescriptor>>(body)
            sourceModel.removeAllElements()
            sources.forEach(sourceModel::addElement)
            status.text = if (sources.isEmpty()) {
                "No desktop source plugins are installed. Add trusted JVM plugins to the source server."
            } else {
                "Connected — ${sources.size} source(s) available"
            }
        }
    }

    private fun search() {
        val source = sourcePicker.selectedItem as? SourceDescriptor ?: run {
            status.text = "No sources are available"
            return
        }
        val encodedQuery = URLEncoder.encode(query.text, StandardCharsets.UTF_8)
        task("Searching ${source.name}…", {
            get("/api/sources/${path(source.id)}/search?q=$encodedQuery&page=1")
        }) { body ->
            val results = json.decodeFromString<SourcePageResult<SourceManga>>(body)
            mangaModel.clear()
            results.items.forEach(mangaModel::addElement)
            status.text = "${results.items.size} title(s) found"
        }
    }

    private fun addSelectedToLibrary() {
        val source = sourcePicker.selectedItem as? SourceDescriptor ?: run {
            status.text = "Choose a source first"
            return
        }
        val manga = mangaList.selectedValue ?: run {
            status.text = "Select a title to add"
            return
        }
        val existing = (0 until libraryModel.size()).map(libraryModel::getElementAt)
            .any { it.sourceId == source.id && it.manga.id == manga.id }
        if (existing) {
            status.text = "Already in your library"
            return
        }
        val entries = (0 until libraryModel.size()).map(libraryModel::getElementAt) +
            LibraryEntry(source.id, manga)
        saveLibrary(entries)
    }

    private fun removeSelectedFromLibrary() {
        val selected = libraryList.selectedValue ?: return
        val entries = (0 until libraryModel.size()).map(libraryModel::getElementAt)
            .filterNot { it.sourceId == selected.sourceId && it.manga.id == selected.manga.id }
        saveLibrary(entries)
    }

    private fun loadLibrary() {
        task("Loading local library…", { json.encodeToString(libraryStore.load()) }) { body ->
            val entries = json.decodeFromString<List<LibraryEntry>>(body)
            libraryModel.clear()
            entries.forEach(libraryModel::addElement)
            status.text = "${entries.size} title(s) in local library"
        }
    }

    private fun loadPlugins() {
        task("Loading desktop plugins…", { pluginStore.installedPlugins() }) { plugins ->
            pluginModel.clear()
            plugins.forEach(pluginModel::addElement)
            status.text = if (plugins.isEmpty()) {
                "No third-party desktop plugins installed — MangaDex is built in"
            } else {
                "${plugins.size} desktop plugin JAR(s) installed; restart Mihon after changes"
            }
        }
    }

    private fun installPlugin() {
        val accepted = JOptionPane.showConfirmDialog(
            this,
            "A plugin can run code on your computer and access files and network resources. " +
                "Install only a JAR from a publisher you trust.",
            "Trust desktop plugin?",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (accepted != JOptionPane.OK_OPTION) return

        val chooser = JFileChooser().apply {
            dialogTitle = "Install a Mihon desktop source plugin"
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Desktop plugin JARs", "jar")
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val selected = chooser.selectedFile.toPath()
        task("Installing ${selected.fileName}…", { pluginStore.install(selected) }) { installed ->
            loadPlugins()
            status.text = "${installed.fileName} installed. Restart Mihon to load it."
        }
    }

    private fun removePlugin() {
        val selected = pluginList.selectedValue ?: return
        val accepted = JOptionPane.showConfirmDialog(
            this,
            "Remove ${selected.fileName}? Restart Mihon to unload it.",
            "Remove desktop plugin",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
        )
        if (accepted != JOptionPane.OK_OPTION) return
        task("Removing ${selected.fileName}…", {
            pluginStore.remove(selected)
            selected.fileName.toString()
        }) { name ->
            loadPlugins()
            status.text = "$name removed. Restart Mihon to unload it."
        }
    }

    private fun saveLibrary(entries: List<LibraryEntry>) {
        task("Saving library…", { json.encodeToString(libraryStore.replace(entries)) }) { saved ->
            val savedEntries = json.decodeFromString<List<LibraryEntry>>(saved)
            libraryModel.clear()
            savedEntries.forEach(libraryModel::addElement)
            status.text = "Library saved (${savedEntries.size} title(s))"
        }
    }

    private fun exportBackup() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Export Mihon library backup"
            selectedFile = Path.of(System.getProperty("user.home"), "mihon-library-backup.json").toFile()
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        val destination = chooser.selectedFile.toPath()
        task("Exporting library backup…", {
            libraryStore.exportBackup(destination)
            destination.toAbsolutePath().toString()
        }) {
            status.text = "Backup exported to $it"
        }
    }

    private fun importBackup() {
        val chooser = JFileChooser().apply { dialogTitle = "Import Mihon library backup" }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        val backup = chooser.selectedFile.toPath()
        task("Importing library backup…", {
            json.encodeToString(libraryStore.mergeBackup(backup))
        }) { result ->
            val merged = json.decodeFromString<List<LibraryEntry>>(result)
            libraryModel.clear()
            merged.forEach(libraryModel::addElement)
            status.text = "Backup imported — ${merged.size} title(s) in library"
        }
    }

    private fun loadChapters(manga: SourceManga) {
        val source = sourcePicker.selectedItem as? SourceDescriptor ?: return
        task("Loading chapters for ${manga.title}…", {
            post("/api/sources/${path(source.id)}/chapters", json.encodeToString(manga))
        }) { body ->
            val chapters = json.decodeFromString<List<SourceChapter>>(body)
            chapterModel.clear()
            chapters.forEach(chapterModel::addElement)
            status.text = "${chapters.size} chapter(s) — ${manga.title}"
        }
    }

    private fun loadPages(chapter: SourceChapter) {
        val source = sourcePicker.selectedItem as? SourceDescriptor ?: return
        task("Loading pages for ${chapter.name}…", {
            val body = post("/api/sources/${path(source.id)}/pages", json.encodeToString(chapter))
            val savedIndex = readingProgressStore.get(source.id, chapter.mangaId, chapter.id)
            body to savedIndex
        }) { (body, savedIndex) ->
            val pages = json.decodeFromString<List<SourcePage>>(body)
            selectedChapter = chapter
            pageModel.clear()
            pages.forEach(pageModel::addElement)
            if (pages.isNotEmpty()) {
                pageList.selectedIndex = (savedIndex ?: 0).coerceIn(0, pages.lastIndex)
            }
            status.text = "${pages.size} page URL(s) — double-click a row to open"
        }
    }

    private fun <T> task(message: String, action: () -> T, onSuccess: (T) -> Unit) {
        status.text = message
        object : SwingWorker<T, Unit>() {
            override fun doInBackground(): T = action()
            override fun done() {
                runCatching { get() }
                    .onSuccess(onSuccess)
                    .onFailure { status.text = "Request failed: ${it.cause?.message ?: it.message}" }
            }
        }.execute()
    }

    private fun get(path: String): String {
        val request = HttpRequest.newBuilder(URI.create(apiBase + path)).GET().build()
        return response(request)
    }

    private fun post(path: String, body: String): String {
        val request = HttpRequest.newBuilder(URI.create(apiBase + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return response(request)
    }

    private fun response(request: HttpRequest): String {
        val result = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(result.statusCode() in 200..299) {
            "Source server returned HTTP ${result.statusCode()}: ${result.body()}"
        }
        return result.body()
    }

    private fun openReader(startIndex: Int) {
        if (startIndex !in 0 until pageModel.size()) return
        val source = sourcePicker.selectedItem as? SourceDescriptor ?: return
        val chapter = selectedChapter ?: return

        val reader = JDialog(this, "Mihon Reader", false)
        val image = JLabel("Loading page…", JLabel.CENTER)
        val counter = JLabel()
        var currentIndex = startIndex
        var worker: SwingWorker<BufferedImage, Unit>? = null

        fun loadPage() {
            val page = pageModel.getElementAt(currentIndex)
            counter.text = "${currentIndex + 1} / ${pageModel.size()}"
            val progress = ReadingProgress(source.id, chapter.mangaId, chapter.id, currentIndex)
            readingProgressWriter.execute {
                try {
                    readingProgressStore.save(progress)
                } catch (error: Exception) {
                    SwingUtilities.invokeLater {
                        status.text = "Unable to save reading progress: ${error.message}"
                    }
                }
            }
            image.icon = null
            image.text = "Loading page ${currentIndex + 1}…"
            worker?.cancel(true)
            worker = object : SwingWorker<BufferedImage, Unit>() {
                override fun doInBackground(): BufferedImage = downloadPage(page)

                override fun done() {
                    if (isCancelled || !reader.isDisplayable) return
                    runCatching { get() }
                        .onSuccess {
                            image.text = null
                            image.icon = ImageIcon(it)
                        }
                        .onFailure {
                            image.icon = null
                            image.text = "Unable to load image: ${it.cause?.message ?: it.message}"
                        }
                }
            }.also { it.execute() }
        }

        val controls = JPanel().apply {
            add(JButton("Previous").apply {
                addActionListener {
                    if (currentIndex > 0) {
                        currentIndex--
                        loadPage()
                    }
                }
            })
            add(counter)
            add(JButton("Next").apply {
                addActionListener {
                    if (currentIndex < pageModel.size() - 1) {
                        currentIndex++
                        loadPage()
                    }
                }
            })
        }
        reader.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        reader.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(event: java.awt.event.WindowEvent?) {
                worker?.cancel(true)
            }
        })
        reader.layout = BorderLayout()
        reader.add(JScrollPane(image), BorderLayout.CENTER)
        reader.add(controls, BorderLayout.SOUTH)
        reader.setSize(1000, 850)
        reader.setLocationRelativeTo(this)
        reader.isVisible = true
        loadPage()
    }

    private fun downloadPage(page: SourcePage): BufferedImage {
        val requestBuilder = HttpRequest.newBuilder(URI.create(page.imageUrl))
            .timeout(java.time.Duration.ofSeconds(40))
            .GET()
        page.headers.forEach(requestBuilder::header)
        val response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() in 200..299) {
            "Image request returned HTTP ${response.statusCode()}"
        }
        return response.body().use(::decodeBoundedImage)
    }

    private fun decodeBoundedImage(input: InputStream): BufferedImage {
        val imageInput = ImageIO.createImageInputStream(input)
            ?: error("Unsupported or invalid image")
        imageInput.use {
            val reader = ImageIO.getImageReaders(imageInput).asSequence().firstOrNull()
                ?: error("No image decoder supports this page")
            try {
                reader.input = imageInput
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val sample = maxOf(
                    1,
                    kotlin.math.ceil(width / MAX_READER_DIMENSION.toDouble()).toInt(),
                    kotlin.math.ceil(height / MAX_READER_DIMENSION.toDouble()).toInt(),
                )
                val parameters = reader.defaultReadParam.apply {
                    setSourceSubsampling(sample, sample, 0, 0)
                }
                return reader.read(0, parameters) ?: error("Unable to decode selected page")
            } finally {
                reader.dispose()
            }
        }
    }

    private fun path(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20")
}

private fun renderer(text: (Any?) -> String) = object : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        this.text = text(value)
        return this
    }
}

private fun SourceDescriptor.displayName(): String = "$name (${language.uppercase()})"

private const val MAX_READER_DIMENSION = 1600
