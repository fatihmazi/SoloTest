package com.stokmatik.solotest
import kotlin.math.abs

class GameEngine {
    companion object {
        const val BOARD_SIZE = 7
        const val TOTAL_CELLS = BOARD_SIZE * BOARD_SIZE
        const val CENTER_POSITION = 24 // 3,3 pozisyonu
    }

    private var board = Array(BOARD_SIZE) { Array(BOARD_SIZE) { CellType.INVALID } }
    private var selectedPosition: Int? = null
    private var moveHistory = mutableListOf<Move>()
    private var moveCount = 0

    init {
        initializeBoard()
    }

    private fun initializeBoard() {
        // Tahta yapısını oluştur
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                board[row][col] = when {
                    // Üst ve alt köşeler
                    (row < 2 || row > 4) && (col < 2 || col > 4) -> CellType.INVALID
                    // Merkez boş
                    row == 3 && col == 3 -> CellType.EMPTY
                    // Diğer geçerli alanlar taş ile dolu
                    else -> CellType.PEG
                }
            }
        }
        moveCount = 0
        moveHistory.clear()
        selectedPosition = null
    }

    fun handleCellClick(position: Int): ClickResult {
        val (row, col) = positionToRowCol(position)

        if (!isValidPosition(row, col)) {
            return ClickResult(ClickResult.Type.INVALID_MOVE)
        }

        val cellType = board[row][col]

        return when {
            // Taş seçme
            cellType == CellType.PEG && selectedPosition != position -> {
                selectedPosition = position
                val validMoves = getValidMovesFor(row, col)
                ClickResult(ClickResult.Type.PEG_SELECTED, validMoves = validMoves)
            }

            // Aynı taşa tekrar tıklama (seçimi kaldır)
            selectedPosition == position -> {
                selectedPosition = null
                ClickResult(ClickResult.Type.DESELECTED)
            }

            // Hamle yapma
            cellType == CellType.EMPTY && selectedPosition != null -> {
                val selectedPos = selectedPosition!!
                val (selectedRow, selectedCol) = positionToRowCol(selectedPos)

                if (isValidMove(selectedRow, selectedCol, row, col)) {
                    makeMove(selectedRow, selectedCol, row, col)
                    selectedPosition = null
                    ClickResult(ClickResult.Type.MOVE_MADE)
                } else {
                    ClickResult(ClickResult.Type.INVALID_MOVE)
                }
            }

            else -> ClickResult(ClickResult.Type.INVALID_MOVE)
        }
    }

    private fun getValidMovesFor(row: Int, col: Int): List<Int> {
        val validMoves = mutableListOf<Int>()
        val directions = listOf(
            Pair(-2, 0), // Yukarı
            Pair(2, 0),  // Aşağı
            Pair(0, -2), // Sol
            Pair(0, 2)   // Sağ
        )

        for ((dRow, dCol) in directions) {
            val newRow = row + dRow
            val newCol = col + dCol

            if (isValidMove(row, col, newRow, newCol)) {
                validMoves.add(rowColToPosition(newRow, newCol))
            }
        }

        return validMoves
    }

    private fun isValidMove(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        // Hedef pozisyon geçerli mi?
        if (!isValidPosition(toRow, toCol) || board[toRow][toCol] != CellType.EMPTY) {
            return false
        }

        // Kaynak pozisyonda taş var mı?
        if (board[fromRow][fromCol] != CellType.PEG) {
            return false
        }

        // İki birim mesafe mi?
        val rowDiff = abs(toRow - fromRow)
        val colDiff = abs(toCol - fromCol)

        if (!((rowDiff == 2 && colDiff == 0) || (rowDiff == 0 && colDiff == 2))) {
            return false
        }

        // Aradaki pozisyonda taş var mı?
        val middleRow = (fromRow + toRow) / 2
        val middleCol = (fromCol + toCol) / 2

        return board[middleRow][middleCol] == CellType.PEG
    }

    private fun makeMove(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        val middleRow = (fromRow + toRow) / 2
        val middleCol = (fromCol + toCol) / 2

        // Hamleyi kaydet (geri alma için)
        val move = Move(
            from = rowColToPosition(fromRow, fromCol),
            to = rowColToPosition(toRow, toCol),
            captured = rowColToPosition(middleRow, middleCol)
        )
        moveHistory.add(move)

        // Hamleyi yap
        board[fromRow][fromCol] = CellType.EMPTY
        board[toRow][toCol] = CellType.PEG
        board[middleRow][middleCol] = CellType.EMPTY

        moveCount++
    }

    fun undoMove(): Boolean {
        if (moveHistory.isEmpty()) return false

        val lastMove = moveHistory.removeLastOrNull() ?: return false
        val (fromRow, fromCol) = positionToRowCol(lastMove.from)
        val (toRow, toCol) = positionToRowCol(lastMove.to)
        val (capturedRow, capturedCol) = positionToRowCol(lastMove.captured)

        // Hamleyi geri al
        board[fromRow][fromCol] = CellType.PEG
        board[toRow][toCol] = CellType.EMPTY
        board[capturedRow][capturedCol] = CellType.PEG

        moveCount--
        selectedPosition = null

        return true
    }

    fun getHint(): Move? {
        // Basit ipucu algoritması: ilk geçerli hamleyi bul
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                if (board[row][col] == CellType.PEG) {
                    val validMoves = getValidMovesFor(row, col)
                    if (validMoves.isNotEmpty()) {
                        return Move(
                            from = rowColToPosition(row, col),
                            to = validMoves[0],
                            captured = -1 // İpucu için gerekli değil
                        )
                    }
                }
            }
        }
        return null
    }

    fun getGameState(): GameState {
        val remainingPegs = getRemainingPegsCount()

        return when {
            remainingPegs == 1 -> {
                if (board[3][3] == CellType.PEG) GameState.PERFECT_WIN else GameState.WIN
            }
            hasValidMoves() -> GameState.IN_PROGRESS
            else -> GameState.GAME_OVER
        }
    }

    private fun hasValidMoves(): Boolean {
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                if (board[row][col] == CellType.PEG) {
                    if (getValidMovesFor(row, col).isNotEmpty()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    fun getRemainingPegsCount(): Int {
        var count = 0
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                if (board[row][col] == CellType.PEG) {
                    count++
                }
            }
        }
        return count
    }

    fun getBoard(): Array<Array<CellType>> = board
    fun getMoveCount(): Int = moveCount
    fun canUndo(): Boolean = moveHistory.isNotEmpty()

    fun resetGame() {
        initializeBoard()
    }

    // Yardımcı fonksiyonlar
    private fun isValidPosition(row: Int, col: Int): Boolean {
        return row in 0 until BOARD_SIZE &&
                col in 0 until BOARD_SIZE &&
                board[row][col] != CellType.INVALID
    }

    private fun positionToRowCol(position: Int): Pair<Int, Int> {
        return Pair(position / BOARD_SIZE, position % BOARD_SIZE)
    }

    private fun rowColToPosition(row: Int, col: Int): Int {
        return row * BOARD_SIZE + col
    }
}

enum class CellType {
    INVALID,  // Tahta dışı
    EMPTY,    // Boş
    PEG       // Taş
}

enum class GameState {
    IN_PROGRESS,
    WIN,
    PERFECT_WIN,
    GAME_OVER
}

data class Move(
    val from: Int,
    val to: Int,
    val captured: Int
)

data class ClickResult(
    val type: Type,
    val validMoves: List<Int>? = null
) {
    enum class Type {
        PEG_SELECTED,
        MOVE_MADE,
        INVALID_MOVE,
        DESELECTED
    }
}