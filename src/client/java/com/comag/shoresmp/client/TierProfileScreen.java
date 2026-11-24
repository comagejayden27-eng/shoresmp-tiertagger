package com.comag.shoresmp.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;

// The player profile card: avatar, name, rank title, region, position and tiers
public class TierProfileScreen extends Screen {

    private final Screen parent;
    private final String name;
    private int left, top, panelW, panelH;

    public TierProfileScreen(Screen parent, String name) {
        super(Component.literal(name));
        this.parent = parent;
        this.name = name;
    }

    @Override
    protected void init() {
        panelW = Math.min(this.width - 20, 280);
        panelH = Math.min(this.height - 16, 236);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(left + panelW - 28, top + 8, 20, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelW, top + panelH, 0xF00F1119);
        outline(g, left, top, left + panelW, top + panelH, 0xFF2A2F40);
    }

    // A 1-pixel border
    private static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    // The player's face: their real skin if they are on this server, otherwise a default skin.
    // Also used by the leaderboard rows.
    // If PlayerFaceExtractor or DefaultPlayerSkin show red, delete everything inside this method
    // and use this one line instead:  g.fill(x, y, x + size, y + size, 0xFF3A4260);
    public static void drawFace(GuiGraphicsExtractor g, String name, int x, int y, int size) {
        PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfo(name);
            if (info != null) skin = info.getSkin();
        }
        PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
    }

    private void drawCentered(GuiGraphicsExtractor g, Component text, int centerX, int y, int color) {
        g.text(this.font, text, centerX - this.font.width(text) / 2, y, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int cx = left + panelW / 2;
        int points = TierData.totalPoints(name);
        int rank = TierData.rankFor(name);

        // Avatar (gold ring for #1)
        int size = 56;
        int ax = cx - size / 2;
        int ay = top + 14;
        g.fill(ax - 3, ay - 3, ax + size + 3, ay + size + 3, rank == 1 ? 0xFFE5B84A : 0xFF2B3347);
        drawFace(g, name, ax, ay, size);

        // Name
        drawCentered(g, Component.literal(name).withStyle(ChatFormatting.BOLD), cx, top + 78, 0xFFCFD8F0);

        // Rank title pill
        Component title = Component.literal(TierData.titleName(points));
        int pillW = this.font.width(title) + 20;
        g.fill(cx - pillW / 2, top + 91, cx + pillW / 2, top + 105, 0xFF262B3B);
        drawCentered(g, title, cx, top + 94, 0xFF000000 | TierData.titleColor(points));

        // Region
        drawCentered(g, Component.literal(TierData.regionFor(name)), cx, top + 112, 0xFF5A6482);

        // Position box with the rank banner
        int bx1 = left + 16;
        int bx2 = left + panelW - 16;
        g.fill(bx1, top + 128, bx2, top + 156, 0xFF151A28);
        outline(g, bx1, top + 128, bx2, top + 156, 0xFF262B3B);
        g.fill(bx1 + 1, top + 129, bx1 + 52, top + 155, 0xFF000000 | TierData.rankColor(rank));
        g.text(this.font, Component.literal("#" + rank).withStyle(ChatFormatting.BOLD),
                bx1 + 10, top + 138, rank <= 3 ? 0xFF1A1A1A : 0xFFCFD8F0);
        g.text(this.font, Component.literal("OVERALL").withStyle(ChatFormatting.BOLD),
                bx1 + 60, top + 138, 0xFFFFFFFF);
        g.text(this.font, Component.literal("(" + points + " points)"),
                bx1 + 60 + this.font.width("OVERALL") + 6, top + 138, 0xFF8A92AA);

        // Tiers header, with a "Retired" callout on the right if the player has a retired tier
        g.text(this.font, Component.literal("TIERS"), left + 16, top + 164, 0xFF8A92AA);
        TierData.Entry retired = TierData.retiredEntry(name);
        if (retired != null) {
            Component line1 = Component.literal("Retired " + retired.tier()).withStyle(ChatFormatting.BOLD);
            Component line2 = Component.literal(TierData.pointsFor(retired.tier()) + " points");
            int rx = left + panelW - 16;
            g.text(this.font, line1, rx - this.font.width(line1), top + 158, 0xFFFFFFFF);
            g.text(this.font, line2, rx - this.font.width(line2), top + 168, 0xFF8A92AA);
        }

        // Gamemode slots
        List<String> order = TierConfig.get().priority;
        int n = order.size();
        int slotW = (panelW - 32) / n;
        for (int i = 0; i < n; i++) {
            int mode = TierData.modeIndex(order.get(i));
            if (mode < 0) continue;
            int sx = left + 16 + i * slotW + slotW / 2;
            int sy = top + 178;
            TierData.Entry e = TierData.entryFor(name, mode);

            g.fill(sx - 12, sy, sx + 12, sy + 24, e == null ? 0xFF121622 : 0xFF1E2438);
            outline(g, sx - 12, sy, sx + 12, sy + 24, e == null ? 0xFF2E3448 : 0xFF4A5170);

            Component label;
            int labelColor;
            if (e == null) {
                label = Component.literal("-");
                labelColor = 0xFF6B7390;
            } else {
                g.text(this.font, TierData.modeIcon(mode), sx - 4, sy + 8, 0xFFFFFFFF);
                label = Component.literal(TierData.label(e));
                labelColor = 0xFF000000 | TierData.colorOf(e);
            }
            int lw = this.font.width(label) + 10;
            g.fill(sx - lw / 2, sy + 28, sx + lw / 2, sy + 40, 0xFF262B3B);
            drawCentered(g, label, sx, sy + 30, labelColor);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}