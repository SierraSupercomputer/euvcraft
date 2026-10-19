package com.euvcraft.lithmod.mc;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Supplemental source audit; runtime invariance checks cover actual packets and tooltips. */
class M1BoundaryResourceTest {
    private static final Path MC = Path.of("src/main/java/com/euvcraft/lithmod/mc");

    private static List<Path> sources() throws IOException {
        assertTrue(Files.isRegularFile(MC.resolve("EUVCRAFTMod.java")), "Production source root is required");
        try (var walk = Files.walk(MC)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !MC.relativize(p).startsWith("gametest"))
                    .sorted().toList();
            assertTrue(files.size() > 20, "Audit must cover the real production tree");
            return files;
        }
    }

    @Test void rawContentsAccessIsDiagnosticOnly() throws IOException {
        List<String> violations = new ArrayList<>();
        Pattern call = Pattern.compile("\\bFoupItems\\s*\\.\\s*contentsOf\\s*\\(");
        for (Path file : sources()) {
            if (file.equals(MC.resolve("FoupItems.java")) || file.equals(MC.resolve("FoupDebugCommand.java"))) continue;
            if (call.matcher(maskComments(Files.readString(file))).find()) violations.add(MC.relativize(file).toString());
        }
        assertEquals(List.of(), violations, "Raw accessor calls outside diagnostics");
    }

    @Test void productionLoggerArgumentsDoNotSerializePrivateLots() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : sources()) {
            if (file.equals(MC.resolve("FoupDebugCommand.java"))) continue; // privileged explicit inspection
            int count = privateLogCalls(Files.readString(file));
            if (count != 0) violations.add(MC.relativize(file) + ": " + count + " suspicious calls");
        }
        assertEquals(List.of(), violations, "Routine logger arguments must not serialize private lot data");
    }

    @Test void auditDetectsNestedAndWholeObjectSerialization() {
        assertEquals(1, privateLogCalls("FoupContents batch; LOGGER.info(\"{}\", wrap(batch.wafers()));"));
        assertEquals(1, privateLogCalls("Wafer sample; logger.warn(\"{}\", sample);"));
        assertEquals(1, privateLogCalls("RunSpec contract; LOG.error(\"bad \" + contract);"));
        assertEquals(1, privateLogCalls("FoupContents lot; LOGGER.info(\"{}\", (lot));"));
        assertEquals(1, privateLogCalls("LOGGER.debug(\"{}\", wafer.cumulativeRoll());"));
        assertEquals(1, privateLogCalls("LOGGER.warn(\"{}\", nested(foo(), lot.wafers()));"));
    }

    @Test void auditPermitsPublicCountsAndIgnoresComments() {
        assertEquals(0, privateLogCalls("FoupContents lot; LOGGER.info(\"{} {}\", lot.size(), lot.capacity());"));
        assertEquals(0, privateLogCalls("LOGGER.info(\"{} {}\", passing, failing);"));
        assertEquals(0, privateLogCalls("LOGGER.warn(\"The hidden rolls were never filled in\", item);"));
        assertEquals(0, privateLogCalls("// LOGGER.info(\"{}\", wafer.cumulativeRoll());\nLOGGER.info(\"{}\", total);"));
        assertEquals(0, privateLogCalls("/* LOGGER.warn(\"{}\", lot.wafers()); */"));
        assertEquals(0, privateLogCalls("LOGGER.info(\"https://example.invalid/a\", publicCount);"));
    }

    /** A bounded lexical audit of logger invocations, not a general Java information-flow proof. */
    private static int privateLogCalls(String source) {
        String clean = maskComments(source);
        Set<String> privateNames = new HashSet<>(Set.of("lot", "contents", "wafer", "spec"));
        var declarations = Pattern.compile("\\b(?:FoupContents|Wafer|RunSpec)\\s+([A-Za-z_$][\\w$]*)").matcher(clean);
        while (declarations.find()) privateNames.add(declarations.group(1));
        var starts = Pattern.compile("\\b[\\w$]*(?:logger|log)[\\w$]*\\s*\\.\\s*(?:trace|debug|info|warn|error)\\s*\\(", Pattern.CASE_INSENSITIVE).matcher(clean);
        int bad = 0;
        while (starts.find()) {
            int open = starts.end()-1;
            int end = matchingClose(clean, open);
            String invocation = maskStrings(clean.substring(open+1, end));
            // These projections are explicitly public. Private roots remaining in any other
            // expression are suspicious, including bare objects, concatenations and nested calls.
            invocation = invocation.replaceAll("\\b[\\w$]+\\s*\\.\\s*(?:size|capacity|lotLayer|publicView)\\s*\\(\\s*\\)", "PUBLIC");
            boolean leak = Pattern.compile("\\b(?:cumulativeRoll|wafers|flags|run|lotId)\\s*\\(").matcher(invocation).find();
            for (String name : privateNames) leak |= Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(invocation).find();
            if (leak) bad++;
        }
        return bad;
    }

    private static int matchingClose(String text, int open) {
        String lexical = maskStrings(text);
        int depth = 0;
        for (int i=open; i<lexical.length(); i++) {
            if (lexical.charAt(i)=='(') depth++;
            if (lexical.charAt(i)==')' && --depth==0) return i;
        }
        throw new AssertionError("Unbalanced logger call in audited source");
    }

    private static String maskStrings(String text) { return lexicalMask(text, false); }
    private static String maskComments(String text) { return lexicalMask(text, true); }

    /** Preserves offsets/newlines; quoted slashes must not start comments. */
    private static String lexicalMask(String text, boolean commentsOnly) {
        StringBuilder out = new StringBuilder(text);
        boolean line=false, block=false, escaped=false;
        char quote=0;
        for (int i=0; i<text.length(); i++) {
            char c=text.charAt(i), next=i+1<text.length()?text.charAt(i+1):0;
            if (line) { if(c=='\n') line=false; else out.setCharAt(i,' '); continue; }
            if (block) {
                if(c!='\n' && c!='\r') out.setCharAt(i,' ');
                if(c=='*' && next=='/') { out.setCharAt(++i,' '); block=false; }
                continue;
            }
            if (quote!=0) {
                if(!commentsOnly && c!='\n' && c!='\r') out.setCharAt(i,' ');
                if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c==quote) quote=0;
                continue;
            }
            if(c=='"' || c=='\'') { quote=c; if(!commentsOnly) out.setCharAt(i,' '); continue; }
            if(commentsOnly && c=='/' && (next=='/' || next=='*')) {
                line=next=='/'; block=next=='*'; out.setCharAt(i,' '); out.setCharAt(++i,' ');
            }
        }
        return out.toString();
    }
}
