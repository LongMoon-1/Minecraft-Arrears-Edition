import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.util.glu.GLU;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

public class Game {

    static int textureGrass = -1;
    static int textureStone = -1;

    static class Block {
        float r, g, b;
        Block(float r, float g, float b) { this.r = r; this.g = g; this.b = b; }
    }

    static class Vec3 {
        float x, y, z;
        Vec3() { this(0, 0, 0); }
        Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        Vec3 add(Vec3 v) { return new Vec3(x + v.x, y + v.y, z + v.z); }
        Vec3 scale(float s) { return new Vec3(x * s, y * s, z * s); }
        float length() { return (float) Math.sqrt(x * x + y * y + z * z); }
        Vec3 normalize() { float len = length(); return len > 0 ? new Vec3(x / len, y / len, z / len) : new Vec3(); }
    }

    static class Player {
        Vec3 pos, velocity;
        float yaw, pitch;
        boolean onGround;
        float eyeHeight = 1.6f, width = 0.6f, height = 1.8f;

        Player(float x, float y, float z) {
            pos = new Vec3(x, y, z);
            velocity = new Vec3();
            yaw = 0;
            pitch = 0;
            onGround = false;
        }

        Vec3 getEyePos() { return new Vec3(pos.x, pos.y + eyeHeight, pos.z); }

        Vec3 getLookDir() {
            float cy = (float) Math.cos(Math.toRadians(yaw));
            float sy = (float) Math.sin(Math.toRadians(yaw));
            float cp = (float) Math.cos(Math.toRadians(pitch));
            float sp = (float) Math.sin(Math.toRadians(pitch));
            return new Vec3(sy * cp, -sp, -cy * cp);
        }
    }

    static class World {
        static final int SIZE = 128;
        static final int PLATFORM_Y = 32;
        byte[][][] blocks;
        Block grassBlock = new Block(0.25f, 0.75f, 0.25f);
        Block stoneBlock = new Block(0.5f, 0.5f, 0.5f);

        World() {
            blocks = new byte[SIZE][SIZE][SIZE];
        }

        void generate() {
            int half = 50, cx = SIZE / 2, cz = SIZE / 2;
            for (int x = cx - half; x < cx + half; x++)
                for (int z = cz - half; z < cz + half; z++)
                    blocks[x][PLATFORM_Y][z] = 1;
        }

        byte getBlock(int x, int y, int z) {
            if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return 0;
            return blocks[x][y][z];
        }

        void setBlock(int x, int y, int z, byte type) {
            if (x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE)
                blocks[x][y][z] = type;
        }

        boolean isSolid(int x, int y, int z) { return getBlock(x, y, z) != 0; }

        Block getBlockType(int x, int y, int z) {
            byte id = getBlock(x, y, z);
            if (id == 1) return grassBlock;
            if (id == 2) return stoneBlock;
            return null;
        }

        int getBlockTexture(int x, int y, int z) {
            byte id = getBlock(x, y, z);
            if (id == 1) return textureGrass;
            if (id == 2) return textureStone;
            return -1;
        }
    }

    static class Camera {
        void apply(Player p) {
            glLoadIdentity();
            Vec3 eye = p.getEyePos();
            Vec3 look = p.getLookDir();
            GLU.gluLookAt(eye.x, eye.y, eye.z, eye.x + look.x, eye.y + look.y, eye.z + look.z, 0, 1, 0);
        }
    }

