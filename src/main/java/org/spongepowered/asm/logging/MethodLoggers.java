package org.spongepowered.asm.logging;

import org.spongepowered.asm.service.MixinService;

public class MethodLoggers {

    public static final ILogger loggerCompat = MixinService.getService().getLogger("mixin.compat");

    public static final ILogger loggerMeta = MixinService.getService().getLogger("mixin.meta");

    public static final ILogger loggerProcessor = MixinService.getService().getLogger("mixin.processor");

    public static final ILogger loggerAll = MixinService.getService().getLogger("mixin.all");

    public static final ILogger loggerInjector = MixinService.getService().getLogger("mixin.injector");
}
