package com.stokmatik.solotest

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class GameBoardAdapter(
    private var board: Array<Array<CellType>>,
    private val onCellClick: (Int) -> Unit
) : RecyclerView.Adapter<GameBoardAdapter.CellViewHolder>() {

    private var selectedPosition: Int? = null
    private var validMoves: List<Int> = emptyList()
    private var hintMove: Move? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_board_cell, parent, false)
        return CellViewHolder(view)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = GameEngine.TOTAL_CELLS

    fun updateBoard(newBoard: Array<Array<CellType>>) {
        board = newBoard
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        val oldSelected = selectedPosition
        selectedPosition = position

        oldSelected?.let { notifyItemChanged(it) }
        notifyItemChanged(position)
    }

    fun setValidMoves(moves: List<Int>) {
        val oldMoves = validMoves.toList()
        validMoves = moves

        // Eski geçerli hamleleri güncelle
        oldMoves.forEach { notifyItemChanged(it) }
        // Yeni geçerli hamleleri güncelle
        validMoves.forEach { notifyItemChanged(it) }
    }

    fun clearSelection() {
        val oldSelected = selectedPosition
        val oldMoves = validMoves.toList()

        selectedPosition = null
        validMoves = emptyList()
        hintMove = null

        oldSelected?.let { notifyItemChanged(it) }
        oldMoves.forEach { notifyItemChanged(it) }
    }

    fun highlightHint(hint: Move) {
        hintMove = hint
        notifyItemChanged(hint.from)
        notifyItemChanged(hint.to)

        // 3 saniye sonra ipucunu temizle
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            hintMove = null
            notifyItemChanged(hint.from)
            notifyItemChanged(hint.to)
        }, 3000)
    }

    inner class CellViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cellView: View = itemView.findViewById(R.id.cellView)

        fun bind(position: Int) {
            val row = position / GameEngine.BOARD_SIZE
            val col = position % GameEngine.BOARD_SIZE
            val cellType = board[row][col]

            // Tıklama olayını ayarla
            itemView.setOnClickListener {
                if (cellType != CellType.INVALID) {
                    onCellClick(position)
                }
            }

            // Hücre görünümünü ayarla
            when (cellType) {
                CellType.INVALID -> {
                    cellView.visibility = View.INVISIBLE
                }
                CellType.EMPTY -> {
                    cellView.visibility = View.VISIBLE
                    setupEmptyCell(position)
                }
                CellType.PEG -> {
                    cellView.visibility = View.VISIBLE
                    setupPegCell(position)
                }
            }
        }

        private fun setupEmptyCell(position: Int) {
            val context = itemView.context

            when {
                // İpucu hedef konumu
                hintMove?.to == position -> {
                    cellView.setBackgroundResource(R.drawable.cell_hint_target)
                }
                // Geçerli hamle konumu
                validMoves.contains(position) -> {
                    cellView.setBackgroundResource(R.drawable.cell_valid_move)
                }
                // Normal boş hücre
                else -> {
                    cellView.setBackgroundResource(R.drawable.cell_empty)
                }
            }
        }

        private fun setupPegCell(position: Int) {
            val context = itemView.context

            when {
                // İpucu kaynak konumu
                hintMove?.from == position -> {
                    cellView.setBackgroundResource(R.drawable.cell_hint_source)
                }
                // Seçili taş
                selectedPosition == position -> {
                    cellView.setBackgroundResource(R.drawable.cell_selected)
                }
                // Normal taş
                else -> {
                    cellView.setBackgroundResource(R.drawable.cell_peg)
                }
            }
        }
    }
}