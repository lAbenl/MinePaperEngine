package com.xkball.wallpaper;

import com.mojang.logging.LogUtils;
import com.xkball.wallpaper.utils.TheSystemTray;
import com.xkball.wallpaper.utils.VanillaUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(MinePaperEngine.MODID)
public class MinePaperEngine {

    public static final String MODID = "mine_paper_engine";

    private static final Logger LOGGER = LogUtils.getLogger();
    public static String currentWindowTitle = "";
    public static TheSystemTray tray; // 确保 TheSystemTray 实例是 public static 的

    public MinePaperEngine(IEventBus modEventBus, ModContainer modContainer) {
        // 注册客户端设置事件监听器
        modEventBus.addListener(this::onClientSetup);
    }

    /**
     * 客户端设置完成后触发的事件，用于执行客户端特有的初始化工作。
     * 我们在这里自动将游戏窗口切换到壁纸模式。
     *
     * @param event 客户端设置事件
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        // 使用 event.enqueueWork 确保在正确的时间点（通常是主线程）执行窗口操作
        event.enqueueWork(() -> {
            // 确保系统托盘和其隐藏消息窗口（用于失焦）被初始化
            if (tray == null) {
                tray = new TheSystemTray();
            }

            LOGGER.info("Client setup complete. Automatically switching to background mode.");

            // 自动切换到背景模式 (该调用会触发 VanillaUtils.ClientHandler.setWindowAsBG()，并在其中强制失焦)
            VanillaUtils.ClientHandler.switchWindowState();
        });
    }
}