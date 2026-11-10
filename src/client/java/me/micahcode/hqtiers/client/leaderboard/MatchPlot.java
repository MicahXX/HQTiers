package me.micahcode.hqtiers.client.leaderboard;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

record MatchPlot(int[] indices, int matchCount) {
    static MatchPlot of(List<RatingHistoryClient.Point> points, int width) {
        int columns = Math.max(1, width);
        int count = points.size();
        if (count <= columns * 2L) {
            return new MatchPlot(IntStream.range(0, count).toArray(), count);
        }
        int[] indices = new int[2 * columns + 2];
        int size = 1;
        for (int column = 0; column < columns; column++) {
            int start = 1 + (int) ((long) column * (count - 2) / columns);
            int end = 1 + (int) ((long) (column + 1) * (count - 2) / columns);
            int min = start, max = start;
            for (int i = start + 1; i < end; i++) {
                if (points.get(i).elo() < points.get(min).elo()) min = i;
                if (points.get(i).elo() > points.get(max).elo()) max = i;
            }
            indices[size++] = Math.min(min, max);
            if (min != max) indices[size++] = Math.max(min, max);
        }
        indices[size++] = count - 1;
        return new MatchPlot(Arrays.copyOf(indices, size), count);
    }

    int x(int sample, int width) {
        return matchCount == 1 ? width / 2 : (int) ((long) indices[sample] * width / (matchCount - 1));
    }
}