    static int loadTexture(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return -1;
            BufferedImage img = ImageIO.read(f);
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);

            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = pixels[y * w + x];
                    buf.put((byte) ((pixel >> 16) & 0xFF));
                    buf.put((byte) ((pixel >> 8) & 0xFF));
                    buf.put((byte) (pixel & 0xFF));
                    buf.put((byte) ((pixel >> 24) & 0xFF));
                }
            }
            buf.flip();

            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    static class Renderer {
        World world;

        Renderer(World w) { this.world = w; }

        void render(RaycastResult target) {
            glClearColor(0.53f, 0.81f, 0.92f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glEnable(GL_LIGHTING);
            glEnable(GL_LIGHT0);
            glEnable(GL_COLOR_MATERIAL);
            glLight(GL_LIGHT0, GL_POSITION, asFloatBuffer(new float[]{0, 100, 100, 1}));
            glLight(GL_LIGHT0, GL_AMBIENT, asFloatBuffer(new float[]{0.5f, 0.5f, 0.5f, 1}));
            glLight(GL_LIGHT0, GL_DIFFUSE, asFloatBuffer(new float[]{0.7f, 0.7f, 0.7f, 1}));

            glEnable(GL_TEXTURE_2D);

            for (int x = 0; x < World.SIZE; x++)
                for (int y = 0; y < World.SIZE; y++)
                    for (int z = 0; z < World.SIZE; z++)
                        if (world.getBlock(x, y, z) != 0 && shouldRenderFace(x, y, z))
                            renderBlock(x, y, z);

            glDisable(GL_TEXTURE_2D);

            if (target != null && target.hit)
                renderBlockOutline(target.blockX, target.blockY, target.blockZ);

            glDisable(GL_LIGHTING);
            glDisable(GL_LIGHT0);
            glDisable(GL_COLOR_MATERIAL);

            drawCrosshair();
            drawText(10, 10, "beta 1.1");
        }

        boolean shouldRenderFace(int bx, int by, int bz) {
            return !world.isSolid(bx + 1, by, bz) || !world.isSolid(bx - 1, by, bz) ||
                    !world.isSolid(bx, by + 1, bz) || !world.isSolid(bx, by - 1, bz) ||
                    !world.isSolid(bx, by, bz + 1) || !world.isSolid(bx, by, bz - 1);
        }

        void renderBlock(int x, int y, int z) {
            Block b = world.getBlockType(x, y, z);
            int tex = world.getBlockTexture(x, y, z);

            if (tex >= 0) {
                glBindTexture(GL_TEXTURE_2D, tex);
                glColor3f(1, 1, 1);
            } else if (b != null) {
                glBindTexture(GL_TEXTURE_2D, 0);
            } else {
                return;
            }

            glPushMatrix();
            glTranslatef(x, y, z);
            float s = 0.5f, br = 1.0f, md = 0.85f, dk = 0.7f;

            if (!world.isSolid(x, y + 1, z)) {
                if (tex < 0 && b != null) glColor3f(b.r * br, b.g * br, b.b * br);
                glBegin(GL_QUADS);
                glNormal3f(0, 1, 0);
                glTexCoord2f(0, 0); glVertex3f(-s, s, -s);
                glTexCoord2f(1, 0); glVertex3f(s, s, -s);
                glTexCoord2f(1, 1); glVertex3f(s, s, s);
                glTexCoord2f(0, 1); glVertex3f(-s, s, s);
                glEnd();
            }
            if (!world.isSolid(x, y - 1, z)) {
                if (tex < 0 && b != null) glColor3f(b.r * dk, b.g * dk, b.b * dk);
                glBegin(GL_QUADS);
                glNormal3f(0, -1, 0);
                glTexCoord2f(0, 0); glVertex3f(-s, -s, s);
                glTexCoord2f(1, 0); glVertex3f(s, -s, s);
                glTexCoord2f(1, 1); glVertex3f(s, -s, -s);
                glTexCoord2f(0, 1); glVertex3f(-s, -s, -s);
                glEnd();
            }
            if (!world.isSolid(x, y, z + 1)) {
                if (tex < 0 && b != null) glColor3f(b.r * md, b.g * md, b.b * md);
                glBegin(GL_QUADS);
                glNormal3f(0, 0, 1);
                glTexCoord2f(0, 0); glVertex3f(-s, -s, s);
                glTexCoord2f(1, 0); glVertex3f(s, -s, s);
                glTexCoord2f(1, 1); glVertex3f(s, s, s);
                glTexCoord2f(0, 1); glVertex3f(-s, s, s);
                glEnd();
            }
            if (!world.isSolid(x, y, z - 1)) {
                if (tex < 0 && b != null) glColor3f(b.r * md, b.g * md, b.b * md);
                glBegin(GL_QUADS);
                glNormal3f(0, 0, -1);
                glTexCoord2f(0, 0); glVertex3f(s, -s, -s);
                glTexCoord2f(1, 0); glVertex3f(-s, -s, -s);
                glTexCoord2f(1, 1); glVertex3f(-s, s, -s);
                glTexCoord2f(0, 1); glVertex3f(s, s, -s);
                glEnd();
            }
            if (!world.isSolid(x + 1, y, z)) {
                if (tex < 0 && b != null) glColor3f(b.r * md, b.g * md, b.b * md);
                glBegin(GL_QUADS);
                glNormal3f(1, 0, 0);
                glTexCoord2f(0, 0); glVertex3f(s, -s, s);
                glTexCoord2f(1, 0); glVertex3f(s, -s, -s);
                glTexCoord2f(1, 1); glVertex3f(s, s, -s);
                glTexCoord2f(0, 1); glVertex3f(s, s, s);
                glEnd();
            }
            if (!world.isSolid(x - 1, y, z)) {
                if (tex < 0 && b != null) glColor3f(b.r * md, b.g * md, b.b * md);
                glBegin(GL_QUADS);
                glNormal3f(-1, 0, 0);
                glTexCoord2f(0, 0); glVertex3f(-s, -s, -s);
                glTexCoord2f(1, 0); glVertex3f(-s, -s, s);
                glTexCoord2f(1, 1); glVertex3f(-s, s, s);
                glTexCoord2f(0, 1); glVertex3f(-s, s, -s);
                glEnd();
            }
            glPopMatrix();
        }

        void renderBlockOutline(int x, int y, int z) {
            glPushMatrix();
            glTranslatef(x, y, z);
            glDisable(GL_LIGHTING);
            glDisable(GL_TEXTURE_2D);
            glColor3f(1, 1, 1);
            glLineWidth(2);
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glEnable(GL_POLYGON_OFFSET_LINE);
            glPolygonOffset(-1, -1);
            float s = 0.51f;
            glBegin(GL_QUADS);
            glVertex3f(-s, s, -s); glVertex3f(s, s, -s); glVertex3f(s, s, s); glVertex3f(-s, s, s);
            glVertex3f(-s, -s, s); glVertex3f(s, -s, s); glVertex3f(s, -s, -s); glVertex3f(-s, -s, -s);
            glVertex3f(-s, -s, s); glVertex3f(s, -s, s); glVertex3f(s, s, s); glVertex3f(-s, s, s);
            glVertex3f(s, -s, -s); glVertex3f(-s, -s, -s); glVertex3f(-s, s, -s); glVertex3f(s, s, -s);
            glVertex3f(s, -s, s); glVertex3f(s, -s, -s); glVertex3f(s, s, -s); glVertex3f(s, s, s);
            glVertex3f(-s, -s, -s); glVertex3f(-s, -s, s); glVertex3f(-s, s, s); glVertex3f(-s, s, -s);
            glEnd();
            glDisable(GL_POLYGON_OFFSET_LINE);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glEnable(GL_LIGHTING);
            glPopMatrix();
        }

        void drawCrosshair() {
            setup2D();
            int cx = Display.getWidth() / 2, cy = Display.getHeight() / 2, sz = 8;
            glColor4f(1, 1, 1, 0.8f);
            glLineWidth(2);
            glBegin(GL_LINES);
            glVertex2f(cx - sz, cy); glVertex2f(cx + sz, cy);
            glVertex2f(cx, cy - sz); glVertex2f(cx, cy + sz);
            glEnd();
            restore3D();
        }

        void drawText(int x, int y, String text) {
            setup2D();
            glColor3f(1, 1, 1);
            int cx = x;
            for (int i = 0; i < text.length(); i++) {
                cx += drawCharBig(cx, y, text.charAt(i));
            }
            restore3D();
        }

        void setup2D() {
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, Display.getWidth(), Display.getHeight(), 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_TEXTURE_2D);
        }

        void restore3D() {
            glEnable(GL_DEPTH_TEST);
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
        }

        int drawCharBig(int x, int y, char c) {
            glLineWidth(3);
            glBegin(GL_LINES);
            int s = 2; // scale
            switch (c) {
                case 'b':
                    glVertex2f(x, y+9*s); glVertex2f(x, y);
                    glVertex2f(x, y); glVertex2f(x+3*s, y);
                    glVertex2f(x+3*s, y); glVertex2f(x+3*s, y+4*s);
                    glVertex2f(x+3*s, y+4*s); glVertex2f(x, y+4*s);
                    glVertex2f(x, y+9*s); glVertex2f(x+3*s, y+9*s);
                    glVertex2f(x+3*s, y+9*s); glVertex2f(x+3*s, y+5*s);
                    glVertex2f(x+3*s, y+5*s); glVertex2f(x, y+5*s);
                    glEnd();
                    return 5*s;
                case 'e':
                    glVertex2f(x+4*s, y); glVertex2f(x, y);
                    glVertex2f(x, y); glVertex2f(x, y+9*s);
                    glVertex2f(x, y+9*s); glVertex2f(x+4*s, y+9*s);
                    glVertex2f(x, y+4*s); glVertex2f(x+3*s, y+4*s);
                    glEnd();
                    return 6*s;
                case 't':
                    glVertex2f(x, y); glVertex2f(x+4*s, y);
                    glVertex2f(x+2*s, y); glVertex2f(x+2*s, y+9*s);
                    glEnd();
                    return 6*s;
                case 'a':
                    glVertex2f(x, y+9*s); glVertex2f(x, y+4*s);
                    glVertex2f(x, y+4*s); glVertex2f(x+3*s, y+4*s);
                    glVertex2f(x+3*s, y+4*s); glVertex2f(x+3*s, y+9*s);
                    glVertex2f(x+3*s, y+4*s); glVertex2f(x+3*s, y);
                    glVertex2f(x, y); glVertex2f(x+3*s, y);
                    glEnd();
                    return 5*s;
                case '1':
                    glVertex2f(x+1*s, y); glVertex2f(x+1*s, y+9*s);
                    glVertex2f(x, y+9*s); glVertex2f(x+3*s, y+9*s);
                    glVertex2f(x, y); glVertex2f(x+1*s, y);
                    glEnd();
                    return 5*s;
                case '.':
                    glVertex2f(x, y+8*s); glVertex2f(x+1*s, y+8*s);
                    glVertex2f(x, y+9*s); glVertex2f(x+1*s, y+9*s);
                    glEnd();
                    return 3*s;
                case '0':
                    glVertex2f(x, y); glVertex2f(x+3*s, y);
                    glVertex2f(x+3*s, y); glVertex2f(x+3*s, y+9*s);
                    glVertex2f(x+3*s, y+9*s); glVertex2f(x, y+9*s);
                    glVertex2f(x, y+9*s); glVertex2f(x, y);
                    glEnd();
                    return 5*s;
                default:
                    glEnd();
                    return 5*s;
            }
        }

        FloatBuffer asFloatBuffer(float[] values) {
            FloatBuffer buf = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            buf.put(values);
            buf.flip();
            return buf;
        }
    }

    static class RaycastResult {
        boolean hit;
        int blockX, blockY, blockZ, placeX, placeY, placeZ;
    }

    static RaycastResult raycast(Player player, World world, float maxDist) {
        RaycastResult r = new RaycastResult();
        Vec3 o = player.getEyePos(), d = player.getLookDir();
        float step = 0.05f;
        for (float t = 0; t < maxDist; t += step) {
            Vec3 p = o.add(d.scale(t));
            int bx = Math.round(p.x), by = Math.round(p.y), bz = Math.round(p.z);
            if (world.isSolid(bx, by, bz)) {
                r.hit = true;
                r.blockX = bx; r.blockY = by; r.blockZ = bz;
                Vec3 prev = o.add(d.scale(t - step));
                int px = Math.round(prev.x), py = Math.round(prev.y), pz = Math.round(prev.z);
                r.placeX = px; r.placeY = py; r.placeZ = pz;
                if (px == bx && py == by && pz == bz) {
                    Vec3 diff = new Vec3(p.x - bx, p.y - by, p.z - bz);
                    float ax = Math.abs(diff.x), ay = Math.abs(diff.y), az = Math.abs(diff.z);
                    if (ax > ay && ax > az) r.placeX = bx + (diff.x > 0 ? -1 : 1);
                    else if (ay > ax && ay > az) r.placeY = by + (diff.y > 0 ? -1 : 1);
                    else r.placeZ = bz + (diff.z > 0 ? -1 : 1);
                }
                return r;
            }
        }
        return r;
    }

    static void handleCollision(Player p, World w) {
        float hw = p.width / 2;
        if (collidesAt(p.pos.x + p.velocity.x, p.pos.y, p.pos.z, hw, p.height, w)) p.velocity.x = 0;
        p.pos.x += p.velocity.x;
        if (collidesAt(p.pos.x, p.pos.y + p.velocity.y, p.pos.z, hw, p.height, w)) {
            if (p.velocity.y < 0) p.onGround = true;
            p.velocity.y = 0;
        } else p.onGround = false;
        p.pos.y += p.velocity.y;
        if (collidesAt(p.pos.x, p.pos.y, p.pos.z + p.velocity.z, hw, p.height, w)) p.velocity.z = 0;
        p.pos.z += p.velocity.z;
    }

    static boolean collidesAt(float px, float py, float pz, float hw, float h, World w) {
        for (int dx = -1; dx <= 1; dx += 2)
            for (int dz = -1; dz <= 1; dz += 2) {
                float cx = px + dx * hw * 0.9f, cz = pz + dz * hw * 0.9f;
                int bx = Math.round(cx), bz = Math.round(cz);
                for (int by = Math.round(py); by <= Math.round(py + h - 0.01f); by++)
                    if (w.isSolid(bx, by, bz)) {
                        float bmnX = bx - 0.5f, bmxX = bx + 0.5f;
                        float bmnY = by - 0.5f, bmxY = by + 0.5f;
                        float bmnZ = bz - 0.5f, bmxZ = bz + 0.5f;
                        float pmnX = px - hw, pmxX = px + hw;
                        float pmnY = py, pmxY = py + h;
                        float pmnZ = pz - hw, pmxZ = pz + hw;
                        if (pmxX > bmnX && pmnX < bmxX && pmxY > bmnY && pmnY < bmxY && pmxZ > bmnZ && pmnZ < bmxZ)
                            return true;
                    }
            }
        return false;
    }

    static void handleInput(Player p, World w) {
        float ms = 0.08f, ms2 = 0.15f;
        if (Mouse.isGrabbed()) {
            p.yaw += Mouse.getDX() * ms2;
            p.pitch -= Mouse.getDY() * ms2;
            if (p.pitch > 89) p.pitch = 89;
            if (p.pitch < -89) p.pitch = -89;
        }
        float ry = (float) Math.toRadians(p.yaw);
        float fx = (float) Math.sin(ry), fz = (float) -Math.cos(ry);
        float rx = (float) Math.cos(ry), rz = (float) Math.sin(ry);
        Vec3 mv = new Vec3();
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) mv = mv.add(new Vec3(fx, 0, fz));
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) mv = mv.add(new Vec3(-fx, 0, -fz));
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) mv = mv.add(new Vec3(-rx, 0, -rz));
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) mv = mv.add(new Vec3(rx, 0, rz));
        if (mv.length() > 0) mv = mv.normalize().scale(ms);
        p.velocity.x = mv.x;
        p.velocity.z = mv.z;
        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE) && p.onGround) { p.velocity.y = 0.25f; p.onGround = false; }
        if (!p.onGround) { p.velocity.y -= 0.015f; if (p.velocity.y < -0.5f) p.velocity.y = -0.5f; }

        while (Mouse.next()) {
            if (Mouse.getEventButtonState()) {
                if (Mouse.getEventButton() == 0) { RaycastResult h = raycast(p, w, 5); if (h.hit) w.setBlock(h.blockX, h.blockY, h.blockZ, (byte) 0); }
                if (Mouse.getEventButton() == 1) {
                    RaycastResult h = raycast(p, w, 5);
                    if (h.hit) {
                        int px = Math.round(p.pos.x), py = Math.round(p.pos.y), pz = Math.round(p.pos.z);
                        if (!(h.placeX == px && h.placeY == py && h.placeZ == pz) &&
                                !(h.placeX == px && h.placeY == py + 1 && h.placeZ == pz))
                            w.setBlock(h.placeX, h.placeY, h.placeZ, (byte) 2);
                    }
                }
            }
        }
    }

    static void saveGame(World world, Player player, String path) {
        try {
            File f = new File(path);
            f.getParentFile().mkdirs();
            DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f)));
            out.writeFloat(player.pos.x);
            out.writeFloat(player.pos.y);
            out.writeFloat(player.pos.z);
            out.writeFloat(player.yaw);
            out.writeFloat(player.pitch);
            for (int x = 0; x < World.SIZE; x++)
                for (int y = 0; y < World.SIZE; y++)
                    for (int z = 0; z < World.SIZE; z++)
                        out.writeByte(world.blocks[x][y][z]);
            out.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    static boolean loadGame(World world, Player player, String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return false;
            DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(f)));
            player.pos.x = in.readFloat();
            player.pos.y = in.readFloat();
            player.pos.z = in.readFloat();
            player.yaw = in.readFloat();
            player.pitch = in.readFloat();
            for (int x = 0; x < World.SIZE; x++)
                for (int y = 0; y < World.SIZE; y++)
                    for (int z = 0; z < World.SIZE; z++)
                        world.blocks[x][y][z] = in.readByte();
            in.close();
            return true;
        } catch (Exception e) { return false; }
    }

    public static void main(String[] args) throws LWJGLException {
        File dataDir = new File("data");
        dataDir.mkdirs();
        File materialDir = new File("data/Material");
        materialDir.mkdirs();

        textureGrass = loadTexture("data/Material/grass.png");
        textureStone = loadTexture("data/Material/stone.png");

        Display.setDisplayMode(new DisplayMode(854, 480));
        Display.setTitle("Minecraft Arrears Edition");
        Display.create();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        GLU.gluPerspective(70, (float) 854 / 480, 0.05f, 300);
        glMatrixMode(GL_MODELVIEW);

        World world = new World();
        Player player = new Player(World.SIZE / 2, World.PLATFORM_Y + 2, World.SIZE / 2);
        Camera camera = new Camera();
        Renderer renderer = new Renderer(world);

        String savePath = "data/save/save.dat";
        if (!loadGame(world, player, savePath)) {
            world.generate();
        }

        Mouse.setGrabbed(true);

        RaycastResult target = new RaycastResult();
        boolean running = true;

        while (running && !Display.isCloseRequested()) {
            handleInput(player, world);

            if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
                saveGame(world, player, savePath);
                running = false;
                break;
            }

            handleCollision(player, world);
            if (player.pos.y < -20) {
                player.pos = new Vec3(World.SIZE / 2, World.PLATFORM_Y + 2, World.SIZE / 2);
                player.velocity = new Vec3();
            }
            target = raycast(player, world, 6);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            camera.apply(player);
            renderer.render(target);
            Display.update();
            Display.sync(60);
        }

        Display.destroy();
    }
}