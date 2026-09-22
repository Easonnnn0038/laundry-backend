package com.laundry.api.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeUtilTest {
    @Test
    void generatesVectorBarcodeWithoutDroppingLeadingZeros() {
        String first = BarcodeUtil.generateCode128SvgDataUri("001091500101");
        String second = BarcodeUtil.generateCode128SvgDataUri("101091500101");
        assertTrue(first.startsWith("data:image/svg+xml;base64,"));
        String svg = new String(Base64.getDecoder().decode(first.substring(first.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("shape-rendering=\"crispEdges\""));
        assertTrue(svg.contains("<rect x="));
        assertNotEquals(first, second);
    }
}
