package com.malevolentgods.modularcraft.domain;

import java.util.UUID;

public record Cable(UUID source, Port output, UUID destination, Port input) {}
