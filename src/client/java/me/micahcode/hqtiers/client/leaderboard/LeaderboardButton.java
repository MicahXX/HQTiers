package me.micahcode.hqtiers.client.leaderboard;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class LeaderboardButton {
    private LeaderboardButton() {}

    static Button create(String label, int x, int y, int width, int height, boolean selected, Runnable action) {
        Button button = Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, width, height).build();
        button.active = !selected;
        return button;
    }
}
