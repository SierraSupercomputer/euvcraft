package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.*;
import com.euvcraft.lithmod.mc.compat.TooltipLineWrapping;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class TooltipLineWrappingTest {
    @Test void asyncIndexPreservesFullTextWithoutTouchingFont() {
        var text = FormattedText.of("Encoded Logistics description with searchable words");
        var splitter = new StringSplitter((codePoint, style) -> {
            fail("Background indexing must not measure glyphs");
            return 0;
        });
        var lines = CompletableFuture.supplyAsync(() ->
                TooltipLineWrapping.wrap(splitter, text, 12, Style.EMPTY, false)).join();
        assertEquals(1, lines.size());
        assertSame(text, lines.getFirst());
        assertEquals("Encoded Logistics description with searchable words", lines.getFirst().getString());
    }

    @Test void renderThreadRetainsActualWidthWrapping() {
        AtomicInteger measurements = new AtomicInteger();
        var splitter = new StringSplitter((codePoint, style) -> {
            measurements.incrementAndGet();
            return 1;
        });
        var text = FormattedText.of("one two three four");
        var expected = splitter.splitLines(text, 6, Style.EMPTY).stream().map(FormattedText::getString).toList();
        measurements.set(0);
        var actual = TooltipLineWrapping.wrap(splitter, text, 6, Style.EMPTY, true);
        assertTrue(measurements.get() > 0);
        assertTrue(actual.size() > 1);
        assertEquals(expected, actual.stream().map(FormattedText::getString).toList());
    }
}
