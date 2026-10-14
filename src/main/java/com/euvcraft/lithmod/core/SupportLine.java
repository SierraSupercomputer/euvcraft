package com.euvcraft.lithmod.core;

import java.util.List;

/**
 * One support line's required generation for every tier, in tier index order.
 */
public record SupportLine(String id, List<Integer> generations) {
}
