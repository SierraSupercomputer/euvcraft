package com.euvcraft.lithmod.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks that the core package does not import any Minecraft or NeoForge classes.
 * This is run as a verification task during the build.
 */
public class ImportChecker {
    private static final Pattern MINECRAFT_IMPORT = Pattern.compile("^import\\s+net\\.minecraft\\..*");
    private static final Pattern NEOFORGE_IMPORT = Pattern.compile("^import\\s+net\\.neoforged\\..*");
    private static final Pattern LITHMOD_MC_IMPORT = Pattern.compile("^import\\s+com\\.euvcraft\\.lithmod\\.mc\\..*");
    private static final Pattern MOJANG_IMPORT = Pattern.compile("^import\\s+com\\.mojang\\..*");
    private static final Pattern NETTY_IMPORT = Pattern.compile("^import\\s+io\\.netty\\..*");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("^package\\s+com\\.euvcraft\\.lithmod\\.core(\\..*)?\\s*;");

    public static void main(String[] args) throws IOException {
        Path coreSrcDir = Paths.get("src/main/java/com/euvcraft/lithmod/core");
        
        if (!Files.exists(coreSrcDir)) {
            System.err.println("Core source directory not found: " + coreSrcDir);
            System.exit(1);
        }

        List<String> violations = new ArrayList<>();
        
        Files.walk(coreSrcDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> checkFile(path, violations));

        if (!violations.isEmpty()) {
            System.err.println("==========================================");
            System.err.println("CORE IMPORT CHECK FAILED");
            System.err.println("The following files contain forbidden imports:");
            System.err.println("==========================================");
            violations.forEach(System.err::println);
            System.err.println("==========================================");
            System.exit(1);
        } else {
            System.out.println("Core import check passed: no Minecraft/NeoForge imports found in core package.");
            System.exit(0);
        }
    }

    private static void checkFile(Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean inCorePackage = false;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                
                // Check if we're in the core package
                if (JAVA_PACKAGE.matcher(line).matches()) {
                    inCorePackage = true;
                    continue;
                }
                
                // If we're in core package, check for forbidden imports
                if (inCorePackage) {
                    if (MINECRAFT_IMPORT.matcher(line).matches()) {
                        violations.add(file.toString() + ":" + (i + 1) + " - Forbidden Minecraft import: " + line);
                    } else if (NEOFORGE_IMPORT.matcher(line).matches()) {
                        violations.add(file.toString() + ":" + (i + 1) + " - Forbidden NeoForge import: " + line);
                    } else if (LITHMOD_MC_IMPORT.matcher(line).matches()) {
                        violations.add(file.toString() + ":" + (i + 1) + " - Forbidden lithmod.mc import: " + line);
                    } else if (MOJANG_IMPORT.matcher(line).matches()) {
                        violations.add(file.toString() + ":" + (i + 1) + " - Forbidden Mojang import: " + line);
                    } else if (NETTY_IMPORT.matcher(line).matches()) {
                        violations.add(file.toString() + ":" + (i + 1) + " - Forbidden Netty import: " + line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file " + file + ": " + e.getMessage());
        }
    }
}