package com.euvcraft.lithmod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ImportCheckerTest {
    @TempDir Path directory;

    private int check(String importLine) throws Exception {
        Path source = directory.resolve("src/main/java/com/euvcraft/lithmod/core/Probe.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package com.euvcraft.lithmod.core;\n" + importLine + "\nclass Probe {}\n");
        Path classes = Path.of(ImportChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process = new ProcessBuilder(javaExecutable.toString(), "-cp", classes.toString(), ImportChecker.class.getName())
            .directory(directory.toFile()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "import checker timed out");
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(output.contains("Core import check passed") || output.contains("CORE IMPORT CHECK FAILED"), output);
            return process.exitValue();
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    @Test void rejectsMojangImports() throws Exception { assertEquals(1, check("import com.mojang.serialization.Codec;")); }
    @Test void rejectsNettyImports() throws Exception { assertEquals(1, check("import io.netty.buffer.ByteBuf;")); }
    @Test void rejectsStaticGameImports() throws Exception { assertEquals(1, check("import static net.minecraft.world.item.Items.STONE;")); }
    @Test void acceptsStandardLibraryImports() throws Exception { assertEquals(0, check("import java.util.List;")); }
}
