package com.flashnote.java.ui.media;

final class PdfPreviewRenderSpec {
    static final int MAX_DIMENSION_PX = 2048;

    private PdfPreviewRenderSpec() {
    }

    static Size fit(int pageWidth, int pageHeight) {
        int safeWidth = Math.max(pageWidth, 1);
        int safeHeight = Math.max(pageHeight, 1);
        int longestSide = Math.max(safeWidth, safeHeight);
        if (longestSide <= MAX_DIMENSION_PX) {
            return new Size(safeWidth, safeHeight);
        }
        float scale = (float) MAX_DIMENSION_PX / (float) longestSide;
        int scaledWidth = Math.max(1, Math.round(safeWidth * scale));
        int scaledHeight = Math.max(1, Math.round(safeHeight * scale));
        return new Size(scaledWidth, scaledHeight);
    }

    static final class Size {
        final int width;
        final int height;

        Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
