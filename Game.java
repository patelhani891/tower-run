import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*; // RadialGradientPaint is here
import java.awt.event.*;
import java.awt.geom.Point2D; 
import java.io.File;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class Game extends JPanel implements Runnable, KeyListener {

    private final int WIDTH = 600, HEIGHT = 800;
    private Thread gameThread;
    private boolean running = true;

    // STATES
    private final int TITLE_STATE = 0, MAP_SELECT_STATE = 1, CHAR_SELECT_STATE = 2, PLAY_STATE = 3, PAUSE_STATE = 4, OVER_STATE = 5;
    private int currentState = TITLE_STATE;

    // THEMES
    private final int JUNGLE_THEME = 0, DARK_THEME = 1, COMBINED_THEME = 2;
    private int selectedMap = JUNGLE_THEME;

    // AUDIO
    private Clip bgmClip;
    private FloatControl bgmVolume;
    private Timer musicFadeTimer;

    // VISUALS & PARTICLES (Thread-Safe)
    private CopyOnWriteArrayList<Point> jumpTrail = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<GhostSnapshot> ghostTrail = new CopyOnWriteArrayList<>(); 
    private CopyOnWriteArrayList<Particle> particles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<FloatingText> floatingTexts = new CopyOnWriteArrayList<>();
    private Star[] stars = new Star[100]; 
    
    // Cloud Variables
    private int[] cloudX = new int[5], cloudY = new int[5];

    private boolean bossActive = false, laserFiring = false;
    private float laserX = 300;
    private int laserWarningTimer = 0, flashAlpha = 0, combo = 0, animFrame = 0;
    private String alertText = "";
    private int alertTimer = 0;
    private int[] speedLineY = new int[15], speedLineX = new int[15];

    // GOD LEVEL COLORS
    private final Color NEON_CYAN = new Color(0, 255, 255);
    private final Color NEON_PINK = new Color(255, 0, 128);
    private final Color NEON_GREEN = new Color(50, 255, 50);
    private final Color GOLD_ORANGE = new Color(255, 200, 50);
    private final Color PHANTOM_PURPLE = new Color(130, 0, 255);
    private final Color DARK_BG = new Color(10, 10, 18);
    private final Color UI_BG = new Color(20, 20, 30, 200); 
    
    // JUNGLE COLORS
    private final Color JUNGLE_SKY_TOP = new Color(0, 150, 255);
    private final Color JUNGLE_SKY_BOT = new Color(100, 200, 255);
    private final Color JUNGLE_WALL = new Color(60, 40, 20); // Brown
    private final Color JUNGLE_VINE = new Color(30, 120, 30); // Green

    // FONTS
    private Font gameFont = new Font("Verdana", Font.BOLD, 18);
    private Font hudFont = new Font("Segoe UI", Font.BOLD, 16);
    private Font comboFont = new Font("Impact", Font.ITALIC, 36);
    private Font titleFont = new Font("Impact", Font.BOLD, 80);
    private Font menuFont = new Font("Segoe UI", Font.BOLD, 22);
    private Font descFont = new Font("Segoe UI", Font.PLAIN, 14);

    // PLAYER & SKILLS
    private int ninjaX = 100, ninjaY = 600, ninjaWidth = 40, ninjaHeight = 45;
    private int xSpeed = 0, jumpPower = 20;
    private boolean onLeftWall = true, isJumping = false;
    private long skillCooldownEnd = 0, skillActiveTimer = 0, totalCooldownTime = 15000;
    private boolean isSkillActive = false;
    private int solarOrbsLeft = 0; 
    private boolean hasRevived = false;
    
    // Solar Invincibility Logic
    private long solarIFrameEnd = 0; 

    // OBSTACLES
    private int droneX, droneY = -500, scrollSpeed = 8, pipeY = -400, pipeSide = 0, score = 0;
    private boolean droneActive = false, missileActive = false;
    private int missileX, missileY = -500;

    private Random random = new Random();
    
    // MENUS DATA
    private String[] characters = {"Phantom", "Cyber", "Solar", "Glitch"};
    private String[] charDescriptions = {
        "CLASS: PHANTOM\nSkill: Become a Ghost (4s).\nPass through all matter. Ignore Reality.",
        "CLASS: CYBER\nSkill: Static Overload (4s).\nArc lightning destroys nearby enemies automatically.",
        "CLASS: SOLAR\nSkill: Absolute Defense.\nSummons 2 Sun Orbs. Orbs take damage for you.\n(Guaranteed 2 Extra Lives)",
        "CLASS: GLITCH\nSkill: System Restore.\nSurvive death once. Emits a reality-breaking\nshockwave that clears the screen."
    };
    private int selectedChar = 0;
    private long gracePeriod = 0;
    private long deathTime = 0; 

    // --- INNER CLASSES ---
    class Star {
        int x, y, size;
        float speed;
        float alpha;
        public Star() { reset(true); }
        void reset(boolean randomY) {
            x = random.nextInt(WIDTH);
            y = randomY ? random.nextInt(HEIGHT) : -10;
            size = random.nextInt(3) + 1;
            speed = (random.nextFloat() * 2) + 0.5f;
            alpha = random.nextFloat();
        }
        void update(float boost) {
            y += speed + boost;
            if (y > HEIGHT) reset(false);
        }
        void draw(Graphics2D g2) {
            g2.setColor(new Color(1f, 1f, 1f, alpha));
            g2.fillOval(x, y, size, size);
        }
    }

    class FloatingText {
        String text;
        float x, y;
        float alpha = 1.0f;
        Color color;
        public FloatingText(String text, int x, int y, Color c) {
            this.text = text; this.x = x; this.y = y; this.color = c;
        }
        boolean update() { y -= 2; alpha -= 0.02f; return alpha > 0; }
        void draw(Graphics2D g2) {
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255 * alpha)));
            g2.setFont(new Font("Verdana", Font.BOLD, 16));
            g2.drawString(text, (int)x - (text.length()*4), (int)y);
        }
    }

    class Particle {
        float x, y, vx, vy;
        int life;
        Color color;
        String textOverride = null; 
        public Particle(int x, int y, Color c) {
            this.x = x; this.y = y; this.color = c;
            this.vx = (random.nextFloat() - 0.5f) * 10;
            this.vy = (random.nextFloat() - 0.5f) * 10;
            this.life = 30 + random.nextInt(20);
        }
        public Particle(int x, int y, Color c, String text) {
            this(x, y, c); this.textOverride = text;
        }
        boolean update() { x += vx; y += vy; life--; return life > 0; }
        void draw(Graphics2D g2) {
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, life * 5)));
            if (textOverride != null) {
                g2.setFont(new Font("Monospaced", Font.BOLD, 12));
                g2.drawString(textOverride, x, y);
            } else {
                g2.fillRect((int)x, (int)y, 4, 4); 
            }
        }
    }

    class GhostSnapshot {
        int x, y;
        public GhostSnapshot(int x, int y) { this.x = x; this.y = y; }
    }

    public Game() {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);
        this.addKeyListener(this);
        this.setFocusTraversalKeysEnabled(false);
        this.setBackground(Color.BLACK);
        
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });

        for(int i=0; i<15; i++) {
            speedLineX[i] = random.nextInt(WIDTH - 200) + 100;
            speedLineY[i] = random.nextInt(HEIGHT);
        }
        for(int i=0; i<5; i++) {
            cloudX[i] = random.nextInt(WIDTH);
            cloudY[i] = random.nextInt(HEIGHT);
        }
        for(int i=0; i<stars.length; i++) stars[i] = new Star();
        
        initBGM("bgm.wav");
    }

    // --- AUDIO SYSTEM ---
    private void initBGM(String file) {
        try {
            File f = new File(file);
            if (f.exists()) {
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(f);
                bgmClip = AudioSystem.getClip();
                bgmClip.open(audioIn);
                bgmVolume = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN);
                bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
                setBGMVolume(-15.0f);
            }
        } catch (Exception e) {}
    }

    private void setBGMVolume(float v) { if (bgmVolume != null) bgmVolume.setValue(v); }

    private void fadeMusicIn() {
        if (bgmClip == null) return;
        if (bgmClip.isRunning()) return;
        bgmClip.setFramePosition(0);
        bgmClip.start();
        bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
        setBGMVolume(-60.0f); 
        if (musicFadeTimer != null && musicFadeTimer.isRunning()) musicFadeTimer.stop();
        musicFadeTimer = new Timer(100, new ActionListener() {
            float vol = -60.0f;
            @Override
            public void actionPerformed(ActionEvent e) {
                vol += 2.0f;
                if (vol >= -15.0f) { vol = -15.0f; ((Timer)e.getSource()).stop(); }
                setBGMVolume(vol);
            }
        });
        musicFadeTimer.start();
    }

    private void stopMusic() { if (bgmClip != null && bgmClip.isRunning()) bgmClip.stop(); }

    public void playSound(String s) {
        new Thread(() -> {
            try {
                File f = new File(s);
                if (f.exists()) {
                    AudioInputStream ai = AudioSystem.getAudioInputStream(f);
                    Clip c = AudioSystem.getClip(); c.open(ai); c.start();
                }
            } catch (Exception e) {}
        }).start();
    }

    public void start() { gameThread = new Thread(this); gameThread.start(); }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double amountOfTicks = 60.0;
        double ns = 1000000000 / amountOfTicks;
        double delta = 0;
        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            while(delta >= 1) { update(); delta--; }
            repaint();
        }
    }

    private void resetGame() {
        score = 0; combo = 0; ninjaX = 100; ninjaY = 600; onLeftWall = true; isJumping = false;
        bossActive = false; laserFiring = false; alertTimer = 0;
        
        if(selectedChar == 1) { 
            ninjaHeight = 35; jumpPower = 24; scrollSpeed = 10; totalCooldownTime = 20000; 
        } else { 
            ninjaHeight = 45; jumpPower = 19; scrollSpeed = 8; totalCooldownTime = 15000; 
        }
        
        hasRevived = false; isSkillActive = false; solarOrbsLeft = 0; skillCooldownEnd = 0;
        gracePeriod = System.currentTimeMillis() + 1500;
        jumpTrail.clear(); ghostTrail.clear(); particles.clear(); floatingTexts.clear();
        missileActive = false; droneActive = false;
        solarIFrameEnd = 0; 
        stopMusic();
    }

    public void update() {
        if (currentState == OVER_STATE) {
            long now = System.currentTimeMillis();
            if (now > deathTime + 3000 && bgmClip != null && !bgmClip.isRunning()) fadeMusicIn();
        }

        // Starfield Update (For Menu/Neon)
        float boost = (currentState == PLAY_STATE) ? scrollSpeed * 0.5f : 0.5f;
        for(Star s : stars) s.update(boost);
        
        // Cloud Update (For Jungle)
        for(int i=0; i<5; i++) {
            cloudY[i] += 1; // Slow clouds
            if(cloudY[i] > HEIGHT) { cloudY[i] = -100; cloudX[i] = random.nextInt(WIDTH); }
        }

        if (currentState != PLAY_STATE) return;

        score++; animFrame++;
        if (flashAlpha > 0) flashAlpha -= 8;
        if (alertTimer > 0) alertTimer--;

        for (Particle p : particles) if (!p.update()) particles.remove(p);
        for (FloatingText ft : floatingTexts) if(!ft.update()) floatingTexts.remove(ft);

        if (score == 1950) { alertText = "WARNING: BOSS INBOUND"; alertTimer = 120; flashAlpha = 150; }
        if (score >= 2000) { bossActive = true; updateBoss(); }

        if (pipeY > HEIGHT) { combo++; pipeY = -200; pipeSide = random.nextInt(2); }
        if (droneY > HEIGHT) { combo++; droneActive = false; }
        
        if (score > 1500 && score % 800 == 0) scrollSpeed += 1;

        for(int i=0; i<15; i++) {
            speedLineY[i] += scrollSpeed + 12;
            if(speedLineY[i] > HEIGHT) { speedLineY[i] = -100; speedLineX[i] = random.nextInt(WIDTH - 200) + 100; }
        }

        if (isSkillActive && System.currentTimeMillis() > skillActiveTimer) isSkillActive = false;

        // Skill: Phantom
        if (selectedChar == 0 && isSkillActive) {
            if (animFrame % 5 == 0) {
                ghostTrail.add(new GhostSnapshot(ninjaX, ninjaY));
                if (ghostTrail.size() > 4) ghostTrail.remove(0);
            }
        } else { ghostTrail.clear(); }

        // Skill: Cyber
        if (selectedChar == 1 && isSkillActive && droneActive) {
            double dist = Math.sqrt(Math.pow(ninjaX - droneX, 2) + Math.pow(ninjaY - droneY, 2));
            if (dist < 200) { 
                droneActive = false;
                spawnExplosion(droneX, droneY, NEON_CYAN);
                playSound("zap.wav");
                combo++;
            }
        }

        // Ninja Movement
        if (isJumping) {
            jumpTrail.add(new Point(ninjaX, ninjaY));
            if (jumpTrail.size() > 6) jumpTrail.remove(0);
            ninjaX += xSpeed;
            if (ninjaX >= WIDTH - 140) {
                ninjaX = WIDTH - 140; isJumping = false; onLeftWall = false; jumpTrail.clear();
                spawnWallHitParticles(ninjaX + ninjaWidth, ninjaY + ninjaHeight/2);
            }
            if (ninjaX <= 100) {
                ninjaX = 100; isJumping = false; onLeftWall = true; jumpTrail.clear();
                spawnWallHitParticles(ninjaX, ninjaY + ninjaHeight/2);
            }
        } else jumpTrail.clear();

        pipeY += scrollSpeed;
        if (!droneActive) { droneY = -300; droneX = random.nextInt(WIDTH - 300) + 150; droneActive = true; }
        else droneY += scrollSpeed + 4;

        // Missile Logic
        if (score > 300 && !bossActive && !missileActive) {
            missileX = WIDTH/2; missileY = -50; missileActive = true; 
        }
        if (missileActive) {
            missileY += scrollSpeed + 6;
            int homing = (selectedChar == 1) ? 4 : 3;
            if (missileX < ninjaX) missileX += homing; else missileX -= homing;
            if (missileY > HEIGHT) { combo++; missileActive = false; }
        }

        if (System.currentTimeMillis() > gracePeriod) checkCollisions();
    }

    private void spawnWallHitParticles(int x, int y) {
        for(int i=0; i<10; i++) particles.add(new Particle(x, y, Color.WHITE));
    }

    private void updateBoss() {
        float trackingSpeed = (score >= 3000) ? 0.08f : 0.04f;
        if (!laserFiring) {
            laserX += (ninjaX - laserX) * trackingSpeed;
            laserWarningTimer++;
            int limit = (score >= 3000) ? 70 : 100;
            if (laserWarningTimer > limit) { laserFiring = true; laserWarningTimer = 0; }
        } else {
            laserWarningTimer++;
            if (animFrame % 2 == 0) particles.add(new Particle((int)laserX, random.nextInt(HEIGHT), NEON_PINK));
            if (laserWarningTimer > 40) { laserFiring = false; laserWarningTimer = 0; }
        }
    }

    private void checkCollisions() {
        Rectangle nR = new Rectangle(ninjaX, ninjaY, 40, ninjaHeight);
        Rectangle dR = new Rectangle(droneX, droneY, 40, 25);
        Rectangle mR = new Rectangle(missileX, missileY, 15, 15);
        int px = (pipeSide == 0) ? 100 : WIDTH - 130;
        Rectangle pR = new Rectangle(px, pipeY, 30, 80);

        boolean hit = false;
        if (laserFiring) {
            Rectangle laserRect = new Rectangle((int)laserX - 20, 0, 40, HEIGHT);
            if (nR.intersects(laserRect)) hit = true;
        }
        if (nR.intersects(dR) || nR.intersects(pR) || nR.intersects(mR)) hit = true;

        if (hit) handleHit();
    }

    private void handleHit() {
        long now = System.currentTimeMillis();

        if (selectedChar == 2) {
            if (now < solarIFrameEnd) return; 
            if (solarOrbsLeft > 0) {
                solarOrbsLeft--; 
                flashAlpha = 150;
                playSound("break.wav");
                solarIFrameEnd = now + 1000; 
                droneActive = false; missileActive = false; pipeY = -500;
                spawnExplosion(ninjaX, ninjaY, GOLD_ORANGE);
                floatingTexts.add(new FloatingText("SHIELD BROKEN!", ninjaX, ninjaY - 20, GOLD_ORANGE));
                return;
            }
        }
        
        if (isSkillActive && selectedChar != 2) {
            droneActive = false; missileActive = false; pipeY = -500;
            spawnExplosion(droneX, droneY, selectedChar==0 ? PHANTOM_PURPLE : NEON_CYAN);
            return;
        }

        if (selectedChar == 3 && !hasRevived) {
            hasRevived = true; isSkillActive = true; flashAlpha = 255;
            skillActiveTimer = System.currentTimeMillis() + 3000;
            playSound("glitch.wav"); 
            droneActive = false; missileActive = false; pipeY = -500;
            for(int i=0; i<40; i++) particles.add(new Particle(ninjaX, ninjaY, NEON_GREEN, random.nextBoolean() ? "1" : "0"));
            floatingTexts.add(new FloatingText("SYSTEM RESTORED!", ninjaX, ninjaY - 20, NEON_GREEN));
            return;
        }

        combo = 0; 
        playSound("death" + (random.nextInt(3) + 1) + ".wav");
        currentState = OVER_STATE; 
        deathTime = System.currentTimeMillis(); 
    }

    private void spawnExplosion(int x, int y, Color c) {
        for(int i=0; i<30; i++) particles.add(new Particle(x, y, c));
    }

    // --- VISUAL RENDERING ---

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (currentState == TITLE_STATE) drawTitle(g2);
        else if (currentState == MAP_SELECT_STATE) drawMapSelect(g2);
        else if (currentState == CHAR_SELECT_STATE) drawSelect(g2);
        else {
            drawGame(g2);
            if (currentState == PAUSE_STATE) drawPause(g2);
            if (currentState == OVER_STATE) drawOver(g2);
        }
    }

    private void drawGame(Graphics2D g2) {
        // --- DETERMINE ACTIVE THEME ---
        int currentTheme = selectedMap;
        if(selectedMap == COMBINED_THEME) {
            // Switch every 1000 points
            currentTheme = (score / 1000) % 2 == 0 ? JUNGLE_THEME : DARK_THEME;
        }

        if (currentTheme == JUNGLE_THEME) {
            // --- JUNGLE VISUALS ---
            // Day Gradient
            g2.setPaint(new GradientPaint(0, 0, JUNGLE_SKY_TOP, 0, HEIGHT, JUNGLE_SKY_BOT));
            g2.fillRect(0, 0, WIDTH, HEIGHT);
            
            // Clouds
            g2.setColor(new Color(255, 255, 255, 150));
            for(int i=0; i<5; i++) g2.fillRoundRect(cloudX[i], cloudY[i], 120, 60, 30, 30);
            
            // Organic Walls
            g2.setColor(JUNGLE_WALL);
            g2.fillRect(0, 0, 100, HEIGHT); g2.fillRect(WIDTH-100, 0, 100, HEIGHT);
            
            // Vines
            g2.setColor(JUNGLE_VINE);
            g2.fillRect(90, 0, 10, HEIGHT); g2.fillRect(WIDTH-100, 0, 10, HEIGHT);
            
            // Moving Wall Details (Leaves/Vines)
            int wallOffset = (animFrame * 14) % 100;
            g2.setColor(new Color(40, 100, 40));
            for(int i=-100; i<HEIGHT; i+=100) {
                g2.fillOval(-30, i + wallOffset, 80, 40);
                g2.fillOval(WIDTH-50, i + wallOffset, 80, 40);
            }
            
        } else {
            // --- NEON VISUALS ---
            // Deep Space
            g2.setPaint(new GradientPaint(0, 0, new Color(10, 10, 30), 0, HEIGHT, Color.BLACK));
            g2.fillRect(0, 0, WIDTH, HEIGHT);
            drawCyberGrid(g2);
            for(Star s : stars) s.draw(g2);
            
            // Neon Walls
            g2.setColor(new Color(20, 20, 40));
            g2.fillRect(0, 0, 100, HEIGHT); g2.fillRect(WIDTH-100, 0, 100, HEIGHT);
            g2.setColor(NEON_PINK);
            g2.fillRect(98, 0, 2, HEIGHT); g2.fillRect(WIDTH-100, 0, 2, HEIGHT);
            
            // Neon Lines
            int wallOffset = (animFrame * 14) % 100;
            g2.setColor(new Color(255, 0, 128, 30));
            for(int i=-100; i<HEIGHT; i+=100) {
                g2.drawLine(20, i + wallOffset, 80, i + wallOffset + 20);
                g2.drawLine(WIDTH-20, i + wallOffset, WIDTH-80, i + wallOffset + 20);
            }
        }

        // --- GAME OBJECTS ---
        
        // BOSS
        if (bossActive) {
            if (laserFiring) {
                g2.setColor(Color.WHITE); g2.fillRect((int)laserX - 5, 0, 10, HEIGHT);
                g2.setPaint(new GradientPaint((int)laserX-40, 0, new Color(255, 0, 100, 0), (int)laserX, 0, NEON_PINK));
                g2.fillRect((int)laserX - 40, 0, 40, HEIGHT);
                g2.setPaint(new GradientPaint((int)laserX, 0, NEON_PINK, (int)laserX+40, 0, new Color(255, 0, 100, 0)));
                g2.fillRect((int)laserX, 0, 40, HEIGHT);
            } else {
                g2.setColor(new Color(1f, 0f, 0f, (float)Math.abs(Math.sin(animFrame * 0.2)) * 0.8f));
                g2.fillRect((int)laserX - 1, 0, 2, HEIGHT);
            }
        }

        // Speed Lines (Subtle in Jungle, Bright in Neon)
        g2.setColor(new Color(255, 255, 255, currentTheme == JUNGLE_THEME ? 10 : 30));
        for(int i=0; i<15; i++) g2.fillRect(speedLineX[i], speedLineY[i], 2, 40 + (int)(scrollSpeed*2));

        // Pipe Obstacle
        int px = (pipeSide == 0) ? 100 : WIDTH - 130;
        if(currentTheme == JUNGLE_THEME) {
            g2.setPaint(new GradientPaint(px, pipeY, new Color(100, 50, 20), px+30, pipeY, new Color(150, 100, 50))); // Wood Log
        } else {
            g2.setColor(new Color(255, 0, 0, 50)); g2.fillOval(px-10, pipeY-10, 50, 100); 
            g2.setPaint(new GradientPaint(px, pipeY, Color.RED.darker(), px+30, pipeY, Color.RED));
        }
        g2.fillRect(px, pipeY, 30, 80);
        g2.setColor(Color.WHITE); g2.drawRect(px, pipeY, 30, 80);

        // Drone
        if (droneActive) {
            g2.setColor(Color.DARK_GRAY); g2.fillRoundRect(droneX, droneY, 40, 25, 5, 5);
            g2.setColor(currentTheme == JUNGLE_THEME ? Color.RED : NEON_CYAN);
            g2.fillOval(droneX + 12, droneY + 5, 16, 16);
            g2.setColor(new Color(255, 255, 255, 100)); g2.fillOval(droneX + 8, droneY + 1, 24, 24); 
            g2.setColor(Color.WHITE); g2.fillOval(droneX + 16, droneY + 8, 4, 4);
        }
        // Missile
        if (missileActive) {
            g2.setColor(Color.ORANGE); g2.fillOval(missileX, missileY, 15, 15);
            g2.setColor(new Color(255, 200, 0, 100)); g2.fillOval(missileX-5, missileY-5, 25, 25);
            g2.setColor(Color.YELLOW); g2.fillOval(missileX+3, missileY+3, 9, 9);
        }

        // Particles
        for (Point p : jumpTrail) {
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(p.x, p.y, 40, ninjaHeight, 10, 10);
        }
        for(Particle p : particles) p.draw(g2);

        drawNinja(g2, ninjaX, ninjaY, getCharColor());
        for(FloatingText ft : floatingTexts) ft.draw(g2);
        
        // VIGNETTE EFFECT (Atmosphere)
        RadialGradientPaint vignette = new RadialGradientPaint(
            new Point2D.Float(WIDTH/2, HEIGHT/2), WIDTH,
            new float[]{0.0f, 1.0f}, 
            new Color[]{new Color(0,0,0,0), new Color(0,0,0,150)}
        );
        g2.setPaint(vignette);
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        drawHUD(g2);

        if (flashAlpha > 0) {
            g2.setColor(new Color(255, 255, 255, Math.min(255, flashAlpha)));
            g2.fillRect(0, 0, WIDTH, HEIGHT);
        }
        if (alertTimer > 0 && (alertTimer / 10) % 2 == 0) {
            g2.setColor(new Color(255, 0, 0, 100));
            g2.fillRect(0, 350, WIDTH, 60);
            g2.setColor(Color.YELLOW); g2.setFont(gameFont.deriveFont(30f));
            drawCenteredString(g2, alertText, 390);
        }
    }

    private void drawNinja(Graphics2D g2, int x, int y, Color c) {
        if (selectedChar == 2 && System.currentTimeMillis() < solarIFrameEnd) {
            if (animFrame % 4 == 0) return; 
        }

        if (selectedChar == 0 && isSkillActive) {
            for(GhostSnapshot ghost : ghostTrail) {
                g2.setColor(new Color(PHANTOM_PURPLE.getRed(), PHANTOM_PURPLE.getGreen(), PHANTOM_PURPLE.getBlue(), 80));
                g2.fillRoundRect(ghost.x, ghost.y, 40, ninjaHeight, 10, 10);
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        }

        if (selectedChar == 1 && isSkillActive) {
            g2.setColor(new Color(200, 255, 255));
            for(int i=0; i<3; i++) {
                g2.setStroke(new BasicStroke(random.nextInt(3)+1));
                int lx = x + 20; int ly = y + 20;
                int tx = random.nextBoolean() ? 100 : WIDTH - 100; 
                int ty = ly + random.nextInt(100) - 50;
                g2.drawLine(lx, ly, tx, ty);
            }
            g2.setStroke(new BasicStroke(1));
        }

        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
        g2.fillOval(x-10, y-10, 60, ninjaHeight+20);
        
        g2.setColor(c);
        g2.fillRoundRect(x, y, 40, ninjaHeight, 10, 10);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(3));
        g2.drawLine(x+5, y+5, x+35, y+35);
        g2.setStroke(new BasicStroke(1));

        g2.setColor(c.darker());
        int scarfX = onLeftWall ? x + 35 : x - 5;
        int scarfY = y + 10;
        int tipX = onLeftWall ? scarfX + 30 : scarfX - 30;
        int tipY = scarfY - (int)(xSpeed * 0.5) + (int)(Math.sin(animFrame * 0.5) * 5); 
        if(isJumping) tipY -= 15; 
        
        Polygon scarf = new Polygon();
        scarf.addPoint(scarfX, scarfY);
        scarf.addPoint(scarfX, scarfY + 8);
        scarf.addPoint(tipX, tipY);
        g2.fillPolygon(scarf);

        g2.setColor(Color.BLACK); 
        g2.fillRect(x, y + 8, 40, 10); 
        g2.setColor(Color.WHITE);
        if(selectedChar == 1) g2.setColor(NEON_CYAN);
        if(selectedChar == 0) g2.setColor(PHANTOM_PURPLE);
        g2.fillRect(x + 10, y + 11, 10, 4); 
        g2.fillRect(x + 24, y + 11, 10, 4); 

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        if (selectedChar == 2 && solarOrbsLeft > 0) {
            long time = System.currentTimeMillis();
            g2.setColor(new Color(255, 200, 0, 30));
            g2.fillOval(x-20, y-20, 80, 80);
            g2.setColor(new Color(255, 200, 0, 100));
            g2.drawOval(x-20, y-20, 80, 80);

            for(int i=0; i<solarOrbsLeft; i++) {
                double angle = (time / 150.0) + (i * Math.PI); 
                int ox = (int)(x + 15 + Math.cos(angle) * 55); 
                int oy = (int)(y + 20 + Math.sin(angle) * 55);
                g2.setColor(Color.WHITE); g2.fillOval(ox, oy, 14, 14);
                g2.setColor(GOLD_ORANGE); g2.fillOval(ox-2, oy-2, 18, 18);
                g2.setColor(new Color(255, 200, 50, 100)); g2.fillOval(ox-6, oy-6, 26, 26);
            }
        }
    }

    private void drawHUD(Graphics2D g2) {
        g2.setColor(UI_BG);
        g2.fillRoundRect(10, 10, 220, 70, 15, 15);
        g2.setColor(NEON_CYAN);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(10, 10, 220, 70, 15, 15);
        
        g2.setColor(NEON_CYAN);
        g2.setFont(hudFont);
        g2.drawString("SCORE: " + score, 25, 35);
        
        if (combo > 1) {
            g2.setFont(comboFont);
            g2.setColor(combo > 10 ? NEON_PINK : GOLD_ORANGE);
            g2.drawString(combo + "x", 25, 70);
        }

        long now = System.currentTimeMillis();
        int barWidth = 250;
        int barX = (WIDTH - barWidth) / 2;
        int barY = 30;
        
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRoundRect(barX-4, barY-4, barWidth+8, 18, 10, 10);
        g2.setColor(Color.WHITE);
        g2.drawRoundRect(barX-4, barY-4, barWidth+8, 18, 10, 10);
        
        if (now < skillCooldownEnd) {
            float ratio = (float)(skillCooldownEnd - now) / totalCooldownTime;
            g2.setColor(new Color(255, 50, 50));
            g2.fillRoundRect(barX, barY, (int)(ratio * barWidth), 10, 8, 8);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.setColor(Color.WHITE);
            g2.drawString("RECHARGING...", barX + 90, barY + 9);
        } else {
            g2.setColor(NEON_GREEN);
            if (selectedChar == 2 && solarOrbsLeft > 0) g2.setColor(Color.GRAY); 
            g2.fillRoundRect(barX, barY, barWidth, 10, 8, 8);
            g2.setColor(new Color(255, 255, 255, 150)); 
            g2.fillRect(barX, barY, barWidth, 5);
            g2.setFont(new Font("Arial", Font.BOLD, 10));
            g2.setColor(Color.BLACK);
            g2.drawString("SKILL READY [PRESS W]", barX + 60, barY + 9);
        }
    }

    private void drawCyberGrid(Graphics2D g2) {
        g2.setColor(new Color(50, 0, 100, 100));
        int gridOffset = (animFrame * 4) % 40;
        for(int x = 100; x < WIDTH-100; x+=40) g2.drawLine(x, 0, x, HEIGHT);
        for(int y = 0; y < HEIGHT; y+=40) g2.drawLine(100, y + gridOffset - 40, WIDTH-100, y + gridOffset - 40);
    }

    private Color getCharColor() {
        if (selectedChar == 0) return PHANTOM_PURPLE;
        if (selectedChar == 1) return NEON_CYAN;
        if (selectedChar == 2) return GOLD_ORANGE;
        return Color.LIGHT_GRAY; 
    }

    // --- MENUS ---
    private void drawTitle(Graphics2D g2) {
        drawMenuBg(g2);
        g2.setColor(NEON_CYAN); g2.setFont(titleFont);
        drawCenteredString(g2, "TOWER RUN", 200);
        g2.setColor(NEON_PINK); g2.setFont(new Font("Verdana", Font.BOLD, 24));
        drawCenteredString(g2, "GOD MODE EDITION", 260);
        float pulse = (float)Math.abs(Math.sin(animFrame * 0.05));
        g2.setColor(new Color(1f, 1f, 1f, pulse));
        g2.setFont(menuFont);
        drawCenteredString(g2, "[ PRESS ENTER TO START ]", 450);
    }

    private void drawMapSelect(Graphics2D g2) {
        drawMenuBg(g2);
        g2.setColor(Color.WHITE); g2.setFont(titleFont.deriveFont(40f));
        drawCenteredString(g2, "SELECT MAP", 150);
        String[] maps = {"JUNGLE THEME", "NEON DARK", "COMBINED MODE"};
        drawMenuList(g2, maps, selectedMap);
    }

    private void drawSelect(Graphics2D g2) {
        drawMenuBg(g2);
        g2.setColor(Color.WHITE); g2.setFont(titleFont.deriveFont(40f));
        drawCenteredString(g2, "CHOOSE CLASS", 120);
        drawMenuList(g2, characters, selectedChar);
        g2.setColor(UI_BG);
        g2.fillRoundRect(80, 580, 440, 120, 20, 20);
        g2.setColor(NEON_CYAN);
        g2.drawRoundRect(80, 580, 440, 120, 20, 20);
        g2.setColor(Color.WHITE);
        g2.setFont(descFont);
        String[] lines = charDescriptions[selectedChar].split("\n");
        for(int i=0; i<lines.length; i++) g2.drawString(lines[i], 100, 610 + (i*25));
    }
    
    private void drawMenuBg(Graphics2D g2) {
        g2.setPaint(new GradientPaint(0,0, new Color(10,10,40), WIDTH, HEIGHT, Color.BLACK));
        g2.fillRect(0,0,WIDTH,HEIGHT);
        drawCyberGrid(g2);
        for(Star s : stars) s.update(0.5f);
        for(Star s : stars) s.draw(g2);
    }

    private void drawMenuList(Graphics2D g2, String[] items, int selected) {
        g2.setFont(menuFont);
        for (int i = 0; i < items.length; i++) {
            int yPos = 220 + (i * 70);
            if (i == selected) {
                g2.setColor(new Color(0, 255, 255, 50));
                g2.fillRoundRect(150, yPos - 35, 300, 50, 10, 10);
                g2.setColor(NEON_CYAN);
                g2.drawRoundRect(150, yPos - 35, 300, 50, 10, 10);
                g2.drawString("> " + items[i], 170, yPos);
            } else {
                g2.setColor(Color.GRAY);
                g2.drawString(items[i], 170, yPos);
            }
        }
    }
    
    private void drawCenteredString(Graphics2D g, String text, int y) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, y);
    }

    private void drawPause(Graphics2D g2) {
        g2.setColor(new Color(0,0,0,200)); g2.fillRect(0,0,WIDTH,HEIGHT);
        g2.setColor(NEON_CYAN); g2.setFont(titleFont);
        drawCenteredString(g2, "PAUSED", 400);
    }

    private void drawOver(Graphics2D g2) {
        g2.setColor(new Color(20, 0, 0, 240)); g2.fillRect(0,0,WIDTH,HEIGHT);
        g2.setColor(Color.RED); g2.setFont(titleFont);
        drawCenteredString(g2, "GAME OVER", 300);
        g2.setColor(Color.WHITE); g2.setFont(gameFont);
        drawCenteredString(g2, "FINAL SCORE: " + score, 400);
        long now = System.currentTimeMillis();
        if(now > deathTime + 3000) {
            g2.setColor(new Color(1f, 1f, 1f, (float)Math.abs(Math.sin(animFrame * 0.1))));
            drawCenteredString(g2, "PRESS ENTER TO RESTART", 500);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_ESCAPE) System.exit(0);
        if (code == KeyEvent.VK_TAB) {
            if (currentState == PLAY_STATE) currentState = PAUSE_STATE;
            else if (currentState == PAUSE_STATE) currentState = PLAY_STATE;
        }
        if (currentState == TITLE_STATE && code == KeyEvent.VK_ENTER) currentState = MAP_SELECT_STATE;
        else if (currentState == MAP_SELECT_STATE) {
            if (code == KeyEvent.VK_UP) selectedMap = (selectedMap > 0) ? selectedMap - 1 : 2;
            if (code == KeyEvent.VK_DOWN) selectedMap = (selectedMap < 2) ? selectedMap + 1 : 0;
            if (code == KeyEvent.VK_ENTER) currentState = CHAR_SELECT_STATE;
        } else if (currentState == CHAR_SELECT_STATE) {
            if (code == KeyEvent.VK_UP) selectedChar = (selectedChar > 0) ? selectedChar - 1 : 3;
            if (code == KeyEvent.VK_DOWN) selectedChar = (selectedChar < 3) ? selectedChar + 1 : 0;
            if (code == KeyEvent.VK_ENTER) { resetGame(); currentState = PLAY_STATE; }
        } else if (currentState == PLAY_STATE) {
            if (code == KeyEvent.VK_SPACE) {
                playSound("jump.wav");
                if (!isJumping) {
                    isJumping = true; xSpeed = onLeftWall ? jumpPower : -jumpPower;
                    for(int i=0; i<5; i++) particles.add(new Particle(ninjaX + ninjaWidth/2, ninjaY + ninjaHeight, Color.WHITE));
                } else {
                    xSpeed = -xSpeed;
                    for(int i=0; i<5; i++) particles.add(new Particle(ninjaX + ninjaWidth/2, ninjaY + ninjaHeight/2, Color.LIGHT_GRAY));
                }
            }
            if (code == KeyEvent.VK_W && System.currentTimeMillis() > skillCooldownEnd) {
                activateSkill();
            }
        } else if (currentState == OVER_STATE && code == KeyEvent.VK_ENTER) {
            if (System.currentTimeMillis() > deathTime + 3000) { 
                currentState = TITLE_STATE; 
                fadeMusicIn();
            }
        }
    }

    private void activateSkill() {
        long now = System.currentTimeMillis();
        if (selectedChar == 2 && solarOrbsLeft > 0) return; 

        playSound("skill.wav");
        String text = "SKILL ACTIVATED!"; Color c = Color.WHITE;
        
        if (selectedChar == 0) { 
            isSkillActive = true; skillActiveTimer = now + 4000; skillCooldownEnd = now + 15000; 
            text = "PHANTOM WALK!"; c = PHANTOM_PURPLE;
        }
        else if (selectedChar == 1) { 
            isSkillActive = true; skillActiveTimer = now + 4000; skillCooldownEnd = now + 20000; 
            text = "STATIC FIELD!"; c = NEON_CYAN;
        }
        else if (selectedChar == 2) { 
            solarOrbsLeft = 2; skillCooldownEnd = now + 15000; 
            text = "SHIELDS ACTIVE!"; c = GOLD_ORANGE;
        }
        else if (selectedChar == 3) { 
            hasRevived = false; skillCooldownEnd = now + 25000; 
            text = "SYSTEM READY!"; c = NEON_GREEN;
        }
        floatingTexts.add(new FloatingText(text, ninjaX, ninjaY - 50, c));
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame f = new JFrame("Tower Run: God Mode");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new Game());
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        ((Game)f.getContentPane().getComponent(0)).start();
    }
}