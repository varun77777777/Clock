import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

/**
 * ElegantMinimalClockDark
 * Single-file Swing application - dark theme, top navigation, time section,
 * Alarm / Timer / Stopwatch panels, WAV audio playback via AudioSystem,
 * and an Audio selection panel to choose custom files for each sound.
 */
public class ElegantMinimalClockDark extends JFrame {
    // Time formatters
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm:ss a");
    private final DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEE");
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd");
    private final DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM");

    // Time UI
    private final JLabel bigTimeLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel weekdayLabel = new JLabel("", SwingConstants.LEFT);
    private final JLabel dayLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel dateLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);

    // Alarm
    private final JTextField alarmField = new JTextField();
    private Timer alarmChecker;

    // Timer
    private final JTextField timerField = new JTextField();
    private final JProgressBar timerProgress = new JProgressBar();
    private final JLabel timerRemaining = new JLabel("00:00", SwingConstants.CENTER);
    private Timer countdownTimer;
    private int countdownSeconds;

    // Stopwatch
    private final JLabel stopwatchLabel = new JLabel("00:00.00", SwingConstants.CENTER);
    private long stopwatchStart = 0;
    private boolean stopwatchRunning = false;
    private Timer stopwatchTimer;
    private final DefaultListModel<String> lapModel = new DefaultListModel<>();

    // Navigation buttons (top)
    private final JButton navAlarm = new JButton();
    private final JButton navTimer = new JButton();
    private final JButton navStopwatch = new JButton();
    private final JButton navAudio = new JButton();

    // Card layout container
    private final JPanel cardsPanel = new JPanel(new CardLayout());

    // Keep active Clips so they are not GC'd immediately
    private final List<Clip> activeClips = Collections.synchronizedList(new ArrayList<>());

    // Audio selection fields (defaults)
    private String alarmSoundPath = "sounds/alarm.wav";
    private String timerSoundPath = "sounds/timer_finish.wav";
    private String stopwatchClickPath = "sounds/click.wav";
    private String stopwatchResetPath = "sounds/reset.wav";

    // Preferences keys
    private static final String PREF_ALARM = "alarmSound";
    private static final String PREF_TIMER = "timerSound";
    private static final String PREF_STOPWATCH_CLICK = "stopwatchClick";
    private static final String PREF_STOPWATCH_RESET = "stopwatchReset";
    private final Preferences prefs = Preferences.userNodeForPackage(ElegantMinimalClockDark.class);

    public ElegantMinimalClockDark() {
        setTitle("Elegant Clock - Dark");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(920, 620);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(new Color(18, 20, 23));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(root);

        JPanel topColumn = new JPanel(new BorderLayout(12, 12));
        topColumn.setOpaque(false);
        topColumn.add(buildTimeSection(), BorderLayout.NORTH);
        topColumn.add(buildNavBar(), BorderLayout.SOUTH);
        root.add(topColumn, BorderLayout.NORTH);

        cardsPanel.add(buildAlarmPanel(), "ALARM");
        cardsPanel.add(buildTimerPanel(), "TIMER");
        cardsPanel.add(buildStopwatchPanel(), "STOPWATCH");
        cardsPanel.add(buildAudioPanel(), "AUDIO");
        showCard("ALARM");

        root.add(cardsPanel, BorderLayout.CENTER);

        loadSavedAudioPaths();
        startClock();
        setActiveNav(navAlarm);
        setVisible(true);
    }

    // Time section
    private JPanel buildTimeSection() {
        RoundedPanel card = new RoundedPanel(new Color(28, 30, 34), 14);
        card.setLayout(new BorderLayout(10, 10));
        card.setBorder(new EmptyBorder(12, 12, 12, 12));
        weekdayLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        weekdayLabel.setForeground(new Color(160, 170, 180));
        card.add(weekdayLabel, BorderLayout.NORTH);

        bigTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 56));
        bigTimeLabel.setForeground(new Color(220, 230, 240));
        card.add(bigTimeLabel, BorderLayout.CENTER);

        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        infoRow.setOpaque(false);

        dayLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        dayLabel.setForeground(new Color(230, 230, 230));
        dateLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        dateLabel.setForeground(new Color(240, 240, 240));
        monthLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        monthLabel.setForeground(new Color(180, 190, 200));
        infoRow.add(makeInfoCard("DAY", dayLabel));
        infoRow.add(makeInfoCard("DATE", dateLabel));
        infoRow.add(makeInfoCard("MONTH", monthLabel));
        card.add(infoRow, BorderLayout.SOUTH);

        // Update time immediately and periodically
        new Timer(true).scheduleAtFixedRate(new TimerTask() {
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                SwingUtilities.invokeLater(() -> {
                    bigTimeLabel.setText(now.format(timeFmt));
                    weekdayLabel.setText(now.format(dayFmt).toUpperCase());
                    dayLabel.setText(now.format(dayFmt).toUpperCase());
                    dateLabel.setText(now.format(dateFmt));
                    monthLabel.setText(now.format(monthFmt).toUpperCase());
                });
            }
        }, 0, 250);

        return card;
    }

    private JPanel makeInfoCard(String title, JLabel valueLabel) {
        RoundedPanel p = new RoundedPanel(new Color(22, 24, 28), 12);
        p.setLayout(new BorderLayout());
        p.setPreferredSize(new Dimension(120, 64));
        p.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        t.setForeground(new Color(140, 150, 160));
        p.add(t, BorderLayout.NORTH);
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(valueLabel, BorderLayout.CENTER);

        return p;
    }

    // Top navigation bar
    private JPanel buildNavBar() {
        RoundedPanel navBar = new RoundedPanel(new Color(24, 26, 30), 12);
        navBar.setLayout(new FlowLayout(FlowLayout.CENTER, 24, 12));
        navBar.setBorder(new EmptyBorder(8, 8, 8, 8));
        Font navFont = new Font("Segoe UI", Font.BOLD, 18);
        Color fg = new Color(220, 220, 220);
        Color bg = new Color(28, 30, 34);

        configureNavButton(navAlarm, "Alarm", new NavIcon(NavIcon.Type.ALARM, 18, new Color(160, 200, 255)), navFont, fg, bg);
        configureNavButton(navTimer, "Timer", new NavIcon(NavIcon.Type.TIMER, 18, new Color(160, 200, 255)), navFont, fg, bg);
        configureNavButton(navStopwatch, "Stopwatch", new NavIcon(NavIcon.Type.STOPWATCH, 18, new Color(160, 200, 255)), navFont, fg, bg);
        configureNavButton(navAudio, "Audio", new NavIcon(NavIcon.Type.TIMER, 18, new Color(160, 200, 255)), navFont, fg, bg);

        navAlarm.addActionListener((ActionEvent e) -> {
            showCard("ALARM");
            setActiveNav(navAlarm);
        });
        navTimer.addActionListener((ActionEvent e) -> {
            showCard("TIMER");
            setActiveNav(navTimer);
        });
        navStopwatch.addActionListener((ActionEvent e) -> {
            showCard("STOPWATCH");
            setActiveNav(navStopwatch);
        });
        navAudio.addActionListener((ActionEvent e) -> {
            showCard("AUDIO");
            setActiveNav(navAudio);
        });

        navBar.add(navAlarm);
        navBar.add(navTimer);
        navBar.add(navStopwatch);
        navBar.add(navAudio);

        return navBar;
    }

    private void configureNavButton(JButton b, String text, Icon icon, Font font, Color fg, Color bg) {
        b.setText(text);
        b.setIcon(icon);
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(10);
        b.setFont(font);
        b.setForeground(fg);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(200, 52));
    }

    // Ensure this method exists and is used by nav actions
    private void setActiveNav(JButton active) {
        Color activeBg = new Color(60, 130, 255);
        Color activeFg = Color.WHITE;
        Color inactiveBg = new Color(28, 30, 34);
        Color inactiveFg = new Color(200, 200, 200);

        JButton[] all = {navAlarm, navTimer, navStopwatch, navAudio};
        for (JButton b : all) {
            if (b == active) {
                b.setBackground(activeBg);
                b.setForeground(activeFg);
                b.setBorder(BorderFactory.createLineBorder(new Color(90, 160, 255), 2));
            } else {
                b.setBackground(inactiveBg);
                b.setForeground(inactiveFg);
                b.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            }
        }
    }

    private void showCard(String name) {
        CardLayout cl = (CardLayout) cardsPanel.getLayout();
        cl.show(cardsPanel, name);
    }

    // Panels for Alarm / Timer / Stopwatch

    private JPanel buildAlarmPanel() {
        JPanel p = new RoundedPanel(new Color(24, 26, 30), 12);
        p.setLayout(new GridBagLayout());
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel lbl = new JLabel("Alarm time (HH:MM AM/PM)");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lbl.setForeground(new Color(200, 200, 200));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(lbl, c);

        alarmField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        alarmField.setBackground(new Color(34, 36, 40));
        alarmField.setForeground(new Color(220, 220, 220));
        c.gridy = 1;
        c.gridwidth = 1;
        c.weightx = 1.0;
        p.add(alarmField, c);

        JButton setBtn = flatButton("Set Alarm");
        setBtn.addActionListener(e -> setAlarm());
        c.gridx = 1;
        c.weightx = 0;
        p.add(setBtn, c);

        JLabel hint = new JLabel("Sound file used: " + alarmSoundPath, SwingConstants.LEFT);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(new Color(150, 160, 170));
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        p.add(hint, c);

        return p;
    }

    private JPanel buildTimerPanel() {
        JPanel p = new RoundedPanel(new Color(24, 26, 30), 12);
        p.setLayout(new GridBagLayout());
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel lbl = new JLabel("Timer seconds");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lbl.setForeground(new Color(200, 200, 200));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        p.add(lbl, c);

        timerField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        timerField.setBackground(new Color(34, 36, 40));
        timerField.setForeground(new Color(220, 220, 220));
        c.gridy = 1;
        c.gridwidth = 1;
        c.weightx = 1.0;
        p.add(timerField, c);

        JButton start = flatButton("Start");
        start.addActionListener(e -> startTimer());
        c.gridx = 1;
        c.weightx = 0;
        p.add(start, c);

        timerProgress.setStringPainted(false);
        timerProgress.setPreferredSize(new Dimension(420, 10));
        timerProgress.setForeground(new Color(90, 160, 255));
        timerProgress.setBackground(new Color(34, 36, 40));
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 1.0;
        p.add(timerProgress, c);

        timerRemaining.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        timerRemaining.setForeground(new Color(180, 190, 200));
        c.gridy = 3;
        p.add(timerRemaining, c);

        JLabel hint = new JLabel("Sound file used: " + timerSoundPath, SwingConstants.LEFT);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(new Color(150, 160, 170));
        c.gridy = 4;
        p.add(hint, c);

        return p;
    }

    private JPanel buildStopwatchPanel() {
        JPanel p = new RoundedPanel(new Color(24, 26, 30), 12);
        p.setLayout(new BorderLayout(8, 8));
        p.setBorder(new EmptyBorder(14, 14, 14, 14));

        stopwatchLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        stopwatchLabel.setForeground(new Color(220, 220, 220));
        p.add(stopwatchLabel, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        controls.setOpaque(false);

        JButton playBtn = iconButton(new PlayIcon(18, new Color(220, 220, 220)), "Start");
        JButton stopBtn = iconButton(new StopIcon(16, new Color(220, 220, 220)), "Stop");
        JButton lapBtn = iconButton(new FlagIcon(16, new Color(220, 220, 220)), "Lap");
        JButton resetBtn = iconButton(new ResetIcon(18, new Color(220, 220, 220)), "Reset");
        playBtn.addActionListener(e -> startStopwatch());
        stopBtn.addActionListener(e -> stopStopwatch());
        lapBtn.addActionListener(e -> lapStopwatch());
        resetBtn.addActionListener(e -> resetStopwatch());
        controls.add(playBtn);
        controls.add(stopBtn);
        controls.add(lapBtn);
        controls.add(resetBtn);

        p.add(controls, BorderLayout.CENTER);

        JList<String> lapList = new JList<>(lapModel);
        lapList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lapList.setForeground(new Color(200, 200, 200));
        lapList.setBackground(new Color(28, 30, 34));
        lapList.setVisibleRowCount(4);
        JScrollPane sp = new JScrollPane(lapList);
        sp.setBorder(null);
        sp.setPreferredSize(new Dimension(0, 120));
        p.add(sp, BorderLayout.SOUTH);

        JLabel hint = new JLabel("Sounds: " + stopwatchClickPath + " | " + stopwatchResetPath, SwingConstants.LEFT);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(new Color(150, 160, 170));
        p.add(hint, BorderLayout.NORTH);

        return p;
    }

    // Audio selection panel
    private JPanel buildAudioPanel() {
        JPanel p = new RoundedPanel(new Color(24, 26, 30), 12);
        p.setLayout(new GridBagLayout());
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;

        JLabel title = new JLabel("Select audio files", SwingConstants.LEFT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(220, 220, 220));
        p.add(title, c);

        // Alarm row
        c.gridy++;
        c.gridwidth = 1;
        JLabel alarmLbl = new JLabel("Alarm:");
        alarmLbl.setForeground(new Color(200, 200, 200));
        p.add(alarmLbl, c);

        JButton alarmChoose = flatButton("Choose...");
        alarmChoose.addActionListener(e -> {
            String chosen = chooseAudioFile(alarmSoundPath);
            if (chosen != null) {
                alarmSoundPath = chosen;
                prefs.put(PREF_ALARM, alarmSoundPath);
                notifyUser("Alarm sound set");
            }
        });
        c.gridx = 1;
        p.add(alarmChoose, c);

        // Timer row
        c.gridx = 0;
        c.gridy++;
        JLabel timerLbl = new JLabel("Timer:");
        timerLbl.setForeground(new Color(200, 200, 200));
        p.add(timerLbl, c);

        JButton timerChoose = flatButton("Choose...");
        timerChoose.addActionListener(e -> {
            String chosen = chooseAudioFile(timerSoundPath);
            if (chosen != null) {
                timerSoundPath = chosen;
                prefs.put(PREF_TIMER, timerSoundPath);
                notifyUser("Timer sound set");
            }
        });
        c.gridx = 1;
        p.add(timerChoose, c);

        // Stopwatch click row
        c.gridx = 0;
        c.gridy++;
        JLabel clickLbl = new JLabel("Stopwatch click:");
        clickLbl.setForeground(new Color(200, 200, 200));
        p.add(clickLbl, c);

        JButton clickChoose = flatButton("Choose...");
        clickChoose.addActionListener(e -> {
            String chosen = chooseAudioFile(stopwatchClickPath);
            if (chosen != null) {
                stopwatchClickPath = chosen;
                prefs.put(PREF_STOPWATCH_CLICK, stopwatchClickPath);
                notifyUser("Stopwatch click sound set");
            }
        });
        c.gridx = 1;
        p.add(clickChoose, c);

        // Stopwatch reset row
        c.gridx = 0;
        c.gridy++;
        JLabel resetLbl = new JLabel("Stopwatch reset:");
        resetLbl.setForeground(new Color(200, 200, 200));
        p.add(resetLbl, c);

        JButton resetChoose = flatButton("Choose...");
        resetChoose.addActionListener(e -> {
            String chosen = chooseAudioFile(stopwatchResetPath);
            if (chosen != null) {
                stopwatchResetPath = chosen;
                prefs.put(PREF_STOPWATCH_RESET, stopwatchResetPath);
                notifyUser("Stopwatch reset sound set");
            }
        });
        c.gridx = 1;
        p.add(resetChoose, c);

        return p;
    }

    // Helpers
    private JButton flatButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        b.setBackground(new Color(60, 65, 75));
        b.setForeground(new Color(230, 230, 230));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(110, 36));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton iconButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setPreferredSize(new Dimension(44, 36));
        b.setBackground(new Color(28, 30, 34));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setToolTipText(tooltip);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // Clock updater
    private void startClock() {
        Timer t = new Timer(true);
        t.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                SwingUtilities.invokeLater(() -> {
                    bigTimeLabel.setText(now.format(timeFmt));
                    weekdayLabel.setText(now.format(dayFmt).toUpperCase());
                    dayLabel.setText(now.format(dayFmt).toUpperCase());
                    dateLabel.setText(now.format(dateFmt));
                    monthLabel.setText(now.format(monthFmt).toUpperCase());
                });
            }
        }, 0, 250);
    }

    // Alarm logic
    private void setAlarm() {
        String text = alarmField.getText().trim();
        if (text.isEmpty()) {
            notifyUser("Enter alarm time like 08:30 PM");
            return;
        }
        try {
            DateTimeFormatter parseFmt = DateTimeFormatter.ofPattern("hh:mm a");
            java.time.LocalTime alarmTime = java.time.LocalTime.parse(text.toUpperCase(), parseFmt);
            if (alarmChecker != null) alarmChecker.cancel();
            alarmChecker = new Timer(true);
            alarmChecker.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    java.time.LocalTime now = java.time.LocalTime.now().withSecond(0).withNano(0);
                    if (now.equals(alarmTime)) {
                        SwingUtilities.invokeLater(() -> {
                            notifyUser("Alarm");
                            playSound(alarmSoundPath);
                        });
                        alarmChecker.cancel();
                    }
                }
            }, 0, 1000);
            notifyUser("Alarm set for " + alarmTime.format(DateTimeFormatter.ofPattern("hh:mm a")));
        } catch (Exception ex) {
            notifyUser("Invalid format. Use HH:MM AM/PM");
        }
    }

    // Timer logic
    private void startTimer() {
        String t = timerField.getText().trim();
        if (t.isEmpty()) {
            notifyUser("Enter seconds");
            return;
        }
        try {
            int seconds = Integer.parseInt(t);
            if (seconds <= 0) {
                notifyUser("Enter a positive number");
                return;
            }
            if (countdownTimer != null) countdownTimer.cancel();
            countdownSeconds = seconds;
            timerProgress.setMaximum(seconds);
            timerProgress.setValue(0);
            timerRemaining.setText(formatSeconds(seconds));
            countdownTimer = new Timer(true);
            countdownTimer.scheduleAtFixedRate(new TimerTask() {
                int elapsed = 0;

                public void run() {
                    elapsed++;
                    SwingUtilities.invokeLater(() -> {
                        timerProgress.setValue(elapsed);
                        timerRemaining.setText(formatSeconds(Math.max(0, countdownSeconds - elapsed)));
                    });
                    if (elapsed >= countdownSeconds) {
                        SwingUtilities.invokeLater(() -> {
                            notifyUser("Timer finished");
                            playSound(timerSoundPath);
                        });
                        countdownTimer.cancel();
                    }
                }
            }, 1000, 1000);
            notifyUser("Timer started for " + seconds + "s");
        } catch (NumberFormatException ex) {
            notifyUser("Enter a valid integer");
        }
    }

    // Stopwatch logic
    private void startStopwatch() {
        if (stopwatchRunning) return;
        stopwatchStart = System.currentTimeMillis();
        stopwatchRunning = true;
        stopwatchTimer = new Timer(true);
        stopwatchTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                long elapsed = System.currentTimeMillis() - stopwatchStart;
                SwingUtilities.invokeLater(() -> stopwatchLabel.setText(formatStopwatch(elapsed)));
            }
        }, 0, 50);
        playSound(stopwatchClickPath);
    }

    private void stopStopwatch() {
        if (!stopwatchRunning) return;
        stopwatchRunning = false;
        if (stopwatchTimer != null) stopwatchTimer.cancel();
        playSound(stopwatchClickPath);
    }

    private void lapStopwatch() {
        if (!stopwatchRunning) return;
        long elapsed = System.currentTimeMillis() - stopwatchStart;
        lapModel.addElement("Lap " + (lapModel.getSize() + 1) + " " + formatStopwatch(elapsed));
    }

    private void resetStopwatch() {
        stopwatchRunning = false;
        if (stopwatchTimer != null) stopwatchTimer.cancel();
        stopwatchLabel.setText("00:00.00");
        lapModel.clear();
        playSound(stopwatchResetPath);
    }

    // Utilities
    private String formatSeconds(int s) {
        int mm = s / 60;
        int ss = s % 60;
        return String.format("%02d:%02d", mm, ss);
    }

    private String formatStopwatch(long ms) {
        int centis = (int) ((ms % 1000) / 10);
        int seconds = (int) (ms / 1000) % 60;
        int minutes = (int) (ms / 60000);
        return String.format("%02d:%02d.%02d", minutes, seconds, centis);
    }

    private void notifyUser(String message) {
        final JDialog d = new JDialog(this, false);
        d.setUndecorated(true);

        JLabel l = new JLabel(message, SwingConstants.CENTER);
        l.setBorder(new EmptyBorder(10, 18, 10, 18));
        l.setBackground(new Color(40, 44, 50));
        l.setForeground(Color.WHITE);
        l.setOpaque(true);
        d.add(l);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setLocation(d.getX(), d.getY() - 80);
        d.setVisible(true);
        new java.util.Timer().schedule(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(d::dispose);
            }
        }, 1400);
    }

    /**
     * Play sound using AudioSystem (WAV/AIFF/AU). Prints diagnostics if file missing or unsupported.
     */
    private void playSound(String path) {
        if (path == null || path.isEmpty()) return;
        File f = new File(path);
        System.out.println("Attempting to play: " + f.getAbsolutePath());
        if (!f.exists()) {
            System.err.println("Sound file not found: " + f.getAbsolutePath());
            return;
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(f)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.addLineListener(evt -> {
                if (evt.getType() == LineEvent.Type.STOP) {
                    clip.close();
                    activeClips.remove(clip);
                }
            });
            activeClips.add(clip);
            clip.start();
        } catch (Exception ex) {
            System.err.println("Audio playback failed for: " + f.getAbsolutePath());
            ex.printStackTrace();
        }
    }

    // File chooser helper
    private String chooseAudioFile(String currentPath) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select audio file");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileNameExtensionFilter filter =
                new FileNameExtensionFilter("Audio files (wav, aiff, au, mp3)", "wav", "aiff", "au", "mp3");
        chooser.setFileFilter(filter);
        if (currentPath != null) {
            File cur = new File(currentPath);
            if (cur.exists()) chooser.setSelectedFile(cur);
        }
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            return f.getAbsolutePath();
        }
        return null;
    }

    private void loadSavedAudioPaths() {
        alarmSoundPath = prefs.get(PREF_ALARM, alarmSoundPath);
        timerSoundPath = prefs.get(PREF_TIMER, timerSoundPath);
        stopwatchClickPath = prefs.get(PREF_STOPWATCH_CLICK, stopwatchClickPath);
        stopwatchResetPath = prefs.get(PREF_STOPWATCH_RESET, stopwatchResetPath);
    }

    // Rounded panel helper
    static class RoundedPanel extends JPanel {
        private final Color bg;
        private final int radius;

        RoundedPanel(Color bg, int radius) {
            this.bg = bg;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Vector icons
    static class NavIcon implements Icon {
        enum Type {ALARM, TIMER, STOPWATCH}

        private final Type type;
        private final int size;
        private final Color color;

        NavIcon(Type type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            switch (type) {
                case ALARM:
                    g2.fillOval(x + 2, y + 2, size - 4, size - 6);
                    g2.fillRect(x + size / 3, y + size / 2, size / 3, size / 3);
                    break;
                case TIMER:
                    g2.fillOval(x + 2, y + 2, size - 4, size - 4);
                    g2.fillRect(x + size / 2 - 2, y - 2, 4, 6);
                    break;
                case STOPWATCH:
                    int[] xs = {x + 2, x + size - 2, x + 2};
                    int[] ys = {y + 2, y + size / 2, y + size - 2};
                    g2.fillPolygon(xs, ys, 3);
                    break;
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static class PlayIcon implements Icon {
        private final int size;
        private final Color color;

        PlayIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int[] xs = {x + 2, x + size - 2, x + 2};
            int[] ys = {y + 2, y + size / 2, y + size - 2};
            g2.setColor(color);
            g2.fillPolygon(xs, ys, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static class StopIcon implements Icon {
        private final int size;
        private final Color color;

        StopIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int pad = 3;
            g2.setColor(color);
            g2.fillRect(x + pad, y + pad, size - pad * 2, size - pad * 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static class FlagIcon implements Icon {
        private final int size;
        private final Color color;

        FlagIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int poleX = x + 6;
            g2.fillRect(poleX, y + 2, 2, size - 4);
            int[] xs = {poleX + 2, x + size - 2, poleX + 2};
            int[] ys = {y + 4, y + 8, y + 12};
            g2.fillPolygon(xs, ys, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static class ResetIcon implements Icon {
        private final int size;
        private final Color color;

        ResetIcon(int size, Color color) {
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(color);
            int cx = x + size / 2, cy = y + size / 2, r = size / 2 - 4;
            g2.drawArc(cx - r, cy - r, r * 2, r * 2, 30, 300);
            int[] px = {x + size - 8, x + size - 2, x + size - 8};
            int[] py = {y + 6, y + 10, y + 14};
            g2.fillPolygon(px, py, 3);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    // Main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ElegantMinimalClockDark::new);
    }
}