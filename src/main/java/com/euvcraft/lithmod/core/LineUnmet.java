package com.euvcraft.lithmod.core;

public record LineUnmet(String lineId, int requiredGeneration, int installedGeneration) {
}
