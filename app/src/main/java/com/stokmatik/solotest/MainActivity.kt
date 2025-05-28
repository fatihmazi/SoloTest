package com.stokmatik.solotest

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.widget.LinearLayout
import android.widget.GridLayout
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import android.widget.ScrollView
import android.widget.EditText
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.database.*
import com.google.firebase.auth.FirebaseAuth
import androidx.appcompat.app.AlertDialog
import android.content.res.Configuration
import android.media.MediaPlayer

class MainActivity : Activity() {

    private lateinit var gameEngine: GameEngine
    private lateinit var tvMoveCount: TextView
    private lateinit var tvGameTime: TextView // Kalan Taş yerine Oynama Süresi
    private lateinit var gameGrid: GridLayout
    private var cellViews = mutableListOf<View>()
    private var selectedPosition: Int? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var gameStartTime: Long = 0
    private var playerName: String = ""
    private lateinit var firebaseDatabase: FirebaseDatabase
    private lateinit var leaderboardRef: DatabaseReference

    // Zaman takibi için
    private var isGameStarted = false
    private var gameTimeHandler = Handler(Looper.getMainLooper())
    private var gameTimeRunnable: Runnable? = null
    private var currentGameTimeSeconds = 0

    // Ses dosyaları için
    private var perfectScorePlayer: MediaPlayer? = null
    private var zeroScorePlayer: MediaPlayer? = null

    // Oyun geçmişi için data class
    data class GameRecord(
        val remainingPegs: Int,
        val score: Int,
        val moves: Int,
        val date: String
    )

    // Liderlik tablosu için data class
    data class LeaderRecord(
        val playerName: String,
        val remainingPegs: Int,
        val score: Int,
        val moves: Int,
        val gameTimeSeconds: Int,
        val date: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase başlat
        try {
            firebaseDatabase = FirebaseDatabase.getInstance()
            leaderboardRef = firebaseDatabase.getReference("leaderboard")
        } catch (e: Exception) {
            Toast.makeText(this, "Firebase bağlantı sorunu", Toast.LENGTH_SHORT).show()
        }

        // Ses dosyalarını yükle
        initializeSoundFiles()

        // Diğer başlangıç kodları
        sharedPreferences = getSharedPreferences("SoloTestHistory", MODE_PRIVATE)
        loadPlayerName()
        setupLayout()
        setupGame()
    }

