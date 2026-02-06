package com.unciv.app.desktop

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.badlogic.gdx.utils.Os
import com.badlogic.gdx.utils.SharedLibraryLoader
import com.unciv.UncivGame
import com.unciv.app.desktop.DesktopScreenMode.Companion.getMaximumWindowBounds
import com.unciv.json.json
import com.unciv.logic.files.SETTINGS_FILE_NAME
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.ScreenSize
import com.unciv.models.metadata.GameSettings.WindowState
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.UniqueAutoUpdater
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Display
import com.unciv.utils.Log
import org.lwjgl.system.Configuration
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Taskbar
import java.awt.Toolkit
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess


internal object DesktopLauncher {

    @JvmStatic
    fun main(arg: Array<String>) {

        // Find the index of the "-creategame" parameter
        val createGameArg = arg.find { it.startsWith("--creategame=") }
        if (createGameArg != null) {
            val settingsPath = createGameArg.substringAfter("=")
            val game = UncivGame(true)

            UncivGame.Current = game
            UncivGame.Current.settings = GameSettings()
            RulesetCache.loadRulesets(consoleMode = true, noMods = false)
            // Call the function to create a game with the specified path
            runBlocking {
                CreateGameFromSettings.startGame(settingsPath)
            }
            exitProcess(0)
        }

        val visibilitySavePath = parseVisibilityCheckPath(arg)
        if (visibilitySavePath != null) {
            val ok = runVisibilityCheck(visibilitySavePath)
            exitProcess(if (ok) 0 else 1)
        }

        // The uniques checker requires the file system to be set up, which happens after lwjgl initializes it
        if (arg.isNotEmpty() && arg[0] == "mod-ci") {
            ImagePacker.packImagesPerMod(".", ".")
            val ruleset = Ruleset()
            ruleset.folderLocation = FileHandle(".")
            val jsonsFolder = FileHandle("jsons")
            if (jsonsFolder.exists()) {
                // Load vanilla ruleset from the JAR, in case the mod requires parts of it
                RulesetCache.loadRulesets(consoleMode = true, noMods = true)
                // Load the actual ruleset here
                ruleset.load(jsonsFolder)
            }
            UniqueAutoUpdater.autoupdateUniques(ruleset)
            val errors = RulesetValidator.create(ruleset, true).getErrorList()
            println(errors.getErrorText(true))
            exitProcess(if (errors.any { it.errorSeverityToReport == RulesetErrorSeverity.Error }) 1 else 0)
        }

        if (arg.isNotEmpty() && arg[0] == "--version") {
            println(UncivGame.VERSION.text)
            exitProcess(0)
        }

        if (SharedLibraryLoader.os == Os.MacOsX) {
            Configuration.GLFW_LIBRARY_NAME.set("glfw_async")
            // Since LibGDX 1.13.1 on Mac you cannot call Lwjgl3ApplicationConfiguration.getPrimaryMonitor()
            //  before GraphicsEnvironment.getLocalGraphicsEnvironment().
            GraphicsEnvironment.getLocalGraphicsEnvironment()
        }

        val customDataDirPrefix="--data-dir="
        val customDataDir = arg.find { it.startsWith(customDataDirPrefix) }?.removePrefix(customDataDirPrefix)

        // Setup Desktop logging
        Log.backend = DesktopLogBackend()

        // Setup Desktop display
        Display.platform = DesktopDisplay()

        // Setup Desktop font
        Fonts.fontImplementation = DesktopFont()

        // Setup Desktop saver-loader
        UncivFiles.saverLoader = if (LinuxX11SaverLoader.isRequired()) LinuxX11SaverLoader() else DesktopSaverLoader()
        UncivFiles.preferExternalStorage = false

        // Solves a rendering problem in specific GPUs and drivers.
        // For more info see https://github.com/yairm210/Unciv/pull/3202 and https://github.com/LWJGL/lwjgl/issues/119
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")

        val dataDirectory = customDataDir ?: "."

        val isRunFromJAR = DesktopLauncher.javaClass.`package`.specificationVersion != null
        ImagePacker.packImages(isRunFromJAR, dataDirectory)

        val config = Lwjgl3ApplicationConfiguration()
        config.setWindowIcon("ExtraImages/Icons/Unciv32.png", "ExtraImages/Icons/Unciv128.png")
        if (SharedLibraryLoader.os == Os.MacOsX) updateDockIconForMacOs("ExtraImages/Icons/Unciv128.png")
        config.setTitle("Unciv")
        config.setHdpiMode(HdpiMode.Logical)
        config.setWindowSizeLimits(WindowState.minimumWidth, WindowState.minimumHeight, -1, -1)


        // LibGDX not yet configured, use regular java class
        val maximumWindowBounds = getMaximumWindowBounds()


        val settings = UncivFiles.getSettingsForPlatformLaunchers(dataDirectory)
        if (settings.isFreshlyCreated) {
            settings.screenSize = ScreenSize.Large // By default we guess that Desktops have larger screens
            settings.windowState = WindowState(maximumWindowBounds)

            FileHandle(dataDirectory + File.separator + SETTINGS_FILE_NAME).writeString(json().toJson(settings), false, Charsets.UTF_8.name()) // so when we later open the game we get fullscreen
        }
        // Kludge! This is a workaround - the matching call in DesktopDisplay doesn't "take" quite permanently,
        // the window might revert to the "config" values when the user moves the window - worse if they
        // minimize/restore. And the config default is 640x480 unless we set something here.
        val (width, height) = settings.windowState.coerceIn(maximumWindowBounds)
        config.setWindowedMode(width, height)

        config.setInitialBackgroundColor(BaseScreen.clearColor)

        if (!isRunFromJAR) {
            UniqueDocsWriter().write()
            UiElementDocsWriter().write()
        }



        // HardenGdxAudio extends Lwjgl3Application, and the Lwjgl3Application constructor runs as long as the game runs
        HardenGdxAudio(DesktopGame(config, customDataDir), config)
        exitProcess(0)
    }

