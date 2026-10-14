package com.euvcraft.lithmod.core;

public record UnmetRequirement(String lineId, int requiredGeneration, int installedGeneration) {
}
