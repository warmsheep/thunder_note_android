package com.flashnote.java.ui.media;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PdfPreviewRenderSpecTest {

    @Test
    public void fit_keepsSmallPageSize() {
        PdfPreviewRenderSpec.Size size = PdfPreviewRenderSpec.fit(800, 1200);

        assertEquals(800, size.width);
        assertEquals(1200, size.height);
    }

    @Test
    public void fit_scalesDownLargePageToMaxDimension() {
        PdfPreviewRenderSpec.Size size = PdfPreviewRenderSpec.fit(4961, 7016);

        assertTrue(size.width <= PdfPreviewRenderSpec.MAX_DIMENSION_PX);
        assertTrue(size.height <= PdfPreviewRenderSpec.MAX_DIMENSION_PX);
        assertEquals(PdfPreviewRenderSpec.MAX_DIMENSION_PX, size.height);
    }

    @Test
    public void fit_guardsZeroOrNegativeDimensions() {
        PdfPreviewRenderSpec.Size size = PdfPreviewRenderSpec.fit(0, -20);

        assertEquals(1, size.width);
        assertEquals(1, size.height);
    }
}
