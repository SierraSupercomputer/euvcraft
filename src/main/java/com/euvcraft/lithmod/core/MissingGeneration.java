package com.euvcraft.lithmod.core;

public record MissingGeneration(String lineId, int requiredGeneration, int installedGeneration) {
}