    // Orientation değişiminde oyunun resetlenmemesi için
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Hiçbir şey yapma, oyun devam etsin
    }

    // Ses dosyalarını başlat - Debug mesajları ile
    private fun initializeSoundFiles() {
        try {
            // 200 puan (mükemmel) için ses dosyası
            val perfectResourceId = resources.getIdentifier("perfect_score", "raw", packageName)
            if (perfectResourceId != 0) {
                perfectScorePlayer = MediaPlayer.create(this, perfectResourceId)
                Toast.makeText(this, "Perfect score ses dosyası yüklendi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Perfect score ses dosyası bulunamadı!", Toast.LENGTH_SHORT).show()
            }

            // 0 puan için ses dosyası
            val zeroResourceId = resources.getIdentifier("zero_score", "raw", packageName)
            if (zeroResourceId != 0) {
                zeroScorePlayer = MediaPlayer.create(this, zeroResourceId)
                Toast.makeText(this, "Zero score ses dosyası yüklendi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Zero score ses dosyası bulunamadı!", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ses dosyası hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Oyun zamanını başlat
    private fun startGameTimer() {
        if (!isGameStarted) {
            isGameStarted = true
            gameStartTime = System.currentTimeMillis()
            currentGameTimeSeconds = 0
            startTimeUpdate()
        }
    }

    // Zaman güncellemesini başlat
    private fun startTimeUpdate() {
        gameTimeRunnable = object : Runnable {
            override fun run() {
                if (isGameStarted) {
                    currentGameTimeSeconds = ((System.currentTimeMillis() - gameStartTime) / 1000).toInt()
                    updateTimeDisplay()
                    gameTimeHandler.postDelayed(this, 1000) // Her saniye güncelle
                }
            }
        }
        gameTimeHandler.post(gameTimeRunnable!!)
    }

    // Zaman görüntüsünü güncelle
    private fun updateTimeDisplay() {
        val minutes = currentGameTimeSeconds / 60
        val seconds = currentGameTimeSeconds % 60
        tvGameTime.text = "Süre: ${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    // Oyun zamanını durdur
    private fun stopGameTimer() {
        isGameStarted = false
        gameTimeRunnable?.let { gameTimeHandler.removeCallbacks(it) }
    }

    private fun loadPlayerName() {
        playerName = sharedPreferences.getString("player_name", "") ?: ""
        if (playerName.isEmpty()) {
            playerName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        }
    }

    private fun setupLayout() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val boardSize = (screenWidth * 0.9).toInt()
        val topSpace = (screenHeight * 0.08).toInt()
        val bottomSpace = (screenHeight * 0.08).toInt()

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.parseColor("#2E7D32"))
        }

        // Üst boşluk
        val topSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, topSpace)
        }
        mainLayout.addView(topSpacer)

        // Başlık
        val title = TextView(this).apply {
            text = "🎮 Solo Test: Zekâ Tahtası"
            textSize = 24f
            setPadding(0, 0, 0, 8)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        mainLayout.addView(title)

        // Durum bilgisi
        val statusLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 0, 32, 16)
        }

        tvMoveCount = TextView(this).apply {
            text = "Hamle: 0"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Kalan Taş yerine Oynama Süresi
        tvGameTime = TextView(this).apply {
            text = "Süre: 0:00"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        statusLayout.addView(tvMoveCount)
        statusLayout.addView(tvGameTime)
        mainLayout.addView(statusLayout)

        // Orta alan - oyun tahtası
        val centerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val boardContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(boardSize, boardSize).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            background = createCircularBackground()
        }

        gameGrid = GridLayout(this).apply {
            rowCount = 7
            columnCount = 7
            setPadding(boardSize/12, boardSize/12, boardSize/12, boardSize/12)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        boardContainer.addView(gameGrid)
        centerLayout.addView(boardContainer)
        mainLayout.addView(centerLayout)

        // Butonlar
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val btnRestart = Button(this).apply {
            text = "Yeniden Başla"
            setOnClickListener { restartGame() }
            background = createButtonBackground(Color.parseColor("#F44336"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        val btnUndo = Button(this).apply {
            text = "Geri Al"
            setOnClickListener { undoMove() }
            background = createButtonBackground(Color.parseColor("#FF9800"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        val btnHint = Button(this).apply {
            text = "İpucu"
            setOnClickListener { showHint() }
            background = createButtonBackground(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        buttonLayout.addView(btnRestart)
        buttonLayout.addView(btnUndo)
        buttonLayout.addView(btnHint)
        mainLayout.addView(buttonLayout)

        // İkinci satır butonlar
        val buttonLayout2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val btnHistory = Button(this).apply {
            text = "Geçmiş"
            setOnClickListener { showHistoryDialog() }
            background = createButtonBackground(Color.parseColor("#9C27B0"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        val btnClearHistory = Button(this).apply {
            text = "Temizle"
            setOnClickListener { clearHistory() }
            background = createButtonBackground(Color.parseColor("#795548"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        buttonLayout2.addView(btnHistory)
        buttonLayout2.addView(btnClearHistory)
        mainLayout.addView(buttonLayout2)

        // Üçüncü satır butonlar
        val buttonLayout3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        val btnLeaderboard = Button(this).apply {
            text = "Liderler"
            setOnClickListener { showLeaderboard() }
            background = createButtonBackground(Color.parseColor("#FF5722"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        val btnChangeName = Button(this).apply {
            text = "İsim"
            setOnClickListener { changePlayerName() }
            background = createButtonBackground(Color.parseColor("#607D8B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 0, 8, 0) }
        }

        buttonLayout3.addView(btnLeaderboard)
        buttonLayout3.addView(btnChangeName)
        mainLayout.addView(buttonLayout3)

        // Alt boşluk
        val bottomSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, bottomSpace)
        }
        mainLayout.addView(bottomSpacer)

        setContentView(mainLayout)
    }

    private fun createCircularBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            colors = intArrayOf(
                Color.parseColor("#4CAF50"),
                Color.parseColor("#388E3C")
            )
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 300f
            setStroke(8, Color.parseColor("#1B5E20"))
        }
    }

    private fun createButtonBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 20f
        }
    }

    private fun createCellBackground(type: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            when (type) {
                "empty" -> {
                    setColor(Color.parseColor("#66BB6A"))
                    setStroke(4, Color.parseColor("#2E7D32"))
                }
                "peg" -> {
                    colors = intArrayOf(
                        Color.parseColor("#FFD54F"),
                        Color.parseColor("#FFC107")
                    )
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = 25f
                    setStroke(3, Color.parseColor("#FF8F00"))
                }
                "selected" -> {
                    colors = intArrayOf(
                        Color.parseColor("#FF7043"),
                        Color.parseColor("#FF5722")
                    )
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = 25f
                    setStroke(4, Color.parseColor("#BF360C"))
                }
                "valid_move" -> {
                    setColor(Color.parseColor("#81C784"))
                    setStroke(4, Color.parseColor("#4CAF50"))
                }
            }
        }
    }

    private fun setupGame() {
        gameEngine = GameEngine()
        createGameBoard()
        updateUI()
        // Oyun zamanlayıcısını sıfırla ama başlatma
        isGameStarted = false
        currentGameTimeSeconds = 0
        updateTimeDisplay()
    }

    private fun createGameBoard() {
        gameGrid.removeAllViews()
        cellViews.clear()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val boardSize = (screenWidth * 0.9).toInt()
        val cellSize = (boardSize - boardSize/6) / 9
        val margin = cellSize / 10

        for (position in 0 until GameEngine.TOTAL_CELLS) {
            val row = position / GameEngine.BOARD_SIZE
            val col = position % GameEngine.BOARD_SIZE
            val cellType = gameEngine.getBoard()[row][col]

            val cellView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(margin, margin, margin, margin)
                }

                when (cellType) {
                    CellType.INVALID -> {
                        visibility = View.INVISIBLE
                    }
                    CellType.EMPTY -> {
                        background = createCellBackground("empty")
                        setOnClickListener { onCellClicked(position) }
                    }
                    CellType.PEG -> {
                        background = createCellBackground("peg")
                        setOnClickListener { onCellClicked(position) }
                    }
                }
            }

            cellViews.add(cellView)
            gameGrid.addView(cellView)
        }
    }

    private fun onCellClicked(position: Int) {
        // İlk hamle ile zamanı başlat
        if (!isGameStarted) {
            startGameTimer()
        }

        val result = gameEngine.handleCellClick(position)

        when (result.type) {
            ClickResult.Type.PEG_SELECTED -> {
                selectedPosition = position
                updateBoardVisuals()
                result.validMoves?.forEach { movePos ->
                    cellViews[movePos].background = createCellBackground("valid_move")
                    cellViews[movePos].setOnClickListener { onCellClicked(movePos) }
                }
            }
            ClickResult.Type.MOVE_MADE -> {
                selectedPosition = null
                updateBoardVisuals()
                updateUI()
                checkGameEnd()
            }
            ClickResult.Type.INVALID_MOVE -> {
                Toast.makeText(this, "Geçersiz hamle!", Toast.LENGTH_SHORT).show()
            }
            ClickResult.Type.DESELECTED -> {
                selectedPosition = null
                updateBoardVisuals()
            }
        }
    }

    private fun updateBoardVisuals() {
        for (position in 0 until GameEngine.TOTAL_CELLS) {
            val row = position / GameEngine.BOARD_SIZE
            val col = position % GameEngine.BOARD_SIZE
            val cellType = gameEngine.getBoard()[row][col]
            val cellView = cellViews[position]

            when (cellType) {
                CellType.INVALID -> {
                    cellView.visibility = View.INVISIBLE
                }
                CellType.EMPTY -> {
                    cellView.background = createCellBackground("empty")
                    cellView.setOnClickListener { onCellClicked(position) }
                }
                CellType.PEG -> {
                    cellView.background = if (position == selectedPosition) {
                        createCellBackground("selected")
                    } else {
                        createCellBackground("peg")
                    }
                    cellView.setOnClickListener { onCellClicked(position) }
                }
            }
        }
    }

    private fun updateUI() {
        tvMoveCount.text = "Hamle: ${gameEngine.getMoveCount()}"
        // Süre güncellemesi ayrı fonksiyonda
    }

    private fun checkGameEnd() {
        val gameState = gameEngine.getGameState()
        val remainingPegs = gameEngine.getRemainingPegsCount()

        when (gameState) {
            GameState.PERFECT_WIN -> {
                stopGameTimer() // Oyun bittiğinde zamanı durdur
                Toast.makeText(this, "🎉 Mükemmel! Tek taş ortada kaldı!", Toast.LENGTH_LONG).show()
                playScoreSound(200) // 200 puan için ses çal
                saveGameRecord(remainingPegs)
                showScoreDialog(remainingPegs)
            }
            GameState.WIN -> {
                stopGameTimer()
                Toast.makeText(this, "👏 Tebrikler! Tek taş kaldı!", Toast.LENGTH_LONG).show()
                saveGameRecord(remainingPegs)
                showScoreDialog(remainingPegs)
            }
            GameState.GAME_OVER -> {
                stopGameTimer()
                Toast.makeText(this, "🔄 Oyun bitti! $remainingPegs taş kaldı", Toast.LENGTH_LONG).show()
                val score = when (remainingPegs) {
                    1 -> 200; 2 -> 175; 3 -> 150; 4 -> 125; 5 -> 100; 6 -> 75; 7 -> 50; 8 -> 25; else -> 0
                }
                if (score == 0) playScoreSound(0) // 0 puan için ses çal
                saveGameRecord(remainingPegs)
                showScoreDialog(remainingPegs)
            }
            GameState.IN_PROGRESS -> {
                // Oyun devam ediyor
            }
        }
    }

    // Ses çalma fonksiyonu - Debug mesajları ile
    private fun playScoreSound(score: Int) {
        try {
            when (score) {
                200 -> {
                    Toast.makeText(this, "200 puan sesi çalınıyor...", Toast.LENGTH_SHORT).show()
                    perfectScorePlayer?.let { player ->
                        if (player.isPlaying) {
                            player.stop()
                            player.prepare()
                        }
                        player.start()
                        Toast.makeText(this, "Perfect score ses başlatıldı!", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(this, "Perfect score player null!", Toast.LENGTH_SHORT).show()
                    }
                }
                0 -> {
                    Toast.makeText(this, "0 puan sesi çalınıyor...", Toast.LENGTH_SHORT).show()
                    zeroScorePlayer?.let { player ->
                        if (player.isPlaying) {
                            player.stop()
                            player.prepare()
                        }
                        player.start()
                        Toast.makeText(this, "Zero score ses başlatıldı!", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(this, "Zero score player null!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ses çalma hatası: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showScoreDialog(remainingPegs: Int) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val score = when (remainingPegs) {
            1 -> 200
            2 -> 175
            3 -> 150
            4 -> 125
            5 -> 100
            6 -> 75
            7 -> 50
            8 -> 25
            else -> 0
        }

        val imageFileName = "score_${if (remainingPegs <= 8) remainingPegs else 9}"

        val dialogContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
            isFocusable = true
        }

        val dialogWidth = (screenWidth * 0.9f).toInt()
        val dialogHeight = (dialogWidth * 8 / 6)

        val dialogLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dialogWidth, dialogHeight).apply {
                gravity = android.view.Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 25f
                setStroke(4, Color.parseColor("#E0E0E0"))
            }
            elevation = 20f
        }

        // Resim %20 daha büyük gösterilsin
        val imageHeight = (dialogHeight * 0.65f * 1.2f).toInt() // %20 büyük
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                imageHeight
            ).apply {
                gravity = android.view.Gravity.TOP
                setMargins(25, 25, 25, 0)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP // Sığmazsa kenar kırpma

            try {
                val resourceId = resources.getIdentifier(imageFileName, "drawable", packageName)
                if (resourceId != 0) {
                    setImageResource(resourceId)
                } else {
                    setBackgroundColor(Color.parseColor("#F5F5F5"))
                }
            } catch (e: Exception) {
                setBackgroundColor(Color.parseColor("#F5F5F5"))
            }
        }

        // X tuşunu 60x60 piksel boyutunda ve tam ortalanmış
        val closeButtonSize = 60
        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 20f // Boyuta uygun arttırdık
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER // Yatay ve dikey ortalama
            layoutParams = FrameLayout.LayoutParams(closeButtonSize, closeButtonSize).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(0, 15, 15, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F44336"))
            }

            setOnClickListener {
                try {
                    val rootView = window.decorView as FrameLayout
                    rootView.removeView(dialogContainer)
                } catch (e: Exception) {
                    dialogContainer.visibility = View.GONE
                }
            }
        }

        // Puan yazısını alanına daha iyi sığacak şekilde düzenle
        val scoreTextHeight = 150 // Daha küçük alan
        val scoreText = TextView(this).apply {
            text = "🎯 $score Puan"
            textSize = 22f // Boyutu düzenledik
            setTextColor(Color.parseColor("#2E7D32"))
            gravity = android.view.Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                scoreTextHeight
            ).apply {
                gravity = android.view.Gravity.BOTTOM
                setMargins(30, 0, 30, 60)
            }
            setPadding(25, 20, 25, 20) // padding azaltıldı
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E8F5E8"))
                cornerRadius = 25f
                setStroke(3, Color.parseColor("#4CAF50"))
            }
        }

        dialogLayout.addView(imageView)
        dialogLayout.addView(closeButton)
        dialogLayout.addView(scoreText)
        dialogContainer.addView(dialogLayout)

        val rootView = window.decorView as FrameLayout
        rootView.addView(dialogContainer)
    }

    private fun restartGame() {
        selectedPosition = null
        stopGameTimer() // Zamanı durdur
        gameEngine.resetGame()
        createGameBoard()
        updateUI()
        // Oyun zamanlayıcısını sıfırla ama başlatma
        isGameStarted = false
        currentGameTimeSeconds = 0
        updateTimeDisplay()
    }

    // Geri Al - Oyun bittiğinde çalışmaz
    private fun undoMove() {
        val gameState = gameEngine.getGameState()
        if (gameState == GameState.GAME_OVER || gameState == GameState.WIN || gameState == GameState.PERFECT_WIN) {
            Toast.makeText(this, "🚫 Oyun bittiği için geri alma yapılamaz!", Toast.LENGTH_SHORT).show()
            return
        }

        if (gameEngine.undoMove()) {
            selectedPosition = null
            updateBoardVisuals()
            updateUI()
            Toast.makeText(this, "↩️ Hamle geri alındı", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Geri alınacak hamle yok!", Toast.LENGTH_SHORT).show()
        }
    }

    // İpucu - Renkli animasyon ile
    private fun showHint() {
        val gameState = gameEngine.getGameState()
        if (gameState == GameState.GAME_OVER || gameState == GameState.WIN || gameState == GameState.PERFECT_WIN) {
            Toast.makeText(this, "🚫 Oyun bittiği için ipucu verilemez!", Toast.LENGTH_SHORT).show()
            return
        }

        val hint = gameEngine.getHint()
        if (hint != null) {
            Toast.makeText(this, "💡 İpucu gösteriliyor...", Toast.LENGTH_SHORT).show()
            showHintVisual(hint.from, hint.to)
        } else {
            Toast.makeText(this, "❌ Geçerli hamle bulunamadı!", Toast.LENGTH_SHORT).show()
        }
    }

    // İpucu görsel efekti
    private fun showHintVisual(fromPosition: Int, toPosition: Int) {
        try {
            val originalFromBackground = cellViews[fromPosition].background
            val originalToBackground = cellViews[toPosition].background

            // Başlangıç taşı - koyu sarı
            cellViews[fromPosition].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#FF8F00"),
                    Color.parseColor("#F57C00")
                )
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = 25f
                setStroke(4, Color.parseColor("#E65100"))
            }

            // Hedef pozisyon - beyaz
            cellViews[toPosition].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(4, Color.parseColor("#2196F3"))
            }

            // 1 saniye sonra eski renklerine döndür
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    cellViews[fromPosition].background = originalFromBackground
                    cellViews[toPosition].background = originalToBackground
                    Toast.makeText(this, "💡 ${fromPosition} → ${toPosition} hamlesi önerilir", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    // Sessizce devam et
                }
            }, 1000)

        } catch (e: Exception) {
            Toast.makeText(this, "⚠️ İpucu gösterme hatası", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveGameRecord(remainingPegs: Int) {
        val score = when (remainingPegs) {
            1 -> 200
            2 -> 175
            3 -> 150
            4 -> 125
            5 -> 100
            6 -> 75
            7 -> 50
            8 -> 25
            else -> 0
        }

        val dateFormat = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        // Geçmiş kayıt
        val existingHistory = getGameHistory().toMutableList()
        val newRecord = GameRecord(remainingPegs, score, gameEngine.getMoveCount(), currentDate)
        existingHistory.add(0, newRecord)

        if (existingHistory.size > 20) {
            existingHistory.removeAt(existingHistory.size - 1)
        }

        val editor = sharedPreferences.edit()
        existingHistory.forEachIndexed { index, record ->
            editor.putString("game_$index", "${record.remainingPegs},${record.score},${record.moves},${record.date}")
        }
        editor.putInt("history_count", existingHistory.size)

        // Liderlik tablosuna kaydet - currentGameTimeSeconds kullan
        saveLeaderRecord(remainingPegs, score, currentGameTimeSeconds, currentDate)
        editor.apply()
    }

    // Liderlik kaydı - tarih ekleme ile
    private fun saveLeaderRecord(remainingPegs: Int, score: Int, gameTimeSeconds: Int, date: String) {
        val dateOnly = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

        val newLeaderRecord = mapOf(
            "playerName" to playerName,
            "remainingPegs" to remainingPegs,
            "score" to score,
            "moves" to gameEngine.getMoveCount(),
            "gameTimeSeconds" to gameTimeSeconds,
            "date" to date,
            "dateOnly" to dateOnly, // Bugün için eklenen alan
            "timestamp" to System.currentTimeMillis()
        )

        leaderboardRef.push().setValue(newLeaderRecord)
            .addOnSuccessListener {
                // Başarılı kayıt - sessiz
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Liderlik kaydı başarısız", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getGameHistory(): List<GameRecord> {
        val history = mutableListOf<GameRecord>()
        val count = sharedPreferences.getInt("history_count", 0)

        for (i in 0 until count) {
            val recordString = sharedPreferences.getString("game_$i", "") ?: ""
            if (recordString.isNotEmpty()) {
                val parts = recordString.split(",")
                if (parts.size == 4) {
                    history.add(
                        GameRecord(
                            remainingPegs = parts[0].toInt(),
                            score = parts[1].toInt(),
                            moves = parts[2].toInt(),
                            date = parts[3]
                        )
                    )
                }
            }
        }
        return history
    }

    private fun showHistoryDialog() {
        val history = getGameHistory()

        if (history.isEmpty()) {
            Toast.makeText(this, "Henüz oyun geçmişi yok!", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
        }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                (resources.displayMetrics.heightPixels * 0.8).toInt()
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setPadding(20, 20, 20, 20)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 20f
            }
        }

        val titleText = TextView(this).apply {
            text = "📊 Oyun Geçmişi (Son ${history.size})"
            textSize = 20f
            setTextColor(Color.parseColor("#2E7D32"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        dialogLayout.addView(titleText)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val historyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        history.forEachIndexed { index, record ->
            val recordView = TextView(this).apply {
                text = "${index + 1}. ${record.date} | ${record.remainingPegs} taş | ${record.score} puan | ${record.moves} hamle"
                textSize = 14f
                setTextColor(Color.parseColor("#424242"))
                setPadding(15, 10, 15, 10)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (index % 2 == 0) Color.parseColor("#F5F5F5") else Color.WHITE)
                    cornerRadius = 8f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
            }
            historyLayout.addView(recordView)
        }

        scrollView.addView(historyLayout)
        dialogLayout.addView(scrollView)

        val closeButton = Button(this).apply {
            text = "Kapat"
            setOnClickListener {
                val rootView = window.decorView as FrameLayout
                rootView.removeView(dialogContainer)
            }
            background = createButtonBackground(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 20, 0, 0)
            }
        }
        dialogLayout.addView(closeButton)

        dialogContainer.addView(dialogLayout)
        val rootView = window.decorView as FrameLayout
        rootView.addView(dialogContainer)
    }

    private fun clearHistory() {
        AlertDialog.Builder(this)
            .setTitle("Geçmişi Temizle")
            .setMessage("Tüm oyun geçmişini silmek istediğinizden emin misiniz?")
            .setPositiveButton("Evet") { _, _ ->
                sharedPreferences.edit().clear().apply()
                Toast.makeText(this, "Oyun geçmişi temizlendi!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hayır", null)
            .show()
    }

    // Çifte liderlik tablosu sistemi
    private fun showLeaderboard() {
        Toast.makeText(this, "🏆 Liderlik tabloları yükleniyor...", Toast.LENGTH_SHORT).show()

        leaderboardRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.childrenCount == 0L) {
                // İlk defa açılıyor, test verileri ekle
                addSampleLeaderData()
            } else {
                // Veri var, çifte tablo göster
                showDualLeaderDialog(snapshot)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "❌ Bağlantı hatası", Toast.LENGTH_SHORT).show()
        }
    }

    // Test verileri ekleme
    private fun addSampleLeaderData() {
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        val yesterday = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000))

        val sampleData = listOf(
            mapOf("playerName" to "Ahmet K.", "remainingPegs" to 1, "score" to 200, "moves" to 28, "gameTimeSeconds" to 180, "date" to "27.05 15:30", "dateOnly" to today),
            mapOf("playerName" to "Mehmet A.", "remainingPegs" to 1, "score" to 200, "moves" to 30, "gameTimeSeconds" to 220, "date" to "27.05 14:20", "dateOnly" to today),
            mapOf("playerName" to "Ayşe B.", "remainingPegs" to 2, "score" to 175, "moves" to 25, "gameTimeSeconds" to 160, "date" to "27.05 13:10", "dateOnly" to today),
            mapOf("playerName" to "Fatma C.", "remainingPegs" to 1, "score" to 200, "moves" to 35, "gameTimeSeconds" to 300, "date" to "26.05 18:45", "dateOnly" to yesterday),
            mapOf("playerName" to "Ali D.", "remainingPegs" to 3, "score" to 150, "moves" to 22, "gameTimeSeconds" to 140, "date" to "26.05 16:30", "dateOnly" to yesterday),
            mapOf("playerName" to "Zeynep E.", "remainingPegs" to 2, "score" to 175, "moves" to 27, "gameTimeSeconds" to 200, "date" to "25.05 12:15", "dateOnly" to "25.05.2025")
        )

        sampleData.forEach { data ->
            leaderboardRef.push().setValue(data)
        }

        Toast.makeText(this, "✅ Örnek veriler eklendi, tekrar deneyin!", Toast.LENGTH_SHORT).show()
    }

    // Çifte liderlik tablosu dialog'u - Benzersiz liderlik sistemi ile
    private fun showDualLeaderDialog(snapshot: DataSnapshot) {
        val allLeaders = mutableListOf<LeaderRecord>()
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())

        // Verileri oku
        for (child in snapshot.children) {
            val name = child.child("playerName").getValue(String::class.java) ?: "?"
            val pegs = child.child("remainingPegs").getValue(Int::class.java) ?: 9
            val score = child.child("score").getValue(Int::class.java) ?: 0
            val time = child.child("gameTimeSeconds").getValue(Int::class.java) ?: 0
            val moves = child.child("moves").getValue(Int::class.java) ?: 0
            val date = child.child("date").getValue(String::class.java) ?: ""

            allLeaders.add(LeaderRecord(name, pegs, score, moves, time, date))
        }

        // TÜM ZAMANLAR İÇİN: Her oyuncunun sadece EN İYİ skoru
        val uniqueLeaders = allLeaders
            .groupBy { it.playerName } // Oyuncu adına göre grupla
            .map { (playerName, records) ->
                // Her oyuncunun kayıtları arasından en iyisini seç
                records.minWithOrNull(compareBy<LeaderRecord> { it.remainingPegs }.thenBy { it.gameTimeSeconds })
                    ?: records.first()
            }
            .sortedWith(compareBy<LeaderRecord> { it.remainingPegs }.thenBy { it.gameTimeSeconds })

        // Bugünün oyuncuları (burada tekrar edebilir, günlük aktivite için normal)
        val todayLeaders = allLeaders.filter {
            val recordDate = try {
                val parts = it.date.split(" ")[0] // "27.05" kısmını al
                val year = Calendar.getInstance().get(Calendar.YEAR)
                "$parts.$year"
            } catch (e: Exception) {
                ""
            }
            recordDate == today
        }
            .sortedWith(compareBy<LeaderRecord> { it.remainingPegs }.thenBy { it.gameTimeSeconds })
            .take(5)

        // Dialog container
        val dialogContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
        }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                (resources.displayMetrics.heightPixels * 0.85).toInt()
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setPadding(15, 15, 15, 15)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 20f
            }
        }

        // Ana başlık
        val mainTitle = TextView(this).apply {
            text = "🏆 Liderlik Tabloları"
            textSize = 22f
            setTextColor(Color.parseColor("#FF5722"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        dialogLayout.addView(mainTitle)

        // Scroll view
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // TÜM ZAMANLAR TABLOSU (Her oyuncudan sadece 1 kayıt)
        val allTimeTitle = TextView(this).apply {
            text = "🌟 Tüm Zamanların En İyileri (Oyuncu başına 1 rekor)"
            textSize = 18f
            setTextColor(Color.parseColor("#2E7D32"))
            gravity = android.view.Gravity.CENTER
            setPadding(8, 8, 8, 10)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E8F5E8"))
                cornerRadius = 10f
            }
        }
        contentLayout.addView(allTimeTitle)

        // Benzersiz liderler listesi
        uniqueLeaders.take(10).forEachIndexed { index, record ->
            val minutes = record.gameTimeSeconds / 60
            val seconds = record.gameTimeSeconds % 60
            val timeText = "${minutes}:${seconds.toString().padStart(2, '0')}"

            val medal = when (index) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "${index + 1}."
            }

            val recordView = TextView(this).apply {
                text = "$medal ${record.playerName}\n${record.remainingPegs} taş | ${record.score} puan | $timeText | ${record.moves} hamle"
                textSize = 13f
                setTextColor(Color.parseColor("#424242"))
                setPadding(12, 10, 12, 10)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    val bgColor = when (index) {
                        0 -> Color.parseColor("#FFF8E1") // Altın
                        1 -> Color.parseColor("#F3E5F5") // Gümüş
                        2 -> Color.parseColor("#E8F5E8") // Bronz
                        else -> if (index % 2 == 0) Color.parseColor("#F9F9F9") else Color.WHITE
                    }
                    setColor(bgColor)
                    cornerRadius = 8f
                    if (index < 3) {
                        setStroke(2, when (index) {
                            0 -> Color.parseColor("#FFD700")
                            1 -> Color.parseColor("#C0C0C0")
                            else -> Color.parseColor("#CD7F32")
                        })
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 3, 0, 3)
                }
            }
            contentLayout.addView(recordView)
        }

        // Boşluk
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                20
            )
        }
        contentLayout.addView(spacer)

        // BUGÜNÜN EN İYİLERİ TABLOSU (Burada tekrar edebilir - günlük aktivite)
        val todayTitle = TextView(this).apply {
            text = "🔥 Günün En İyileri (Tüm oyunlar)"
            textSize = 18f
            setTextColor(Color.parseColor("#FF5722"))
            gravity = android.view.Gravity.CENTER
            setPadding(8, 8, 8, 10)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#FFF3E0"))
                cornerRadius = 10f
            }
        }
        contentLayout.addView(todayTitle)

        if (todayLeaders.isEmpty()) {
            val noDataText = TextView(this).apply {
                text = "Bugün henüz kimse oynamamış!\nİlk sen ol! 🎯"
                textSize = 14f
                setTextColor(Color.parseColor("#757575"))
                gravity = android.view.Gravity.CENTER
                setPadding(20, 15, 20, 15)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#F5F5F5"))
                    cornerRadius = 8f
                }
            }
            contentLayout.addView(noDataText)
        } else {
            // Bugünün listesi (tekrar edebilir)
            todayLeaders.forEachIndexed { index, record ->
                val minutes = record.gameTimeSeconds / 60
                val seconds = record.gameTimeSeconds % 60
                val timeText = "${minutes}:${seconds.toString().padStart(2, '0')}"

                val medal = when (index) {
                    0 -> "🔥"
                    1 -> "⚡"
                    2 -> "💫"
                    else -> "${index + 1}."
                }

                val recordView = TextView(this).apply {
                    text = "$medal ${record.playerName}\n${record.remainingPegs} taş | ${record.score} puan | $timeText | ${record.moves} hamle"
                    textSize = 13f
                    setTextColor(Color.parseColor("#424242"))
                    setPadding(12, 10, 12, 10)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        val bgColor = when (index) {
                            0 -> Color.parseColor("#FFEBEE") // Kırmızımsı
                            1 -> Color.parseColor("#FFF3E0") // Turuncumsı
                            2 -> Color.parseColor("#F3E5F5") // Morumsu
                            else -> Color.parseColor("#F0F4FF") // Mavimsi
                        }
                        setColor(bgColor)
                        cornerRadius = 8f
                        setStroke(1, Color.parseColor("#E0E0E0"))
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 3, 0, 3)
                    }
                }
                contentLayout.addView(recordView)
            }
        }

        scrollView.addView(contentLayout)
        dialogLayout.addView(scrollView)

        // Kapat butonu
        val closeButton = Button(this).apply {
            text = "Kapat"
            setOnClickListener {
                val rootView = window.decorView as FrameLayout
                rootView.removeView(dialogContainer)
            }
            background = createButtonBackground(Color.parseColor("#FF5722"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 15, 0, 0)
            }
        }
        dialogLayout.addView(closeButton)

        dialogContainer.addView(dialogLayout)
        val rootView = window.decorView as FrameLayout
        rootView.addView(dialogContainer)
    }

    private fun changePlayerName() {
        val dialogContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
        }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setPadding(30, 30, 30, 30)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 20f
            }
        }

        val titleText = TextView(this).apply {
            text = "👤 Oyuncu Adı"
            textSize = 20f
            setTextColor(Color.parseColor("#607D8B"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        dialogLayout.addView(titleText)

        val currentNameText = TextView(this).apply {
            text = "Şu anki adınız: $playerName"
            textSize = 14f
            setTextColor(Color.parseColor("#757575"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }
        dialogLayout.addView(currentNameText)

        val editText = EditText(this).apply {
            hint = "Yeni adınızı girin"
            textSize = 16f
            setPadding(20, 15, 20, 15)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = 10f
                setStroke(2, Color.parseColor("#E0E0E0"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }
        dialogLayout.addView(editText)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val saveButton = Button(this).apply {
            text = "Kaydet"
            setOnClickListener {
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName.length <= 20) {
                    playerName = newName
                    sharedPreferences.edit().putString("player_name", playerName).apply()
                    Toast.makeText(this@MainActivity, "Adınız '$playerName' olarak kaydedildi!", Toast.LENGTH_SHORT).show()
                    val rootView = window.decorView as FrameLayout
                    rootView.removeView(dialogContainer)
                } else {
                    Toast.makeText(this@MainActivity, "Lütfen 1-20 karakter arası bir ad girin!", Toast.LENGTH_SHORT).show()
                }
            }
            background = createButtonBackground(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(10, 0, 10, 0) }
        }

        val cancelButton = Button(this).apply {
            text = "İptal"
            setOnClickListener {
                val rootView = window.decorView as FrameLayout
                rootView.removeView(dialogContainer)
            }
            background = createButtonBackground(Color.parseColor("#757575"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(10, 0, 10, 0) }
        }

        buttonLayout.addView(saveButton)
        buttonLayout.addView(cancelButton)
        dialogLayout.addView(buttonLayout)

        dialogContainer.addView(dialogLayout)
        val rootView = window.decorView as FrameLayout
        rootView.addView(dialogContainer)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ses kaynaklarını temizle
        perfectScorePlayer?.release()
        zeroScorePlayer?.release()

        // Zamanlayıcıyı temizle
        stopGameTimer()
    }
}