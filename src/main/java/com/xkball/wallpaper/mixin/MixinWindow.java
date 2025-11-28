package com.xkball.wallpaper.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import com.xkball.wallpaper.MinePaperEngine;
import com.xkball.wallpaper.utils.TheSystemTray;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {
    
    @Inject(method = "<init>",at = @At("RETURN"))
    public void onInit(WindowEventHandler eventHandler, ScreenManager screenManager, DisplayData displayData, String preferredFullscreenVideoMode, String title, CallbackInfo ci){
        if(Util.getPlatform() == Util.OS.WINDOWS) MinePaperEngine.tray = new TheSystemTray();
    }
    
    @Inject(method = "setTitle",at = @At("RETURN"))
    public void onSetTitle(String title, CallbackInfo ci){
        MinePaperEngine.currentWindowTitle = title;
    }
    
    @Inject(method = "close",at = @At("RETURN"))
    public void onClose(CallbackInfo ci){
        if(MinePaperEngine.tray != null) MinePaperEngine.tray.closeTray();
    }
}
