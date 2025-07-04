import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;

public class Sudoku {
    // Fixed difficulty - medium level
    private static final int CELLS_TO_REMOVE = 50;
    
    class Tile extends JButton {
        int r, c;
        boolean isOriginal = false;
        boolean hasNote = false;
        Set<Integer> notes = new HashSet<>();
        
        Tile(int r, int c) {
            this.r = r;
            this.c = c;
            setFocusable(false);
            setFont(new Font("Arial", Font.BOLD, 18));
            setBackground(Color.WHITE);
            setBorder(createTileBorder());
            
            // Right-click for notes
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e) && !isOriginal && getText().isEmpty()) {
                        toggleNoteMode();
                    }
                }
            });
        }
        
        private Border createTileBorder() {
            int top = (r % 3 == 0) ? 3 : 1;
            int left = (c % 3 == 0) ? 3 : 1;
            int bottom = (r % 3 == 2) ? 3 : 1;
            int right = (c % 3 == 2) ? 3 : 1;
            return BorderFactory.createMatteBorder(top, left, bottom, right, Color.BLACK);
        }
        
        void updateNoteDisplay() {
            if (hasNote && !notes.isEmpty()) {
                StringBuilder sb = new StringBuilder("<html><div style='text-align: center; font-size: 8px;'>");
                List<Integer> sortedNotes = new ArrayList<>(notes);
                Collections.sort(sortedNotes);
                for (int i = 0; i < 9; i++) {
                    if (i % 3 == 0 && i > 0) sb.append("<br>");
                    if (sortedNotes.contains(i + 1)) {
                        sb.append(i + 1);
                    } else {
                        sb.append("&nbsp;");
                    }
                    if (i % 3 < 2) sb.append("&nbsp;");
                }
                sb.append("</div></html>");
                setText(sb.toString());
                setFont(new Font("Arial", Font.PLAIN, 8));
            }
        }
        
        void clearNotes() {
            notes.clear();
            hasNote = false;
            setFont(new Font("Arial", Font.BOLD, 18));
        }
        
        void highlightConflicts() {
            if (!getText().isEmpty() && !hasNote) {
                String value = getText();
                boolean hasConflict = false;
                
                // Check row conflicts
                for (int col = 0; col < 9; col++) {
                    if (col != c && tiles[r][col].getText().equals(value) && !tiles[r][col].hasNote) {
                        hasConflict = true;
                        break;
                    }
                }
                
                // Check column conflicts
                if (!hasConflict) {
                    for (int row = 0; row < 9; row++) {
                        if (row != r && tiles[row][c].getText().equals(value) && !tiles[row][c].hasNote) {
                            hasConflict = true;
                            break;
                        }
                    }
                }
                
                // Check 3x3 box conflicts
                if (!hasConflict) {
                    int boxR = (r / 3) * 3;
                    int boxC = (c / 3) * 3;
                    for (int row = boxR; row < boxR + 3; row++) {
                        for (int col = boxC; col < boxC + 3; col++) {
                            if ((row != r || col != c) && tiles[row][col].getText().equals(value) && !tiles[row][col].hasNote) {
                                hasConflict = true;
                                break;
                            }
                        }
                        if (hasConflict) break;
                    }
                }
                
                setBackground(hasConflict ? new Color(255, 200, 200) : Color.WHITE);
            } else {
                setBackground(Color.WHITE);
            }
        }
    }

    // Game state
    private int boardWidth = 600;
    private int boardHeight = 700;
    private Tile[][] tiles = new Tile[9][9];
    private int[][] currentPuzzle = new int[9][9];
    private int[][] solution = new int[9][9];
    private JButton numSelected = null;
    private int errors = 0;
    private int hintsUsed = 0;
    private boolean noteMode = false;
    private long startTime;
    private Timer gameTimer;
    private int elapsedSeconds = 0;
    
    // UI Components
    private JFrame frame = new JFrame("🧩 Sudoku");
    private JLabel statusLabel = new JLabel();
    private JLabel timerLabel = new JLabel();
    private JPanel boardPanel = new JPanel();
    private JPanel numbersPanel = new JPanel();
    private JPanel controlsPanel = new JPanel();
    private JButton hintButton, undoButton, resetButton, newGameButton, noteModeButton;
    private Stack<GameState> undoStack = new Stack<>();
    
    // Game state for undo functionality
    class GameState {
        int[][] puzzleState = new int[9][9];
        int errorsCount;
        int hintsCount;
        
        GameState(int[][] puzzle, int errors, int hints) {
            for (int i = 0; i < 9; i++) {
                System.arraycopy(puzzle[i], 0, puzzleState[i], 0, 9);
            }
            this.errorsCount = errors;
            this.hintsCount = hints;
        }
    }

    public Sudoku() {
        initializeUI();
        generateNewPuzzle();
        startTimer();
    }
    
    private void initializeUI() {
        // Main frame setup
        frame.setSize(boardWidth, boardHeight);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        
        // Top panel with status and timer
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JPanel statusPanel = new JPanel(new FlowLayout());
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusLabel.setText("Errors: 0");
        timerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        timerLabel.setText("Time: 00:00");
        
        statusPanel.add(statusLabel);
        statusPanel.add(Box.createHorizontalStrut(20));
        statusPanel.add(timerLabel);
        
        topPanel.add(statusPanel, BorderLayout.CENTER);
        frame.add(topPanel, BorderLayout.NORTH);
        
        // Game board
        boardPanel.setLayout(new GridLayout(9, 9, 1, 1));
        boardPanel.setBackground(Color.BLACK);
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setupBoard();
        frame.add(boardPanel, BorderLayout.CENTER);
        
        // Bottom panel with controls and numbers
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        // Controls
        setupControls();
        bottomPanel.add(controlsPanel, BorderLayout.NORTH);
        
        // Number buttons
        setupNumberButtons();
        bottomPanel.add(numbersPanel, BorderLayout.CENTER);
        
        frame.add(bottomPanel, BorderLayout.SOUTH);
        
        frame.setVisible(true);
    }
    
    private void setupBoard() {
        boardPanel.removeAll();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                Tile tile = new Tile(r, c);
                tiles[r][c] = tile;
                
                tile.addActionListener(e -> {
                    Tile clickedTile = (Tile) e.getSource();
                    handleTileClick(clickedTile);
                });
                
                boardPanel.add(tile);
            }
        }
    }
    
    private void setupControls() {
        controlsPanel.removeAll();
        controlsPanel.setLayout(new FlowLayout());
        
        newGameButton = new JButton("New Game");
        newGameButton.addActionListener(e -> generateNewPuzzle());
        
        hintButton = new JButton("Hint");
        hintButton.addActionListener(e -> provideHint());
        
        undoButton = new JButton("Undo");
        undoButton.addActionListener(e -> undoMove());
        
        resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> resetPuzzle());
        
        noteModeButton = new JButton("Note Mode: OFF");
        noteModeButton.addActionListener(e -> toggleNoteMode());
        
        controlsPanel.add(newGameButton);
        controlsPanel.add(hintButton);
        controlsPanel.add(undoButton);
        controlsPanel.add(resetButton);
        controlsPanel.add(noteModeButton);
    }
    
    private void setupNumberButtons() {
        numbersPanel.removeAll();
        numbersPanel.setLayout(new GridLayout(1, 10));
        
        // Clear button
        JButton clearButton = new JButton("Clear");
        clearButton.setFont(new Font("Arial", Font.BOLD, 16));
        clearButton.addActionListener(e -> {
            if (numSelected != null) {
                numSelected.setBackground(Color.WHITE);
            }
            numSelected = clearButton;
            clearButton.setBackground(Color.LIGHT_GRAY);
        });
        numbersPanel.add(clearButton);
        
        // Number buttons 1-9
        for (int i = 1; i <= 9; i++) {
            JButton button = new JButton(String.valueOf(i));
            button.setFont(new Font("Arial", Font.BOLD, 16));
            button.setBackground(Color.WHITE);
            button.addActionListener(e -> {
                if (numSelected != null) {
                    numSelected.setBackground(Color.WHITE);
                }
                numSelected = button;
                button.setBackground(Color.LIGHT_GRAY);
            });
            numbersPanel.add(button);
        }
    }
    
    private void handleTileClick(Tile tile) {
        if (tile.isOriginal || numSelected == null) return;
        
        saveGameState();
        
        String selectedValue = numSelected.getText();
        
        if (selectedValue.equals("Clear")) {
            tile.setText("");
            tile.clearNotes();
            tile.setBackground(Color.WHITE);
            updateAllHighlights();
            return;
        }
        
        if (noteMode && !tile.hasNote) {
            // Toggle note
            int num = Integer.parseInt(selectedValue);
            if (tile.notes.contains(num)) {
                tile.notes.remove(num);
            } else {
                tile.notes.add(num);
            }
            
            if (tile.notes.isEmpty()) {
                tile.setText("");
                tile.hasNote = false;
                tile.setFont(new Font("Arial", Font.BOLD, 18));
            } else {
                tile.hasNote = true;
                tile.updateNoteDisplay();
            }
        } else {
            // Place number
            tile.clearNotes();
            tile.setText(selectedValue);
            
            // Check if correct
            int value = Integer.parseInt(selectedValue);
            if (solution[tile.r][tile.c] != value) {
                errors++;
                statusLabel.setText("Errors: " + errors);
                tile.setBackground(new Color(255, 200, 200));
            } else {
                currentPuzzle[tile.r][tile.c] = value;
                tile.setBackground(Color.WHITE);
            }
            
            updateAllHighlights();
            checkWinCondition();
        }
    }
    
    private void updateAllHighlights() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                tiles[r][c].highlightConflicts();
            }
        }
    }
    
    private void saveGameState() {
        undoStack.push(new GameState(currentPuzzle, errors, hintsUsed));
        if (undoStack.size() > 50) { // Limit undo history
            undoStack.removeElementAt(0);
        }
    }
    
    private void undoMove() {
        if (!undoStack.isEmpty()) {
            GameState lastState = undoStack.pop();
            
            // Restore puzzle state
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    currentPuzzle[r][c] = lastState.puzzleState[r][c];
                    if (!tiles[r][c].isOriginal) {
                        if (currentPuzzle[r][c] == 0) {
                            tiles[r][c].setText("");
                            tiles[r][c].clearNotes();
                        } else {
                            tiles[r][c].setText(String.valueOf(currentPuzzle[r][c]));
                            tiles[r][c].clearNotes();
                        }
                    }
                }
            }
            
            errors = lastState.errorsCount;
            hintsUsed = lastState.hintsCount;
            statusLabel.setText("Errors: " + errors);
            updateAllHighlights();
        }
    }
    
    private void provideHint() {
        // Find empty cells
        List<Point> emptyCells = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (currentPuzzle[r][c] == 0 && !tiles[r][c].isOriginal) {
                    emptyCells.add(new Point(r, c));
                }
            }
        }
        
        if (!emptyCells.isEmpty()) {
            saveGameState();
            Point hint = emptyCells.get(new Random().nextInt(emptyCells.size()));
            int r = hint.x, c = hint.y;
            
            tiles[r][c].clearNotes();
            tiles[r][c].setText(String.valueOf(solution[r][c]));
            tiles[r][c].setBackground(new Color(200, 255, 200)); // Green hint color
            currentPuzzle[r][c] = solution[r][c];
            
            hintsUsed++;
            updateAllHighlights();
            checkWinCondition();
        }
    }
    
    private void toggleNoteMode() {
        noteMode = !noteMode;
        noteModeButton.setText("Note Mode: " + (noteMode ? "ON" : "OFF"));
        noteModeButton.setBackground(noteMode ? Color.YELLOW : null);
    }
    
    private void resetPuzzle() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (!tiles[r][c].isOriginal) {
                    tiles[r][c].setText("");
                    tiles[r][c].clearNotes();
                    tiles[r][c].setBackground(Color.WHITE);
                    currentPuzzle[r][c] = 0;
                }
            }
        }
        errors = 0;
        hintsUsed = 0;
        statusLabel.setText("Errors: 0");
        undoStack.clear();
        elapsedSeconds = 0;
        startTimer();
    }
    
    private void generateNewPuzzle() {
        // Generate a complete solved puzzle
        generateSolution();
        
        // Create a copy for the puzzle
        for (int r = 0; r < 9; r++) {
            System.arraycopy(solution[r], 0, currentPuzzle[r], 0, 9);
        }
        
        // Remove cells based on difficulty
        removeCells(CELLS_TO_REMOVE);
        
        // Update the board
        updateBoardDisplay();
        
        // Reset game state
        errors = 0;
        hintsUsed = 0;
        statusLabel.setText("Errors: 0");
        undoStack.clear();
        elapsedSeconds = 0;
        startTimer();
    }
    
    private void generateSolution() {
        // Clear the solution grid
        for (int r = 0; r < 9; r++) {
            Arrays.fill(solution[r], 0);
        }
        
        // Fill the grid using backtracking
        solvePuzzle(solution);
    }
    
    private boolean solvePuzzle(int[][] grid) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] == 0) {
                    List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9);
                    Collections.shuffle(numbers);
                    
                    for (int num : numbers) {
                        if (isValidMove(grid, r, c, num)) {
                            grid[r][c] = num;
                            if (solvePuzzle(grid)) {
                                return true;
                            }
                            grid[r][c] = 0;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }
    
    private boolean isValidMove(int[][] grid, int row, int col, int num) {
        // Check row
        for (int c = 0; c < 9; c++) {
            if (grid[row][c] == num) return false;
        }
        
        // Check column
        for (int r = 0; r < 9; r++) {
            if (grid[r][col] == num) return false;
        }
        
        // Check 3x3 box
        int boxRow = (row / 3) * 3;
        int boxCol = (col / 3) * 3;
        for (int r = boxRow; r < boxRow + 3; r++) {
            for (int c = boxCol; c < boxCol + 3; c++) {
                if (grid[r][c] == num) return false;
            }
        }
        
        return true;
    }
    
    private void removeCells(int cellsToRemove) {
        Random random = new Random();
        int removed = 0;
        
        while (removed < cellsToRemove) {
            int r = random.nextInt(9);
            int c = random.nextInt(9);
            
            if (currentPuzzle[r][c] != 0) {
                currentPuzzle[r][c] = 0;
                removed++;
            }
        }
    }
    
    private void updateBoardDisplay() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                Tile tile = tiles[r][c];
                
                if (currentPuzzle[r][c] != 0) {
                    tile.setText(String.valueOf(currentPuzzle[r][c]));
                    tile.setBackground(new Color(240, 240, 240));
                    tile.isOriginal = true;
                    tile.setFont(new Font("Arial", Font.BOLD, 18));
                } else {
                    tile.setText("");
                    tile.setBackground(Color.WHITE);
                    tile.isOriginal = false;
                    tile.clearNotes();
                }
            }
        }
    }
    
    private void checkWinCondition() {
        boolean isComplete = true;
        
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (currentPuzzle[r][c] == 0 || 
                    !tiles[r][c].getText().equals(String.valueOf(solution[r][c]))) {
                    isComplete = false;
                    break;
                }
            }
            if (!isComplete) break;
        }
        
        if (isComplete) {
            gameTimer.stop();
            String timeStr = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
            String message = String.format(
                "🎉 Congratulations! 🎉\n\n" +
                "Puzzle completed!\n" +
                "Time: %s\n" +
                "Errors: %d\n" +
                "Hints used: %d\n\n" +
                "Would you like to play again?",
                timeStr, errors, hintsUsed
            );
            
            int choice = JOptionPane.showConfirmDialog(
                frame, message, "Puzzle Complete!", 
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE
            );
            
            if (choice == JOptionPane.YES_OPTION) {
                generateNewPuzzle();
            }
        }
    }
    
    private void startTimer() {
        if (gameTimer != null) {
            gameTimer.stop();
        }
        
        startTime = System.currentTimeMillis();
        gameTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            String timeStr = String.format("Time: %02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
            timerLabel.setText(timeStr);
        });
        gameTimer.start();
    }
}