    private fun runVisibilityCheck(savePath: String): Boolean {
        val success = AtomicBoolean(true)
        val done = CountDownLatch(1)

        val config = HeadlessApplicationConfiguration().apply {
            updatesPerSecond = -1 // run create() once, skip render loop
        }

        val listener = object : ApplicationListener {
            override fun create() {
                try {
                    runVisibilityCheckInternal(savePath)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    success.set(false)
                } finally {
                    done.countDown()
                    Gdx.app.exit()
                }
            }

            override fun resize(width: Int, height: Int) {}
            override fun render() {}
            override fun pause() {}
            override fun resume() {}
            override fun dispose() {}
        }

        HeadlessApplication(listener, config)
        done.await()
        return success.get()
    }

    private fun runVisibilityCheckInternal(savePath: String) {
        val game = UncivGame(true)
        UncivGame.Current = game
        UncivGame.Current.settings = GameSettings()

        RulesetCache.loadRulesets(consoleMode = true, noMods = false)

        val saveData = Files.readString(Paths.get(savePath))
        val gameInfo = UncivFiles.gameInfoFromString(saveData)

        val civ = gameInfo.getCivilization(gameInfo.currentPlayer)
        val exploredTiles = gameInfo.tileMap.values.filter { it.isExplored(civ) }
        val viewableTiles = civ.viewableTiles
        val viewableNotExplored = viewableTiles.filter { !it.isExplored(civ) }
        val exploredOutsideRegion = exploredTiles.filter { !civ.exploredRegion.isPositionInRegion(it.position) }

        println("Visibility check for: $savePath")
        println("Map: shape=${gameInfo.tileMap.mapParameters.shape}, size=${gameInfo.tileMap.mapParameters.mapSize.name}, worldWrap=${gameInfo.tileMap.mapParameters.worldWrap}")
        println("Current player: ${civ.civName}")
        println("Tiles: total=${gameInfo.tileMap.values.size}, viewable=${viewableTiles.size}, explored=${exploredTiles.size}")
        println("Viewable but not explored: ${viewableNotExplored.size}")
        if (viewableNotExplored.isNotEmpty()) {
            println("  Sample (up to 10): " + viewableNotExplored.take(10).joinToString { it.position.toString() })
        }
        println("Explored but outside exploredRegion: ${exploredOutsideRegion.size}")
        if (exploredOutsideRegion.isNotEmpty()) {
            println("  Sample (up to 10): " + exploredOutsideRegion.take(10).joinToString { it.position.toString() })
        }

        val worldBounds = civ.exploredRegion.getWorldBounds()
        if (worldBounds != null) {
            println("ExploredRegion world bounds: x=${worldBounds.x}, y=${worldBounds.y}, w=${worldBounds.width}, h=${worldBounds.height}")
        } else {
            println("ExploredRegion bounds: left=${civ.exploredRegion.getLeftX()}, right=${civ.exploredRegion.getRightX()}, top=${civ.exploredRegion.getTopY()}, bottom=${civ.exploredRegion.getBottomY()}")
        }
    }

    private fun updateDockIconForMacOs(fileName: String) {
        try {
            val defaultToolkit: Toolkit = Toolkit.getDefaultToolkit()
            val imageResource: URL = FileHandle(fileName).file().toURI().toURL()
            val image: Image = defaultToolkit.getImage(imageResource)
            val taskbar = Taskbar.getTaskbar()
            taskbar.iconImage = image
        } catch (_: Throwable) { }
    }

    private fun parseVisibilityCheckPath(args: Array<String>): String? {
        val index = args.indexOfFirst { it == "--check-visibility" || it.startsWith("--check-visibility=") }
        if (index < 0) return null

        val initial = args[index].substringAfter("=", "")
        val extra = args.drop(index + 1).takeWhile { !it.startsWith("--") }

        val parts = ArrayList<String>(1 + extra.size)
        if (initial.isNotBlank()) parts.add(initial)
        parts.addAll(extra)

        return parts.joinToString(" ").trim().ifEmpty { null }
    }
}
