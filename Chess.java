import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/** Two-player chess with Swing. Supports check, checkmate, stalemate, castling, auto-queen promotion. */
public class Chess extends JPanel {
    static final int S = 80; // square size
    char[][] board = new char[8][8];
    boolean[][] touched = new boolean[8][8]; // squares ever moved from/to (for castling rights)
    boolean whiteTurn = true, gameOver = false;
    int selR = -1, selC = -1;
    List<int[]> targets = new ArrayList<>();
    String status = "White to move";

    Chess() {
        setPreferredSize(new Dimension(8 * S, 8 * S + 40));
        reset();
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { click(e.getY() / S, e.getX() / S); }
        });
    }

    void reset() {
        String back = "rnbqkbnr";
        for (int r = 0; r < 8; r++) Arrays.fill(board[r], '.');
        for (int c = 0; c < 8; c++) {
            board[0][c] = back.charAt(c);
            board[1][c] = 'p';
            board[6][c] = 'P';
            board[7][c] = Character.toUpperCase(back.charAt(c));
        }
        touched = new boolean[8][8];
        whiteTurn = true; gameOver = false; selR = selC = -1;
        targets.clear();
        status = "White to move (click board after game ends to restart)";
        repaint();
    }

    static boolean white(char p) { return Character.isUpperCase(p); }
    static boolean in(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    // ---- move generation ----
    List<int[]> pseudo(char[][] b, int r, int c, boolean castling) {
        List<int[]> m = new ArrayList<>();
        char p = b[r][c];
        boolean w = white(p);
        char t = Character.toLowerCase(p);
        switch (t) {
            case 'p': {
                int d = w ? -1 : 1, start = w ? 6 : 1;
                if (in(r + d, c) && b[r + d][c] == '.') {
                    m.add(new int[]{r + d, c});
                    if (r == start && b[r + 2 * d][c] == '.') m.add(new int[]{r + 2 * d, c});
                }
                for (int dc = -1; dc <= 1; dc += 2)
                    if (in(r + d, c + dc) && b[r + d][c + dc] != '.' && white(b[r + d][c + dc]) != w)
                        m.add(new int[]{r + d, c + dc});
                break;
            }
            case 'n':
                for (int[] o : new int[][]{{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}})
                    add(b, m, r + o[0], c + o[1], w);
                break;
            case 'k':
                for (int dr = -1; dr <= 1; dr++)
                    for (int dc = -1; dc <= 1; dc++)
                        if (dr != 0 || dc != 0) add(b, m, r + dr, c + dc, w);
                if (castling && !touched[r][4] && !attacked(b, r, 4, !w)) {
                    char rook = w ? 'R' : 'r';
                    if (b[r][7] == rook && !touched[r][7] && b[r][5] == '.' && b[r][6] == '.'
                            && !attacked(b, r, 5, !w)) m.add(new int[]{r, 6});
                    if (b[r][0] == rook && !touched[r][0] && b[r][1] == '.' && b[r][2] == '.' && b[r][3] == '.'
                            && !attacked(b, r, 3, !w)) m.add(new int[]{r, 2});
                }
                break;
            default: {
                int[][] dirs = t == 'r' ? new int[][]{{1,0},{-1,0},{0,1},{0,-1}}
                        : t == 'b' ? new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}}
                        : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
                for (int[] d : dirs) {
                    int rr = r + d[0], cc = c + d[1];
                    while (in(rr, cc)) {
                        if (b[rr][cc] == '.') m.add(new int[]{rr, cc});
                        else { if (white(b[rr][cc]) != w) m.add(new int[]{rr, cc}); break; }
                        rr += d[0]; cc += d[1];
                    }
                }
            }
        }
        return m;
    }

    void add(char[][] b, List<int[]> m, int r, int c, boolean w) {
        if (in(r, c) && (b[r][c] == '.' || white(b[r][c]) != w)) m.add(new int[]{r, c});
    }

    /** Is square (r,c) attacked by the given colour? */
    boolean attacked(char[][] b, int r, int c, boolean byWhite) {
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++) {
                char p = b[i][j];
                if (p == '.' || white(p) != byWhite) continue;
                if (Character.toLowerCase(p) == 'p') {
                    int d = byWhite ? -1 : 1;
                    if (i + d == r && Math.abs(j - c) == 1) return true;
                } else for (int[] mv : pseudo(b, i, j, false))
                    if (mv[0] == r && mv[1] == c) return true;
            }
        return false;
    }

    boolean inCheck(char[][] b, boolean w) {
        char k = w ? 'K' : 'k';
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                if (b[i][j] == k) return attacked(b, i, j, !w);
        return false;
    }

    void apply(char[][] b, int fr, int fc, int tr, int tc) {
        char p = b[fr][fc];
        b[tr][tc] = p; b[fr][fc] = '.';
        if (Character.toLowerCase(p) == 'k' && Math.abs(tc - fc) == 2) { // castling: move rook
            if (tc == 6) { b[tr][5] = b[tr][7]; b[tr][7] = '.'; }
            else { b[tr][3] = b[tr][0]; b[tr][0] = '.'; }
        }
        if (p == 'P' && tr == 0) b[tr][tc] = 'Q';
        if (p == 'p' && tr == 7) b[tr][tc] = 'q';
    }

    List<int[]> legal(int r, int c) {
        List<int[]> out = new ArrayList<>();
        boolean w = white(board[r][c]);
        for (int[] mv : pseudo(board, r, c, true)) {
            char[][] copy = new char[8][];
            for (int i = 0; i < 8; i++) copy[i] = board[i].clone();
            apply(copy, r, c, mv[0], mv[1]);
            if (!inCheck(copy, w)) out.add(mv);
        }
        return out;
    }

    boolean hasMoves(boolean w) {
        for (int i = 0; i < 8; i++)
            for (int j = 0; j < 8; j++)
                if (board[i][j] != '.' && white(board[i][j]) == w && !legal(i, j).isEmpty()) return true;
        return false;
    }

    // ---- input ----
    void click(int r, int c) {
        if (!in(r, c)) return;
        if (gameOver) { reset(); return; }
        for (int[] t : targets)
            if (t[0] == r && t[1] == c) {
                apply(board, selR, selC, r, c);
                touched[selR][selC] = touched[r][c] = true;
                selR = selC = -1; targets.clear();
                whiteTurn = !whiteTurn;
                String side = whiteTurn ? "White" : "Black";
                boolean chk = inCheck(board, whiteTurn);
                if (!hasMoves(whiteTurn)) {
                    gameOver = true;
                    status = chk ? "Checkmate! " + (whiteTurn ? "Black" : "White") + " wins. Click to restart."
                                 : "Stalemate - draw. Click to restart.";
                } else status = side + " to move" + (chk ? " (check!)" : "");
                repaint();
                return;
            }
        if (board[r][c] != '.' && white(board[r][c]) == whiteTurn) {
            selR = r; selC = c; targets = legal(r, c);
        } else { selR = selC = -1; targets.clear(); }
        repaint();
    }

    // ---- drawing ----
    static final String W = "♔♕♖♗♘♙", B = "♚♛♜♝♞♟", ORDER = "kqrbnp";

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font pieceFont = new Font("Dialog", Font.PLAIN, 58);
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                g.setColor((r + c) % 2 == 0 ? new Color(240, 217, 181) : new Color(181, 136, 99));
                g.fillRect(c * S, r * S, S, S);
                if (r == selR && c == selC) { g.setColor(new Color(255, 255, 0, 120)); g.fillRect(c * S, r * S, S, S); }
                char p = board[r][c];
                if (p != '.') {
                    String glyph = String.valueOf(B.charAt(ORDER.indexOf(Character.toLowerCase(p))));
                    g.setFont(pieceFont);
                    FontMetrics fm = g.getFontMetrics();
                    int x = c * S + (S - fm.stringWidth(glyph)) / 2, y = r * S + (S + fm.getAscent() - fm.getDescent()) / 2;
                    if (white(p)) { g.setColor(Color.BLACK); g.drawString(glyph, x + 2, y + 2); g.setColor(Color.WHITE); }
                    else g.setColor(Color.BLACK);
                    g.drawString(glyph, x, y);
                }
            }
        g.setColor(new Color(0, 160, 0, 150));
        for (int[] t : targets) {
            if (board[t[0]][t[1]] == '.') g.fillOval(t[1] * S + S / 2 - 10, t[0] * S + S / 2 - 10, 20, 20);
            else { g.setStroke(new BasicStroke(5)); g.drawOval(t[1] * S + 6, t[0] * S + 6, S - 12, S - 12); }
        }
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 8 * S, 8 * S, 40);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(status, 12, 8 * S + 26);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Chess");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new Chess());
            f.pack();
            f.setResizable(false);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }
}
